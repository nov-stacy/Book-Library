package ru.homebooks.library;

import org.json.*;
import org.jsoup.Jsoup;
import org.jsoup.nodes.*;
import java.util.*;

/** Extracts only structured book data whose ISBN matches the scanned edition. */
final class WebBookParser {
    static BookLookup.Metadata parse(String html, String isbn, String source) {
        BookLookup.Metadata publisher = BookLookup.parsePublisher(html,isbn);
        if(publisher!=null)return publisher;
        if(source.equals("ast.ru")){BookLookup.Metadata ast=PublisherPages.ast(html,isbn);if(ast!=null)return ast;}
        if(source.equals("www.mann-ivanov-ferber.ru")){BookLookup.Metadata mif=PublisherPages.mif(html,isbn);if(mif!=null)return mif;}
        Document doc=Jsoup.parse(html);
        for(Element script:doc.select("script[type=application/ld+json]"))try{
            BookLookup.Metadata result=find(new JSONTokener(script.data()).nextValue(),isbn,source,0);
            if(result!=null)return result;
        }catch(JSONException ignored){}
        Element code=doc.selectFirst("[itemprop=isbn], meta[property=book:isbn]");
        if(code==null)return null;
        String value=code.hasAttr("content")?code.attr("content"):code.text();
        if(!isbn.equals(Isbn.normalize(value)))return null;
        Element title=doc.selectFirst("h1");if(title==null||title.text().trim().isEmpty())return null;
        Element author=doc.selectFirst("[itemprop=author], meta[name=author]");
        Element cover=doc.selectFirst("meta[property=og:image]");
        return new BookLookup.Metadata(title.text(),scopedAuthors(doc),cover==null?"":safeImage(cover.attr("content")),source);
    }
    static TitleBookSearch.Candidate byTitle(String html,String wanted,String author,String source){
        Document doc=Jsoup.parse(html);Element heading=doc.selectFirst("h1");
        // The visible page title must agree; a matching recommendation is not the main book.
        if(heading==null||!TitleBookSearch.relevant(new TitleBookSearch.Candidate(heading.text(),"","",source,""),wanted,""))return null;
        for(Element script:doc.select("script[type=application/ld+json]"))try{
            TitleBookSearch.Candidate candidate=primary(new JSONTokener(script.data()).nextValue(),wanted,author,heading.text(),source,0);
            if(candidate!=null)return candidate;
        }catch(JSONException ignored){}
        Element type=doc.selectFirst("meta[property=og:type]");
        Element scope=doc.selectFirst("[itemtype$=/Book], [itemtype$=/Product]");
        if(scope==null&&(type==null||!Arrays.asList("book","product","books.book").contains(type.attr("content"))))return null;
        Element own=scope==null?doc:scope;
        Element code=own.selectFirst("[itemprop=isbn], meta[property=book:isbn], meta[property=books:isbn]");
        Element writer=own.selectFirst("[itemprop=author], meta[property=book:author], meta[property=books:author]");
        Element cover=doc.selectFirst("meta[property=og:image]");
        TitleBookSearch.Candidate c=new TitleBookSearch.Candidate(heading.text(),scopedAuthors(doc),cover==null?"":safeImage(cover.attr("content")),source,value(code));
        return TitleBookSearch.relevant(c,wanted,author)?c:null;
    }
    private static String value(Element e){return e==null?"":e.hasAttr("content")?e.attr("content"):e.text();}
    private static TitleBookSearch.Candidate primary(Object node,String wanted,String author,String heading,String source,int depth)throws JSONException{
        if(depth>5)return null;
        if(node instanceof JSONArray){JSONArray a=(JSONArray)node;for(int i=0;i<a.length();i++){TitleBookSearch.Candidate c=primary(a.get(i),wanted,author,heading,source,depth+1);if(c!=null)return c;}}
        if(!(node instanceof JSONObject))return null;JSONObject o=(JSONObject)node;
        Object type=o.opt("@type");
        if(bookType(type)){
            String name=o.optString("name").trim();
            TitleBookSearch.Candidate c=new TitleBookSearch.Candidate(name,names(o.opt("author")),safeImage(image(o.opt("image"))),source,o.optString("isbn",o.optString("gtin13","")));
            if(!name.isEmpty()&&TitleBookSearch.relevant(c,wanted,author)&&TitleBookSearch.relevant(new TitleBookSearch.Candidate(heading,"","",source,""),name,""))return c;
        }
        // Do not descend into related products, ItemLists or reviews.
        for(String key:new String[]{"mainEntity","@graph"})if(o.has(key)){TitleBookSearch.Candidate c=primary(o.get(key),wanted,author,heading,source,depth+1);if(c!=null)return c;}
        return null;
    }
    private static BookLookup.Metadata find(Object node,String isbn,String source,int depth)throws JSONException{
        if(depth>10)return null;
        if(node instanceof JSONArray){JSONArray a=(JSONArray)node;for(int i=0;i<a.length();i++){BookLookup.Metadata m=find(a.get(i),isbn,source,depth+1);if(m!=null)return m;}}
        if(node instanceof JSONObject){JSONObject o=(JSONObject)node;
            if(isbn.equals(Isbn.normalize(o.optString("isbn",o.optString("gtin13",""))))) {
                String title=o.optString("name","").trim();
                if(!title.isEmpty())return new BookLookup.Metadata(title,names(o.opt("author")),safeImage(image(o.opt("image"))),source);
            }
            Iterator<String> keys=o.keys();while(keys.hasNext()){BookLookup.Metadata m=find(o.get(keys.next()),isbn,source,depth+1);if(m!=null)return m;}
        }return null;
    }
    private static String scopedAuthors(Document doc){
        String meta=AuthorNames.elements(doc,"meta[property=book:author], meta[property=books:author]");if(!meta.isEmpty())return meta;
        Element heading=doc.selectFirst("h1");
        if(heading!=null)for(Element scope:doc.select("[itemscope][itemtype$=/Book], [itemscope][itemtype$=/Product]")){
            Element name=scope.selectFirst("h1, [itemprop=name]");
            if(name!=null&&name.text().trim().equalsIgnoreCase(heading.text().trim())){
                String own=AuthorNames.elements(scope,"[itemprop=author]");if(!own.isEmpty())return own;
            }
        }
        return AuthorNames.elements(doc,"meta[name=author]");
    }
    private static boolean bookType(Object type){
        if(type instanceof JSONArray){JSONArray list=(JSONArray)type;for(int i=0;i<list.length();i++)if(bookType(list.opt(i)))return true;return false;}
        return "Book".equals(type)||"Product".equals(type)||"ProductGroup".equals(type);
    }
    private static String names(Object o){return AuthorNames.json(o);}
    private static String image(Object o)throws JSONException{
        if(o instanceof String)return (String)o;
        if(o instanceof JSONObject)return ((JSONObject)o).optString("url",((JSONObject)o).optString("contentUrl",""));
        if(o instanceof JSONArray&&((JSONArray)o).length()>0)return image(((JSONArray)o).get(0));return "";
    }
    private static String safeImage(String s){return s.startsWith("https://")?s:"";}
}
