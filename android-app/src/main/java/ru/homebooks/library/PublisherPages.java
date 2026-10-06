package ru.homebooks.library;

import org.json.*;
import org.jsoup.Jsoup;
import org.jsoup.nodes.*;
import java.util.*;
import java.util.regex.*;

/** Publisher-specific edition data; never match ISBNs in recommendations or page-wide text. */
final class PublisherPages {
    static BookLookup.Metadata ast(String html,String isbn){
        Document doc=Jsoup.parse(html);
        Element code=doc.selectFirst("meta[property=books:isbn]"),title=doc.selectFirst("h1");
        if(code==null||title==null||!isbn.equals(Isbn.normalize(code.attr("content")))||title.text().trim().isEmpty())return null;
        Element author=doc.selectFirst("meta[property=books:author]"),cover=doc.selectFirst("meta[property=og:image]");
        String image=cover==null?"":cover.attr("content");
        if(!image.startsWith("https://cdn.ast.ru/"))image="";
        return new BookLookup.Metadata(title.text().trim(),AuthorNames.elements(doc,"meta[property=books:author]"),image,"АСТ");
    }
    static BookLookup.Metadata mif(String html,String isbn){
        Element data=Jsoup.parse(html).selectFirst("script#__NEXT_DATA__");if(data==null)return null;
        try{
            JSONObject product=new JSONObject(data.data()).getJSONObject("props").getJSONObject("pageProps")
                .getJSONObject("storeSnapshot").getJSONObject("productCardStore").getJSONObject("product");
            Document release=Jsoup.parse(product.optString("releaseParameters",""));boolean matches=false;
            for(Element p:release.select("p")){
                Matcher m=Pattern.compile("(?i)^ISBN\\s*:?\\s*([0-9Xx\\s–-]+)$").matcher(p.text().trim());
                if(m.matches()&&isbn.equals(Isbn.normalize(m.group(1))))matches=true;
            }
            if(!matches)return null;
            JSONObject base=product.getJSONObject("baseData");String title=base.optString("title","").trim();if(title.isEmpty())return null;
            List<String> authors=new ArrayList<>();JSONArray names=base.optJSONArray("authors");
            if(names!=null)for(int i=0;i<names.length();i++){JSONObject a=names.optJSONObject(i);if(a!=null&&!a.optString("name","").isEmpty())authors.add(a.optString("name"));}
            JSONObject cover=base.optJSONObject("cover");String image=cover==null?"":cover.optString("large",cover.optString("small",""));
            if(image.startsWith("/assets/"))image="https://www.mann-ivanov-ferber.ru"+image;
            if(!image.startsWith("https://www.mann-ivanov-ferber.ru/assets/"))image="";
            return new BookLookup.Metadata(title,AuthorNames.join(authors),image,"МИФ");
        }catch(JSONException e){return null;}
    }
    static BookLookup.Metadata threeTotal(String json,String isbn){
        try{
            JSONObject product=new JSONObject(json);JSONArray variants=product.optJSONArray("variants");
            if(variants==null)return null;
            for(int i=0;i<variants.length();i++){
                JSONObject variant=variants.optJSONObject(i);if(variant==null||!isbn.equals(Isbn.normalize(variant.optString("barcode",""))))continue;
                String title=product.optString("title","").trim();if(title.isEmpty())return null;
                JSONObject ownImage=variant.optJSONObject("featured_image");
                String image=ownImage==null?product.optString("featured_image",""):ownImage.optString("src","");
                if(image.startsWith("//"))image="https:"+image;
                if(!image.startsWith("https://cdn.shopify.com/")&&!image.startsWith("https://store.3dtotal.com/cdn/"))image="";
                if(!image.isEmpty())image+=(image.contains("?")?"&":"?")+"width=640";
                return new BookLookup.Metadata(title,threeTotalAuthor(product),image,"3dtotal Publishing");
            }
        }catch(JSONException ignored){}
        return null;
    }

    static String threeTotalAuthor(JSONObject product){
        String author=AuthorNames.json(product.opt("author"));
        if(author.isEmpty())author=AuthorNames.json(product.opt("authors"));
        if(author.isEmpty())author=AuthorNames.labelled(Jsoup.parse(product.optString("description",product.optString("body_html",""))));
        return author;
    }
    static String threeTotalAuthorPage(String html,String title){
        Document doc=Jsoup.parse(html);Element h=doc.selectFirst("h1");
        if(h==null||!TitleBookSearch.relevant(new TitleBookSearch.Candidate(h.text(),"","","",""),title,""))return "";
        BookSources.Source source=BookSources.named("3dtotal");String author=AuthorNames.elements(doc,source.get("author"));
        if(author.isEmpty()){java.util.List<String> names=new java.util.ArrayList<>();for(Element section:doc.select(source.get("authorDetails")))names.add(AuthorNames.labelled(section));author=AuthorNames.join(names);}
        if(author.isEmpty()){TitleBookSearch.Candidate structured=WebBookParser.byTitle(html,title,"","3dtotal Publishing");if(structured!=null)author=structured.book.author;}
        return author;
    }

}
