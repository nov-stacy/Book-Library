package ru.homebooks.library;

import org.json.*;
import org.jsoup.nodes.*;
import java.util.*;
import java.util.regex.*;

/** Explicit author credits only: never infer authors from a vendor, reviews or a description's names. */
final class AuthorNames {
    static String clean(String value){
        if(value==null)return "";
        String name=value.replace('\u00a0',' ').replaceAll("\\s+"," ").trim()
            .replaceFirst("(?iu)^(?:авторы?|authors?)\\s*:\\s*","").replaceFirst("(?i)^by\\s+","").trim();
        if(name.isEmpty()||name.matches("(?iu)(?:не указан[ыа]?|unknown|n/?a|https?://.*|/authors/.*)"))return "";
        return name;
    }
    static String join(Collection<String> values){
        Map<String,String> unique=new LinkedHashMap<>();for(String value:values){String name=clean(value);if(!name.isEmpty()&&!unique.containsKey(name.toLowerCase(Locale.ROOT)))unique.put(name.toLowerCase(Locale.ROOT),name);}return String.join(", ",unique.values());
    }
    static String elements(Element root,String selector){
        if(selector.isEmpty())return "";List<String> names=new ArrayList<>();
        for(Element e:root.select(selector)){
            boolean unrelated=false;
            for(Element parent=e;parent!=null&&parent!=root;parent=parent.parent()){
                if(parent.attr("itemprop").matches(".*(?:review|editor|illustrator|translator).*" )||parent.attr("itemtype").endsWith("/Review")||parent.hasClass("review")||parent.hasClass("reviews")||parent.hasClass("related-products")){unrelated=true;break;}
            }
            if(unrelated)continue;
            if(e.hasAttr("content"))names.add(e.attr("content"));
            else if(!e.select("[itemprop=name]").isEmpty())for(Element n:e.select("[itemprop=name]"))names.add(n.text());
            else names.add(e.text());
        }
        return join(names);
    }
    static String json(Object value){List<String> names=new ArrayList<>();collect(value,names,0);return join(names);}
    private static void collect(Object value,List<String> names,int depth){
        if(depth>5)return;
        if(value instanceof String)names.add((String)value);
        else if(value instanceof JSONArray){JSONArray array=(JSONArray)value;for(int i=0;i<array.length();i++)collect(array.opt(i),names,depth+1);}
        else if(value instanceof JSONObject){JSONObject object=(JSONObject)value;String role=object.optString("roleName");if(!role.isEmpty()&&!role.matches("(?iu)authors?|автор[ы]?"))return;
            if(object.has("name"))collect(object.opt("name"),names,depth+1);else if(object.has("author"))collect(object.opt("author"),names,depth+1);
        }
    }
    static String labelled(Element scope){
        // Preserve line boundaries in publisher product specifications before matching the label.
        Element copy=scope.clone();copy.select("script,style").remove();for(Element e:copy.select("br"))e.before("\n");for(Element e:copy.select("p,li,tr,div"))e.before("\n");
        List<String> names=new ArrayList<>();Matcher match=Pattern.compile("(?imu)^\\s*(?:authors?|авторы?)\\s*:\\s*([^\\r\\n]+)").matcher(copy.wholeText());
        while(match.find())names.add(match.group(1));return join(names);
    }
}
