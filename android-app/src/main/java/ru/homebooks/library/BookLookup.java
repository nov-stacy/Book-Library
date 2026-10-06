package ru.homebooks.library;

import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import org.json.*;
import org.jsoup.Jsoup;
import org.jsoup.nodes.*;
import java.io.*;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.*;

public final class BookLookup {
    interface Transport { byte[] get(String url, int limit) throws IOException; }
    interface ImageDecoder { byte[] decode(byte[] bytes) throws IOException; }
    private final Transport transport;
    private final ImageDecoder decoder;
    private final String googleBooksApiKey;
    private final Map<String,byte[]> verifiedImages=new ConcurrentHashMap<>();
    private static Transport productionTransport=BookLookup::download;
    static void configureCache(File directory){productionTransport=new CatalogRequestCache(new GoogleRequestCache(new File(directory,"google-books"),BookLookup::download));}
    public BookLookup() { this(productionTransport, BookLookup::compactImage, BuildConfig.GOOGLE_BOOKS_API_KEY); }
    BookLookup(Transport transport, ImageDecoder decoder) { this(transport,decoder,""); }
    BookLookup(Transport transport, ImageDecoder decoder,String key) { this.transport=transport;this.decoder=decoder;this.googleBooksApiKey=key.trim(); }
    public static final class Result {
        public final String title, author, notice;
        public final byte[] cover;
        Result(String title, String author, byte[] cover, String notice) {
            this.title = title; this.author = author; this.cover = cover; this.notice = notice;
        }
    }
    static final class Metadata {
        final String title, author, cover, source;
        Metadata(String title, String author, String cover, String source) {
            this.title = title; this.author = author; this.cover = cover; this.source = source;
        }
    }
    static final class HttpFailure extends IOException {
        final int status;
        final boolean dailyQuota;
        HttpFailure(int status) { this(status,false); }
        HttpFailure(int status,boolean dailyQuota) { super("HTTP " + status); this.status = status;this.dailyQuota=dailyQuota; }
    }
    public Result find(String raw) { return find(raw, ""); }
    public Result find(String raw, String titleHint) {return find(raw,titleHint,false);}
    Result findMetadata(String raw){return find(raw,"",true);}
    private Result find(String raw,String titleHint,boolean metadataOnly) {
        String isbn = Isbn.normalize(raw);
        if (isbn == null) throw new IllegalArgumentException("Invalid ISBN");
        Search search = new Search(metadataOnly);
        List<String> failures = Collections.synchronizedList(new ArrayList<>());
        List<String> needsTitle = Collections.synchronizedList(new ArrayList<>());
        LookupControl control = new LookupControl();
        control.deadline=System.nanoTime()+TimeUnit.SECONDS.toNanos(12);
        ExecutorService pool = Executors.newFixedThreadPool(4);
        try {
            runSources(sourceOrder(isbn,!googleBooksApiKey.isEmpty()),isbn,titleHint,search,failures,needsTitle,pool,control);
        } finally { control.cancel();pool.shutdownNow(); }
        if (search.book == null) {
            String message = failures.isEmpty()
                ? "Этот ISBN не найден в доступных каталогах. Можно заполнить карточку вручную."
                : "Не удалось проверить все каталоги. " + String.join("; ", failures) + ". Повторите поиск или заполните карточку вручную.";
            return new Result("", "", null, message);
        }
        String notice = "Найдено · " + search.book.source + ". Проверьте название и автора.";
        if (search.cover != null && !search.coverSource.equals(search.book.source)) notice += " Обложка · " + search.coverSource + ".";
        if (!metadataOnly&&search.cover == null) notice += " Обложка не найдена или недоступна, книгу можно сохранить.";
        return new Result(search.book.title, search.book.author, search.cover, notice);
    }
    static List<String> sourceOrder(String isbn,boolean google){
        return BookSources.isbnOrder(isbn,google);
    }
    private interface SourceLookup { void find(String isbn,String title,Search search)throws Exception; }
    private Map<String,SourceLookup> adapters(){
        Map<String,SourceLookup> result=new HashMap<>();
        result.put("eksmo",(isbn,title,search)->publisher(isbn,search));
        result.put("ast",(isbn,title,search)->ast(isbn,search));
        result.put("mif",this::mif);
        result.put("threeTotal",(isbn,title,search)->threeTotal(isbn,search));
        result.put("google",this::googleBooks);
        result.put("openLibrary",(isbn,title,search)->search.accept(openLibrary(isbn)));
        result.put("web",(isbn,title,search)->webSearch(isbn,search));
        return result;
    }

