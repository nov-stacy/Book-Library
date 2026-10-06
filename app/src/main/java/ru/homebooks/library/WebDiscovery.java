package ru.homebooks.library;

import org.jsoup.Jsoup;
import org.jsoup.nodes.*;
import java.io.*;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.*;

/** Bounded foreground fallback. Search snippets never become book metadata. */
final class WebDiscovery {
    private final BookLookup.Transport transport;
    WebDiscovery(BookLookup.Transport transport){this.transport=transport;}
    TitleBookSearch.Result find(String title,String author,String isbn){
        return find(title,author,isbn,System.nanoTime()+TimeUnit.SECONDS.toNanos(12),c->false,false);
    }
    TitleBookSearch.Result find(String title,String author,String isbn,long deadline,TitleBookSearch.CoverCheck usableCover,boolean coverOnly){
        BookLookup.LookupControl control=new BookLookup.LookupControl();control.deadline=deadline;
        ExecutorService worker=Executors.newSingleThreadExecutor();
        Future<TitleBookSearch.Result> job=worker.submit(()->{BookLookup.ACTIVE.set(control);try{
            TitleBookSearch.Result result=search(title,author,isbn);
            if(!coverOnly)return result;
            List<TitleBookSearch.Candidate> valid=new ArrayList<>();
            for(TitleBookSearch.Candidate c:result.candidates)if(!c.book.cover.isEmpty()&&usableCover.test(c))valid.add(c);
            return new TitleBookSearch.Result(valid,result.incomplete);
        }finally{BookLookup.ACTIVE.remove();}});
        try{return job.get(Math.max(1,deadline-System.nanoTime()),TimeUnit.NANOSECONDS);}
        catch(InterruptedException e){Thread.currentThread().interrupt();return new TitleBookSearch.Result(Collections.emptyList(),true);}
        catch(ExecutionException|TimeoutException e){return new TitleBookSearch.Result(Collections.emptyList(),true);}
        finally{control.cancel();job.cancel(true);worker.shutdownNow();}
    }
    private String text(String url)throws IOException{
        BookLookup.LookupControl control=BookLookup.ACTIVE.get();control.check();
        byte[] data=transport.get(url,3_000_000);control.check();return new String(data,StandardCharsets.UTF_8);
    }
    private TitleBookSearch.Result search(String title,String author,String isbn)throws IOException{
        String code=Isbn.normalize(isbn),query="\""+title.replace('"',' ').trim()+"\" "+author+" книга";
        List<String> queries=new ArrayList<>();if(code!=null)queries.add("\""+code+"\" книга");queries.add(query);
        List<TitleBookSearch.Candidate> found=new ArrayList<>();Set<String> visited=new HashSet<>();boolean incomplete=false;int tried=0;
        for(String q:queries){
            for(int engine=0;engine<2;engine++){
                List<String> links;
                try{
                    String encoded=URLEncoder.encode(q,"UTF-8");
                    links=engine==0?bingLinks(text("https://www.bing.com/search?format=rss&q="+encoded),title,code):duckLinks(text("https://html.duckduckgo.com/html/?q="+encoded),title,code);
                }catch(IOException e){incomplete=true;continue;}
                for(String address:links){
                    if(!visited.add(address))continue;if(tried++>=3)return new TitleBookSearch.Result(found,incomplete);
                    try{
                        URL url=BookLookup.publicUrl(address);String host=url.getHost().toLowerCase(Locale.ROOT);
                        if(host.equals("livelib.ru")||host.endsWith(".livelib.ru"))continue;
                        String html=text(url.toString());TitleBookSearch.Candidate candidate=null;
                        if(code!=null){BookLookup.Metadata exact=WebBookParser.parse(html,code,host);if(exact!=null)candidate=new TitleBookSearch.Candidate(exact.title,exact.author,exact.cover,host,code);}
                        if(candidate==null)candidate=WebBookParser.byTitle(html,title,author,host);
                        if(candidate!=null&&TitleBookSearch.relevant(candidate,title,author)){
                            found.add(candidate);if(!candidate.book.cover.isEmpty())return new TitleBookSearch.Result(found,incomplete);
                        }
                    }catch(IOException e){incomplete=true;}
                }
            }
        }
        return new TitleBookSearch.Result(found,incomplete);
    }
    private static boolean relevant(String text,String title,String isbn){return isbn!=null&&text.replaceAll("[\\s–-]","").contains(isbn)||TitleBookSearch.relevant(new TitleBookSearch.Candidate(text,"","","",""),title,"");}
    static List<String> bingLinks(String html,String title,String isbn)throws IOException{
        Document doc=Jsoup.parse(html,"",org.jsoup.parser.Parser.xmlParser());if(doc.selectFirst("channel")==null)throw new IOException("Search unavailable");
        List<String> links=new ArrayList<>();for(Element item:doc.select("item")){Element link=item.selectFirst("link");if(link!=null&&relevant(item.text(),title,isbn))links.add(link.text());}
        if(links.isEmpty()&&!doc.select("item").isEmpty())throw new IOException("Irrelevant search response");return links;
    }
    static List<String> duckLinks(String html,String title,String isbn)throws IOException{
        Document doc=Jsoup.parse(html,"https://html.duckduckgo.com");
        if(doc.selectFirst(".anomaly-modal, form[action*=anomaly]")!=null)throw new IOException("Search requires verification");
        if(doc.selectFirst(".results, #links") ==null)throw new IOException("Search unavailable");
        List<String> links=new ArrayList<>();for(Element a:doc.select("a.result__a[href]"))if(relevant(a.text(),title,isbn)){
            String url=a.absUrl("href");int start=url.indexOf("uddg=");if(start>=0){String encoded=url.substring(start+5).split("&",2)[0];url=URLDecoder.decode(encoded,"UTF-8");}if(url.startsWith("https://"))links.add(url);
        }return links;
    }
}
