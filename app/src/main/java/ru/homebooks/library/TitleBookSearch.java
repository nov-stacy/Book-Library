package ru.homebooks.library;

import org.json.*;
import org.jsoup.Jsoup;
import org.jsoup.nodes.*;
import java.io.*;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.*;
import java.util.regex.*;

/** Explicit, user-confirmed suggestions. Never used by automatic ISBN or cover lookup. */
final class TitleBookSearch {
    static final class Candidate {
        final BookLookup.Metadata book;
        final String isbn;
        Candidate(String title,String author,String image,String source,String isbn){
            book=new BookLookup.Metadata(title,author,image,source);
            String normalized=Isbn.normalize(isbn);this.isbn=normalized==null?"":normalized;
        }
        String editionNote(String wanted){
            if(isbn.isEmpty())return "ISBN у источника не указан";
            return isbn.equals(Isbn.normalize(wanted))?"ISBN совпадает":"ISBN источника: "+isbn+" · другое издание";
        }
    }
    static final class Result {
        final List<Candidate> candidates;
        final boolean incomplete;
        Result(List<Candidate> candidates,boolean incomplete){this.candidates=candidates;this.incomplete=incomplete;}
    }
    interface CoverCheck { boolean test(Candidate candidate); }
    private final BookLookup.Transport transport;
    private final String key;
    TitleBookSearch(BookLookup.Transport transport,String key){this.transport=transport;this.key=key;}
    Result find(String title,String author,String isbn){return find(title,author,isbn,c->false,false);}
    Result find(String title,String author,String isbn,CoverCheck usableCover,boolean coverOnly){
        if(title.trim().isEmpty())return new Result(Collections.emptyList(),false);
        long deadline=Math.min(BookLookup.ACTIVE.get()==null?Long.MAX_VALUE:BookLookup.ACTIVE.get().deadline,System.nanoTime()+TimeUnit.SECONDS.toNanos(12));
        BookLookup.LookupControl control=new BookLookup.LookupControl();control.deadline=deadline;
        ExecutorService pool=Executors.newFixedThreadPool(4);
        CompletionService<List<Candidate>> completion=new ExecutorCompletionService<>(pool);
        List<Callable<List<Candidate>>> sources=new ArrayList<>();
        Map<String,Callable<List<Candidate>>> adapters=new HashMap<>();
        adapters.put("sindbad",()->sindbad(title));adapters.put("mif",()->mif(title));
        adapters.put("threeTotal",()->threeTotal(title));adapters.put("google",()->google(title,author));
        adapters.put("openLibrary",()->openLibrary(title,author));
        Set<Candidate> verified=Collections.newSetFromMap(new ConcurrentHashMap<Candidate,Boolean>());
        PublisherCatalog catalogs=new PublisherCatalog(transport);
        for(BookSources.Source source:BookSources.titleOrder(title,Isbn.normalize(isbn),!key.isEmpty())){
            Callable<List<Candidate>> adapter=adapters.get(source.adapter);
            if(adapter!=null)sources.add(adapter);
            else if(!source.get("titleSearch").isEmpty())sources.add(()->catalogs.find(source,title,isbn,c->{
                if(relevant(c,title,author)&&strong(c,title,author,isbn)&&!c.book.cover.isEmpty()&&usableCover.test(c)){verified.add(c);return true;}
                return false;
            }));
        }
        List<Future<List<Candidate>>> jobs=new ArrayList<>();
        for(Callable<List<Candidate>> source:sources)jobs.add(completion.submit(()->{
            BookLookup.LookupControl child=control.child(5);BookLookup.ACTIVE.set(child);
            try{
                child.check();List<Candidate> results=new ArrayList<>();
                for(Candidate c:source.call())if(relevant(c,title,author)){
                    boolean check=coverOnly||strong(c,title,author,isbn);
                    boolean valid=verified.contains(c)||(check&&!c.book.cover.isEmpty()&&usableCover.test(c));
                    if(valid)verified.add(c);
                    if(!coverOnly||valid)results.add(c);
                    if(valid&&strong(c,title,author,isbn))break;
                }
                return results;
            }finally{BookLookup.ACTIVE.remove();}
        }));
        List<Candidate> found=new ArrayList<>();boolean incomplete=false,ready=false;
        long catalogDeadline=deadline-TimeUnit.SECONDS.toNanos(3);
        try{
            for(int i=0;i<jobs.size();i++){
                long remaining=catalogDeadline-System.nanoTime();
                Future<List<Candidate>> next=remaining>0?completion.poll(remaining,TimeUnit.NANOSECONDS):null;
                if(next==null){incomplete=true;break;}
                try{found.addAll(next.get());}catch(ExecutionException e){incomplete=true;}
                for(Candidate c:found)if(verified.contains(c)&&strong(c,title,author,isbn)){ready=true;break;}
                Set<String> exact=new HashSet<>();
                if(Isbn.normalize(isbn)==null&&!author.trim().isEmpty())for(Candidate c:found)if(strong(c,title,author,isbn))exact.add(words(c.book.title)+"|"+words(c.book.author)+"|"+c.isbn);
                if(ready||(!coverOnly&&exact.size()>=3))break;
            }
        }catch(InterruptedException e){Thread.currentThread().interrupt();incomplete=true;}
        finally{control.cancel();for(Future<?> job:jobs)job.cancel(true);pool.shutdownNow();}
        boolean hasCover=false;for(Candidate c:found)if(!c.book.cover.isEmpty())hasCover=true;
        if(!ready&&!hasCover&&!Thread.currentThread().isInterrupted()&&System.nanoTime()<deadline){
            Result web=new WebDiscovery(transport).find(title,author,isbn,deadline,usableCover,coverOnly);
            found.addAll(web.candidates);incomplete|=web.incomplete;
        }
        String wanted=Isbn.normalize(isbn);
        Collections.sort(found,(a,b)->{
            int edition=Integer.compare(wanted!=null&&wanted.equals(a.isbn)?0:1,wanted!=null&&wanted.equals(b.isbn)?0:1);
            return edition!=0?edition:Integer.compare(titleRank(a.book.title,title),titleRank(b.book.title,title));
        });
        return new Result(found,incomplete);
    }
    static boolean strong(Candidate c,String title,String author,String isbn){
        String wanted=Isbn.normalize(isbn);
        if(wanted!=null)return wanted.equals(c.isbn);
        return words(c.book.title).equals(words(title))&&(author.trim().isEmpty()||words(c.book.author).equals(words(author)));
    }
    private static String words(String value){
        return value.toLowerCase(Locale.ROOT).replace('ё','е').replaceAll("[^\\p{L}\\p{N}]+"," ").trim().replaceAll(" +"," ");
    }
    private static boolean containsWords(String actual,String query){
        String normalized=words(query);if(normalized.isEmpty())return false;
        Set<String> tokens=new HashSet<>(Arrays.asList(words(actual).split(" ")));
        return tokens.containsAll(Arrays.asList(normalized.split(" ")));
    }
    static boolean relevant(Candidate candidate,String title,String author){
        if(!containsWords(candidate.book.title,title))return false;
        return author.trim().isEmpty()||candidate.book.author.trim().isEmpty()||containsWords(candidate.book.author,author);
    }
    private static int titleRank(String actual,String query){
        String a=words(actual),q=words(query);return a.equals(q)?0:a.startsWith(q+" ")?1:2;
    }
    private String text(String url)throws IOException{
        BookLookup.LookupControl control=BookLookup.ACTIVE.get();if(control!=null)control.check();
        byte[] bytes=transport.get(url,3_000_000);if(control!=null)control.check();
        return new String(bytes,StandardCharsets.UTF_8);
    }
    private static String q(String value)throws UnsupportedEncodingException{return URLEncoder.encode(value.trim(),"UTF-8");}
    private List<Candidate> google(String title,String author)throws Exception{
        String cleanTitle=title.replace('"',' ').trim(),cleanAuthor=author.replace('"',' ').trim();
        String suffix=cleanAuthor.isEmpty()?"":" inauthor:\""+cleanAuthor+"\"";
        List<Candidate> results=new ArrayList<>();
        for(String query:new String[]{"\""+cleanTitle+"\""+suffix}){
        JSONArray items=new JSONObject(text("https://www.googleapis.com/books/v1/volumes?q="+q(query)+"&maxResults=10&key="+q(key))).optJSONArray("items");
        if(items==null)continue;
        for(int i=0;i<items.length();i++){
            JSONObject info=items.getJSONObject(i).optJSONObject("volumeInfo");if(info==null||info.optString("title").trim().isEmpty())continue;
            String isbn="";JSONArray ids=info.optJSONArray("industryIdentifiers");
            if(ids!=null)for(int j=0;j<ids.length();j++){JSONObject id=ids.getJSONObject(j);String type=id.optString("type");if(type.equals("ISBN_13")||type.equals("ISBN_10")){String normalized=Isbn.normalize(id.optString("identifier"));if(normalized!=null)isbn=normalized;}}
            JSONArray authors=info.optJSONArray("authors");List<String> names=new ArrayList<>();if(authors!=null)for(int j=0;j<authors.length();j++)names.add(authors.optString(j));
            JSONObject image=info.optJSONObject("imageLinks");
            Candidate candidate=new Candidate(info.optString("title"),AuthorNames.join(names),image==null?"":image.optString("thumbnail","").replace("http://","https://"),"Google Books",isbn);
            if(relevant(candidate,title,author))results.add(candidate);
        }
        if(!results.isEmpty())return results;
        }
        return results;
    }
    private List<Candidate> openLibrary(String title,String author)throws Exception{
        JSONArray docs=new JSONObject(text("https://openlibrary.org/search.json?title="+q(title)+"&author="+q(author)+"&limit=3&fields=title,author_name,cover_i")).optJSONArray("docs");
        List<Candidate> results=new ArrayList<>();if(docs==null)return results;
        for(int i=0;i<docs.length();i++){
            JSONObject doc=docs.getJSONObject(i);if(doc.optString("title").trim().isEmpty())continue;
            JSONArray authors=doc.optJSONArray("author_name");List<String> names=new ArrayList<>();if(authors!=null)for(int j=0;j<authors.length();j++)names.add(authors.optString(j));
            long cover=doc.optLong("cover_i",-1);
            // Search results describe works with many editions: do not claim an edition ISBN match.
            results.add(new Candidate(doc.optString("title"),AuthorNames.join(names),cover>0?"https://covers.openlibrary.org/b/id/"+cover+"-M.jpg?default=false":"","Open Library",""));
        }return results;
    }
    private List<Candidate> sindbad(String title)throws Exception{
        String base="https://sindbadbooks.ru/";
        Document search=Jsoup.parse(text(base+"index.php?route=product/search&filter_name="+q(title)),base);
        List<Candidate> results=new ArrayList<>();Set<String> links=new LinkedHashSet<>();
        for(Element link:search.select(".product-list .name a[href], .product-grid .name a[href]")){
            if(!containsWords(link.text(),title))continue;
            String url=link.absUrl("href");
            if(url.startsWith(base+"index.php?")&&url.contains("route=product/product")&&url.matches(".*[?&]product_id=[0-9]+(?:&.*)?"))links.add(url);
            if(links.size()==3)break;
        }
        for(String url:links){Candidate candidate=parseSindbad(text(url));if(candidate!=null)results.add(candidate);}
        return results;
    }
    static Candidate parseSindbad(String html){
        Document doc=Jsoup.parse(html,"https://sindbadbooks.ru/");Element title=doc.selectFirst("#content h1"),info=doc.selectFirst(".product-info");
        if(title==null||info==null)return null;
        String author="",isbn="";
        for(Element label:info.select(".description span"))if(label.text().trim().equals("Автор:")){
            StringBuilder name=new StringBuilder();for(Node node=label.nextSibling();node!=null;node=node.nextSibling()){
                if(node instanceof Element&&(((Element)node).tagName().equals("br")||((Element)node).tagName().equals("span")))break;
                if(node instanceof TextNode)name.append(((TextNode)node).text());else if(node instanceof Element)name.append(((Element)node).text());
            }author=name.toString().trim();break;
        }
        for(Element row:doc.select("#tab-attribute tr")){org.jsoup.select.Elements cells=row.select("td");if(cells.size()==2&&cells.get(0).text().contains("ISBN"))isbn=cells.get(1).text();}
        Element cover=info.selectFirst(".image a[href]");String image=cover==null?"":cover.absUrl("href");
        if(!image.startsWith("https://sindbadbooks.ru/image/"))image="";
        return new Candidate(title.text().trim(),author,image,"Синдбад",isbn);
    }
    private List<Candidate> mif(String title)throws Exception{
        JSONArray products=new JSONObject(text("https://www.mann-ivanov-ferber.ru/product/search?query="+q(title))).optJSONArray("products");
        List<Candidate> results=new ArrayList<>();if(products==null)return results;
        Set<String> visited=new HashSet<>();
        for(int i=0;i<Math.min(products.length(),3);i++){
            String url=products.getJSONObject(i).optString("url").replace("http://","https://");
            if(!url.matches("https://www\\.mann-ivanov-ferber\\.ru/catalog/product/[^/]+/")||!visited.add(url))continue;
            Element data=Jsoup.parse(text(url)).selectFirst("script#__NEXT_DATA__");if(data==null)continue;
            JSONObject p=new JSONObject(data.data()).getJSONObject("props").getJSONObject("pageProps").getJSONObject("storeSnapshot").getJSONObject("productCardStore").getJSONObject("product");
            JSONObject base=p.getJSONObject("baseData");String name=base.optString("title").trim();if(name.isEmpty())continue;
            List<String> authors=new ArrayList<>();JSONArray names=base.optJSONArray("authors");if(names!=null)for(int j=0;j<names.length();j++)authors.add(names.getJSONObject(j).optString("name"));
            String isbn="";for(Element line:Jsoup.parse(p.optString("releaseParameters")).select("p")){
                Matcher match=Pattern.compile("(?i)^ISBN\\s*:?\\s*([0-9Xx\\s–-]+)$").matcher(line.text().trim());if(match.matches())isbn=match.group(1);
            }
            JSONObject cover=base.optJSONObject("cover");String image=cover==null?"":cover.optString("large",cover.optString("small",""));if(image.startsWith("/assets/"))image="https://www.mann-ivanov-ferber.ru"+image;
            if(!image.startsWith("https://www.mann-ivanov-ferber.ru/assets/"))image="";
            results.add(new Candidate(name,AuthorNames.join(authors),image,"МИФ",isbn));
        }return results;
    }
    private List<Candidate> threeTotal(String title)throws Exception{
        Document doc=Jsoup.parse(text("https://store.3dtotal.com/search?q="+q(title)+"&type=product"),"https://store.3dtotal.com");
        Set<String> links=new LinkedHashSet<>();for(Element a:doc.select("main .product-grid a[href]")){
            String url=a.absUrl("href").split("[?#]",2)[0];if(url.matches("https://store\\.3dtotal\\.com/products/[^/]+"))links.add(url);if(links.size()==3)break;
        }
        List<Candidate> results=new ArrayList<>();for(String url:links){
            JSONObject p=new JSONObject(text(url+".js"));String name=p.optString("title").trim();if(name.isEmpty())continue;
            String image=p.optString("featured_image","");if(image.startsWith("//"))image="https:"+image;
            if(!image.startsWith("https://cdn.shopify.com/")&&!image.startsWith("https://store.3dtotal.com/cdn/"))image="";
            if(!image.isEmpty())image+=(image.contains("?")?"&":"?")+"width=640";
            // A product may contain several variants. Only a single variant identifies this cover unambiguously.
            JSONArray variants=p.optJSONArray("variants");String isbn=variants!=null&&variants.length()==1?variants.getJSONObject(0).optString("barcode"):"";
            String author=PublisherPages.threeTotalAuthor(p);
            if(author.isEmpty())try{author=PublisherPages.threeTotalAuthorPage(text(url),name);}catch(IOException ignored){}
            results.add(new Candidate(name,author,image,"3dtotal Publishing",isbn));
        }return results;
    }
}