    private void runSources(List<String> sources,String isbn,String titleHint,Search search,List<String> failures,List<String> needsTitle,ExecutorService pool,LookupControl control){
        CompletionService<Void> completed=new ExecutorCompletionService<>(pool);List<Future<Void>> tasks=new ArrayList<>();
        Deque<String> waiting=new ArrayDeque<>(sources);int running=0;
        try{
            while((running>0||!waiting.isEmpty())&&!search.done()){
                control.check();
                if(search.book!=null&&!needsTitle.isEmpty()){
                    synchronized(needsTitle){for(String retry:needsTitle)waiting.addFirst(retry);needsTitle.clear();}
                }
                while(running<4&&!waiting.isEmpty()){
                    String source=waiting.removeFirst();running++;
                    tasks.add(completed.submit(()->{
                        LookupControl child=control.child(5);ACTIVE.set(child);
                        try{
                            child.check();String title=search.book==null?titleHint:search.book.title;
                            BookSources.Source definition=BookSources.named(source);
                            if(definition.data.optBoolean("retryWithTitle",false)&&(title==null||title.trim().isEmpty()))needsTitle.add(source);
                            SourceLookup adapter=adapters().get(definition.adapter);
                            if(adapter!=null)adapter.find(isbn,title,search);
                            else if(definition.adapter.equals("html"))new PublisherCatalog(this::fetch).find(definition,title,isbn,candidate->isbn.equals(candidate.isbn)&&search.accept(candidate.book));
                        }catch(Exception e){if(!control.stopped)failures.add(source+": "+explain(e));}
                        finally{ACTIVE.remove();}
                        return null;
                    }));
                }
                long remaining=control.deadline-System.nanoTime();
                Future<Void> next=remaining>0?completed.poll(remaining,TimeUnit.NANOSECONDS):null;
                if(next==null){failures.add("Время поиска истекло");break;}
                next.get();running--;
                if(running==0&&waiting.isEmpty()&&search.book!=null){synchronized(needsTitle){waiting.addAll(needsTitle);needsTitle.clear();}}
            }
        }catch(InterruptedIOException e){failures.add("Время поиска истекло");}
        catch(InterruptedException e){Thread.currentThread().interrupt();}
        catch(ExecutionException e){throw new IllegalStateException("Book lookup failed",e.getCause());}
        finally{control.cancel();for(Future<Void> task:tasks)task.cancel(true);}
    }
    static final ThreadLocal<LookupControl> ACTIVE=new ThreadLocal<>();
    static final class LookupControl{
        volatile boolean stopped;
        long deadline=Long.MAX_VALUE;
        LookupControl parent;
        LookupControl child(int seconds){LookupControl c=new LookupControl();c.parent=this;c.deadline=Math.min(deadline,System.nanoTime()+TimeUnit.SECONDS.toNanos(seconds));return c;}
        final Set<HttpURLConnection> connections=Collections.newSetFromMap(new ConcurrentHashMap<HttpURLConnection,Boolean>());
        void check()throws InterruptedIOException{if(parent!=null)parent.check();if(stopped||Thread.currentThread().isInterrupted()||System.nanoTime()>=deadline)throw new InterruptedIOException("Lookup cancelled");}
        void cancel(){stopped=true;for(HttpURLConnection connection:connections)connection.disconnect();}
    }
    private byte[] fetch(String url,int limit)throws IOException{
        LookupControl control=ACTIVE.get();if(control!=null)control.check();
        byte[] bytes=transport.get(url,limit);if(control!=null)control.check();return bytes;
    }
    public byte[] findCover(String raw) { return find(raw).cover; }
    public byte[] findCover(String raw,String title) { return find(raw,title).cover; }

