package ru.homebooks.library;

import org.jsoup.Jsoup;
import org.jsoup.nodes.*;
import java.io.*;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.regex.*;

/** Data-driven HTML catalogs: only own product URLs and edition identifiers are accepted. */
final class PublisherCatalog {
    private final BookLookup.Transport transport;
    PublisherCatalog(BookLookup.Transport transport){this.transport=transport;}
    interface Stop { boolean accept(TitleBookSearch.Candidate candidate); }
    List<TitleBookSearch.Candidate> find(BookSources.Source source,String title,String isbn)throws IOException{
        return find(source,title,isbn,candidate->false);
    }
    List<TitleBookSearch.Candidate> find(BookSources.Source source,String title,String isbn,Stop stop)throws IOException{
        boolean byIsbn=title==null||title.trim().isEmpty();String template=source.get(byIsbn?"isbnSearch":"titleSearch");
        if(template.isEmpty())return Collections.emptyList();
        String query=byIsbn?source.formatIsbn(isbn):title;
        String url=source.get("base")+template.replace("{query}",URLEncoder.encode(query,"UTF-8"));
        Document doc=Jsoup.parse(text(url),url);List<TitleBookSearch.Candidate> results=new ArrayList<>();
        // Some searches redirect directly to the book, while the transport returns only its body.
        Element canonical=doc.selectFirst("link[rel=canonical]");
        if(canonical!=null&&allowed(source,canonical.absUrl("href"))){TitleBookSearch.Candidate c=parse(source,doc);if(matches(c,title,isbn,byIsbn)){results.add(c);stop.accept(c);return results;}}
        Set<String> visited=new LinkedHashSet<>();
        for(Element link:doc.select(source.get("links"))){
            String target=link.absUrl("href").split("[?#]",2)[0];if(!allowed(source,target))continue;
            if(!byIsbn&&!link.text().trim().isEmpty()&&!link.text().contains("...")&&!link.text().contains("…")&&!TitleBookSearch.relevant(new TitleBookSearch.Candidate(link.text(),"","",source.name,""),title,""))continue;
            if(!visited.add(target))continue;
            TitleBookSearch.Candidate c=parse(source,Jsoup.parse(text(target),target));if(matches(c,title,isbn,byIsbn)){results.add(c);if(stop.accept(c))break;}
            if(visited.size()>=3)break;
        }
        return results;
    }
    private static boolean matches(TitleBookSearch.Candidate c,String title,String isbn,boolean byIsbn){return c!=null&&(byIsbn?isbn.equals(c.isbn):TitleBookSearch.relevant(c,title,""));}
    private static boolean allowed(BookSources.Source source,String target){
        try{URI url=new URI(target),base=new URI(source.get("base"));return "https".equals(url.getScheme())&&base.getHost().equals(url.getHost())&&url.getRawUserInfo()==null&&(url.getPort()==-1||url.getPort()==443)&&Pattern.compile("^(?:"+source.get("path")+").*").matcher(url.getPath()).matches();}catch(Exception e){return false;}
    }
    static TitleBookSearch.Candidate parse(BookSources.Source source,Document doc){
        String title=value(doc,source.get("title").isEmpty()?"h1":source.get("title"),"");if(title.isEmpty())return null;
        TitleBookSearch.Candidate structured=WebBookParser.byTitle(doc.outerHtml(),title,"",source.name);
        String author=AuthorNames.elements(doc,source.get("author"));String code=uniqueIsbn(doc,source.get("isbn"));
        String cover=value(doc,source.get("cover").isEmpty()?"meta[property=og:image]":source.get("cover"),source.get("coverAttribute").isEmpty()?"content":source.get("coverAttribute"));
        if(structured!=null){if(author.isEmpty())author=structured.book.author;if(code.isEmpty()&&source.get("isbn").isEmpty())code=structured.isbn;if(cover.isEmpty())cover=structured.book.cover;}
        if(!cover.isEmpty())try{cover=new URL(new URL(doc.baseUri()),cover).toString().replaceFirst("^http://","https://");if(!cover.startsWith("https://"))cover="";}catch(MalformedURLException e){cover="";}
        return new TitleBookSearch.Candidate(title,author,cover,source.name,code);
    }
    private static String value(Document doc,String selector,String attribute){
        if(selector.isEmpty())return "";Element e=doc.selectFirst(selector);if(e==null)return "";
        return !attribute.isEmpty()?e.attr(attribute).trim():e.hasAttr("content")?e.attr("content").trim():e.text().trim();
    }
    private static String uniqueIsbn(Document doc,String selector){
        if(selector.isEmpty())return "";Set<String> codes=new HashSet<>();
        for(Element e:doc.select(selector)){String raw=e.hasAttr("content")?e.attr("content"):e.text();Matcher m=Pattern.compile("(?<![0-9])(?:97[89][0-9\\s–-]{10,20}[0-9]|[0-9][0-9–-]{7,15}[0-9Xx])(?![0-9])").matcher(raw);while(m.find()){String normalized=Isbn.normalize(m.group());if(normalized!=null)codes.add(normalized);}}
        // Several languages/editions on one page do not identify the displayed cover's edition.
        return codes.size()==1?codes.iterator().next():"";
    }
    private String text(String url)throws IOException{BookLookup.LookupControl control=BookLookup.ACTIVE.get();if(control!=null)control.check();byte[] bytes=transport.get(url,3_000_000);if(control!=null)control.check();return new String(bytes,StandardCharsets.UTF_8);}
}