    // Keep the first matching edition's metadata, but continue until an image actually decodes.
    private final class Search {
        final boolean metadataOnly;
        Search(boolean metadataOnly){this.metadataOnly=metadataOnly;}
        boolean done(){return cover!=null||(metadataOnly&&book!=null);}
        volatile Metadata book;
        volatile byte[] cover;
        volatile String coverSource="";
        final Set<String> attemptedImages = new HashSet<>();
        boolean accept(Metadata candidate) {
            if (candidate == null) return false;
            synchronized(this){
                if(cover!=null)return true;
                if(book==null)book=candidate;
                else if(book.author.isEmpty()&&!candidate.author.isEmpty())book=new Metadata(book.title,candidate.author,book.cover,book.source);
                if(metadataOnly)return true;
                if(candidate.cover.isEmpty()||!attemptedImages.add(candidate.cover))return false;
            }
            try {
                byte[] image=decoder.decode(fetch(candidate.cover,5_000_000));
                LookupControl control=ACTIVE.get();if(control!=null)control.check();
                synchronized(this){if(cover==null&&image!=null&&image.length>0){coverSource=candidate.source;cover=image;}}
            }catch(IOException ignored){ /* Try the next page or catalog. */ }
            return cover != null;
        }
    }
    private void ast(String isbn,Search search)throws IOException{
        String query=isbn.startsWith("978517")?"978-5-17-"+isbn.substring(6,12)+"-"+isbn.substring(12):isbn;
        String html=text("https://ast.ru/search/?q="+query);
        if(search.accept(PublisherPages.ast(html,isbn)))return;
        Document doc=Jsoup.parse(html,"https://ast.ru");Set<String> links=new LinkedHashSet<>();
        for(Element link:doc.select(".search-results-container a[href]")){
            String url=link.absUrl("href").split("[?#]",2)[0];
            if(url.matches("https://ast\\.ru/book/[^/]+/"))links.add(url);
            if(links.size()==3)break;
        }
        for(String url:links)try{if(search.accept(PublisherPages.ast(text(url),isbn)))return;}catch(IOException ignored){}
    }
    private void mif(String isbn,String title,Search search)throws IOException,JSONException{
        LinkedHashSet<String> queries=new LinkedHashSet<>();queries.add(isbn);
        if(title!=null&&!title.trim().isEmpty()){
            queries.add(title.trim());
            // Imported titles often append the subtitle, whereas the publisher indexes only the main title.
            String shorter=title.split("[.:]",2)[0].trim();if(!shorter.equals(title.trim())&&shorter.length()>=4)queries.add(shorter);
        }
        Set<String> visited=new HashSet<>();
        for(String query:queries){
            JSONObject result=new JSONObject(text("https://www.mann-ivanov-ferber.ru/product/search?query="+URLEncoder.encode(query,"UTF-8")));
            JSONArray products=result.optJSONArray("products");if(products==null)continue;
            for(int i=0;i<Math.min(products.length(),3);i++){
                JSONObject product=products.optJSONObject(i);if(product==null)continue;
                String url=product.optString("url","").replace("http://","https://");
                if(!url.matches("https://www\\.mann-ivanov-ferber\\.ru/catalog/product/[^/]+/")||!visited.add(url))continue;
                try{if(search.accept(PublisherPages.mif(text(url),isbn)))return;}catch(IOException ignored){}
            }
        }
    }
    private void threeTotal(String isbn,Search search)throws IOException{
        Document results=Jsoup.parse(text("https://store.3dtotal.com/search?q="+isbn+"&type=product"),"https://store.3dtotal.com");
        Set<String> links=new LinkedHashSet<>();
        for(Element link:results.select("main .product-grid a[href]")){
            String url=link.absUrl("href").split("[?#]",2)[0];
            if(url.matches("https://store\\.3dtotal\\.com/products/[^/]+"))links.add(url);
            if(links.size()==3)break;
        }
        for(String url:links)try{
            Metadata book=PublisherPages.threeTotal(text(url+".js"),isbn);
            if(book!=null&&book.author.isEmpty())try{book=new Metadata(book.title,PublisherPages.threeTotalAuthorPage(text(url),book.title),book.cover,book.source);}catch(IOException ignored){}
            if(search.accept(book))return;
        }catch(IOException ignored){}
    }
    private void googleBooks(String isbn,String title,Search search)throws IOException,JSONException{
        if(googleBooksApiKey.isEmpty()||title==null||title.trim().isEmpty())return;
        java.util.List<String> queries=java.util.Collections.singletonList("\""+title.replace('"',' ').trim()+"\"");
        for(String query:queries){
            JSONObject response=new JSONObject(text("https://www.googleapis.com/books/v1/volumes?q="+URLEncoder.encode(query,"UTF-8")+"&maxResults=10&key="+URLEncoder.encode(googleBooksApiKey,"UTF-8")));
            JSONArray items=response.optJSONArray("items");if(items==null)continue;
            for(int i=0;i<items.length();i++){
                JSONObject item=items.optJSONObject(i);JSONObject info=item==null?null:item.optJSONObject("volumeInfo");if(info==null)continue;
                JSONArray ids=info.optJSONArray("industryIdentifiers");boolean matches=false;
                if(ids!=null)for(int j=0;j<ids.length();j++){
                    JSONObject id=ids.optJSONObject(j);if(id==null)continue;String type=id.optString("type");
                    if((type.equals("ISBN_10")||type.equals("ISBN_13"))&&isbn.equals(Isbn.normalize(id.optString("identifier"))))matches=true;
                }
                String name=info.optString("title","").trim();if(!matches||name.isEmpty())continue;
                List<String> names=new ArrayList<>();JSONArray authors=info.optJSONArray("authors");
                if(authors!=null)for(int j=0;j<authors.length();j++){String author=authors.optString(j,"").trim();if(!author.isEmpty())names.add(author);}
                String author=AuthorNames.join(names);search.accept(new Metadata(name,author,"","Google Books"));
                JSONObject images=info.optJSONObject("imageLinks");if(images==null)continue;
                for(String size:new String[]{"medium","small","thumbnail","smallThumbnail"}){
                    String image=images.optString(size,"").replace("http://","https://");
                    if(image.startsWith("https://")&&search.accept(new Metadata(name,author,image,"Google Books")))return;
                }
            }
        }
    }
    TitleBookSearch.Result findByTitle(String title,String author,String isbn){
        return new TitleBookSearch(this::fetch,googleBooksApiKey).find(title,author,isbn,this::usableCover,false);
    }
    TitleBookSearch.Result findCoverCandidates(String title,String author,String isbn){
        return new TitleBookSearch(this::fetch,googleBooksApiKey).find(title,author,isbn,this::usableCover,true);
    }
    private boolean usableCover(TitleBookSearch.Candidate candidate){
        try{if(verifiedImages.containsKey(candidate.book.cover))return true;byte[] image=decoder.decode(fetch(candidate.book.cover,5_000_000));if(image==null||image.length==0)return false;verifiedImages.put(candidate.book.cover,image);return true;}catch(IOException e){return false;}
    }
    Result previewTitle(TitleBookSearch.Candidate candidate){return complete(candidate.book);}
    private Result complete(Metadata book) {
        byte[] cover = null;
        String notice = "Найдено · " + book.source + ". Проверьте название и автора.";
        if (!book.cover.isEmpty()) try { cover = decoder.decode(fetch(book.cover, 5_000_000)); }
        catch (IOException e) { notice += " Обложка пока недоступна, книгу можно сохранить."; }
        return new Result(book.title, book.author, cover, notice);
    }
    public Result fromPage(String address, String isbn) {
        try {
            URL url = publicUrl(address);
            Metadata book = WebBookParser.parse(text(url.toString()),isbn,url.getHost());
            if(book == null) return new Result("","",null,"На странице нет данных с подтверждённым ISBN. Попробуйте другую ссылку или заполните карточку вручную.");
            Result result = complete(book);
            if (result.cover != null) return result;
            byte[] cover = findCover(isbn);
            return new Result(book.title, book.author, cover, "Найдено · " + book.source + ". Проверьте название и автора." + (cover == null ? " Обложка не найдена или недоступна, книгу можно сохранить." : ""));
        } catch(Exception e) { return new Result("","",null,"Не удалось прочитать страницу: " + explain(e) + "."); }
    }
    private void webSearch(String isbn, Search search) throws IOException {
        Document results = Jsoup.parse(text("https://www.bing.com/search?format=rss&q=%22" + isbn + "%22"), "", org.jsoup.parser.Parser.xmlParser());
        if(results.selectFirst("channel") == null) throw new IOException("Search unavailable");
        int tried=0;
        for(Element item:results.select("item")) {
            // Ignore unrelated search hits, even if the engine silently rewrites the query.
            if(!item.text().replaceAll("[\\s\\-–]", "").contains(isbn)) continue;
            Element link=item.selectFirst("link");if(link==null)continue;
            try {
                URL url=publicUrl(link.text());
                if(url.getHost().equalsIgnoreCase("livelib.ru") || url.getHost().toLowerCase(Locale.ROOT).endsWith(".livelib.ru"))continue;
                Metadata book=WebBookParser.parse(text(url.toString()),isbn,url.getHost());
                if(search.accept(book))return;
            } catch(IOException ignored) { }
            if(++tried>=3)break;
        }
    }
    static URL publicUrl(String address) throws IOException {
        URL url=new URL(address);
        if(!"https".equals(url.getProtocol()) || url.getUserInfo()!=null || (url.getPort()!=-1 && url.getPort()!=443))throw new IOException("HTTPS required");
        for(InetAddress ip:InetAddress.getAllByName(url.getHost()))
            if(ip.isAnyLocalAddress() || ip.isLoopbackAddress() || ip.isSiteLocalAddress() || ip.isLinkLocalAddress())throw new IOException("Public page required");
        return url;
    }
    private Metadata openLibrary(String isbn) throws IOException, JSONException {
        JSONObject book;
        try { book = new JSONObject(text("https://openlibrary.org/isbn/" + isbn + ".json")); }
        catch (HttpFailure e) { if (e.status == 404) return null; throw e; }
        String title = book.optString("title", "").trim();
        if (title.isEmpty()) throw new IOException("Invalid catalog response");
        String author = "";
        {
            List<String> names = new ArrayList<>(); JSONArray authors = book.optJSONArray("authors");
            if (authors != null) for (int i = 0; i < authors.length(); i++) {
                JSONObject person=authors.optJSONObject(i);if(person==null)continue;
                String direct=AuthorNames.json(person);if(!direct.isEmpty()){names.add(direct);continue;}
                String key = person.optString("key", "");
                if (!key.matches("/authors/OL[0-9]+A")) continue;
                try {
                    String name = new JSONObject(text("https://openlibrary.org" + key + ".json")).optString("name", "");
                    if (!name.isEmpty()) names.add(name);
                } catch (IOException | JSONException ignored) { /* Keep the title if author lookup fails. */ }
            }
            author = AuthorNames.join(names);
        }
        if(author.isEmpty())author=AuthorNames.clean(book.optString("by_statement",""));
        JSONArray covers = book.optJSONArray("covers");
        long coverId = covers == null ? -1 : covers.optLong(0, -1);
        return new Metadata(title, author, coverId > 0 ? "https://covers.openlibrary.org/b/id/" + coverId + "-M.jpg?default=false" : "", "Open Library");
    }
    private void publisher(String isbn, Search search) throws IOException {
        Document results = Jsoup.parse(text("https://eksmo.ru/search/?q=" + isbn), "https://eksmo.ru");
        Set<String> links = new LinkedHashSet<>();
        for (Element link : results.select("a.book__content[href], a.book__image-align[href]")) {
            String url = link.absUrl("href");
            if (url.matches("https://eksmo\\.ru/(?:book|ebook)/[^/?#]+/")) links.add(url);
            if (links.size() == 3) break;
        }
        for (String url : links) try {
            Metadata book = parsePublisher(text(url), isbn);
            if (search.accept(book)) return;
        } catch (IOException ignored) { /* Continue with the next edition. */ }
        if (links.isEmpty() && !results.title().contains("Поиск")) throw new IOException("Invalid catalog response");
    }
    static Metadata parsePublisher(String html, String isbn) {
        Document doc = Jsoup.parse(html);
        // Validate the ISBN in the book's own properties, never in recommendations or a search query.
        Element code = doc.selectFirst(".book-page__copy-isbn .copy__val");
        Element title = doc.selectFirst("h1.book-page__card-title");
        if (code == null || title == null || !isbn.equals(Isbn.normalize(code.text())) || title.text().trim().isEmpty()) return null;
        String author = AuthorNames.elements(doc,".book-page__card-author-link");
        Element image = doc.selectFirst("meta[property=og:image]");
        String cover = image == null ? "" : image.attr("content").replace("http://", "https://");
        if (!cover.matches("https://(cdn|images)\\.eksmo\\.ru/.*")) cover = "";
        return new Metadata(title.text(), author, cover, "Эксмо / Бомбора");
    }
    private String text(String url) throws IOException { return new String(fetch(url, 3_000_000), StandardCharsets.UTF_8); }
    static String explain(Exception e) {
        if (e instanceof UnknownHostException) return "нет соединения с сервером";
        if (e instanceof SocketTimeoutException) return "сервер не ответил вовремя";
        if (e instanceof HttpFailure) {
            int status = ((HttpFailure) e).status;
            if (status == 429) return "слишком много запросов, попробуйте позже";
            if (status == 403) return "сервер ограничил доступ";
            return "сервер вернул ошибку " + status;
        }
        return "каталог временно недоступен";
    }
    private static byte[] compactImage(byte[] bytes) throws IOException {
        BitmapFactory.Options options = new BitmapFactory.Options(); options.inJustDecodeBounds = true;
        BitmapFactory.decodeByteArray(bytes, 0, bytes.length, options);
        if (options.outWidth <= 0 || options.outHeight <= 0) throw new IOException("Invalid image");
        options.inSampleSize = 1;
        while (Math.max(options.outWidth, options.outHeight) / options.inSampleSize > 640) options.inSampleSize *= 2;
        options.inJustDecodeBounds = false;
        Bitmap bitmap = BitmapFactory.decodeByteArray(bytes, 0, bytes.length, options);
        if (bitmap == null) throw new IOException("Invalid image");
        try (ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            if (!bitmap.compress(Bitmap.CompressFormat.JPEG, 85, out)) throw new IOException("Cannot encode image");
            return out.toByteArray();
        } finally { bitmap.recycle(); }
    }
    private static byte[] download(String address, int limit) throws IOException {
        LookupControl control=ACTIVE.get();if(control!=null)control.check();
        URL url = new URL(address);
        if (!"https".equals(url.getProtocol())) throw new IOException("HTTPS required");
        HttpURLConnection connection = (HttpURLConnection) url.openConnection();
        int timeout=control==null?2500:(int)Math.max(1,Math.min(2500,TimeUnit.NANOSECONDS.toMillis(control.deadline-System.nanoTime())));
        connection.setConnectTimeout(timeout); connection.setReadTimeout(timeout);
        connection.setRequestProperty("User-Agent", "HomeLibrary/0.2 (personal ISBN catalog)");
        connection.setRequestProperty("Accept", "text/html,application/json;q=0.9,image/*;q=0.8");
        try {
            if(control!=null){control.connections.add(connection);if(control.parent!=null)control.parent.connections.add(connection);control.check();}
            int status = connection.getResponseCode();
            if (status != 200) {
                boolean daily=false;
                if(status==429&&url.getHost().equals("www.googleapis.com")){
                    try(InputStream error=connection.getErrorStream();ByteArrayOutputStream out=new ByteArrayOutputStream()){
                        if(error!=null){byte[] buffer=new byte[1024];int n;while(out.size()<16000&&(n=error.read(buffer))!=-1)out.write(buffer,0,n);
                            JSONObject detail=new JSONObject(new String(out.toByteArray(),StandardCharsets.UTF_8)).optJSONObject("error");
                            daily=detail!=null&&detail.optString("message").contains("Queries per day");}
                    }catch(IOException|JSONException ignored){}
                }
                throw new HttpFailure(status,daily);
            }
            try (InputStream in = connection.getInputStream(); ByteArrayOutputStream out = new ByteArrayOutputStream()) {
                byte[] buffer = new byte[8192]; int n;
                while ((n = in.read(buffer)) != -1) {
                    if(control!=null)control.check();
                    if (out.size() + n > limit) throw new IOException("Response too large");
                    out.write(buffer, 0, n);
                }
                return out.toByteArray();
            }
        } finally { if(control!=null){control.connections.remove(connection);if(control.parent!=null)control.parent.connections.remove(connection);}connection.disconnect(); }
    }
}
