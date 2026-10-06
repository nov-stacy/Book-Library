package ru.homebooks.library;

import org.json.*;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.*;

/** Packaged, reviewed source definitions. No network configuration or executable rules. */
final class BookSources {
    static final class Source {
        final JSONObject data;
        final String id,name,adapter;
        Source(JSONObject data){this.data=data;id=get("id");name=get("name");adapter=get("adapter");}
        String get(String field){return data.optString(field,"");}
        boolean enabled(){return data.optBoolean("enabled",false);}
        boolean isbn(){return enabled()&&data.optBoolean("isbnEnabled",true);}
        boolean title(){return enabled()&&data.optBoolean("titleEnabled",true);}
        int prefixLength(String isbn){int best=0;JSONArray prefixes=data.optJSONArray("prefixes");if(prefixes!=null)for(int i=0;i<prefixes.length();i++){String p=prefixes.optString(i);if(isbn.startsWith(p))best=Math.max(best,p.length());}return best;}
        String formatIsbn(String isbn){JSONArray groups=data.optJSONArray("isbnGroups");if(groups==null||prefixLength(isbn)==0)return isbn;List<String> parts=new ArrayList<>();int offset=0;for(int i=0;i<groups.length();i++){int size=groups.optInt(i);if(size<=0||offset+size>isbn.length())return isbn;parts.add(isbn.substring(offset,offset+size));offset+=size;}return offset==isbn.length()?String.join("-",parts):isbn;}
    }
    private static final List<Source> ALL=load();
    static List<Source> all(){return ALL;}
    static Source named(String name){for(Source s:ALL)if(s.name.equals(name))return s;throw new IllegalArgumentException("Unknown source: "+name);}
    static List<Source> titleOrder(String title,String isbn,boolean google){
        List<Source> result=new ArrayList<>();for(Source s:ALL)if(s.title()&&(google||!s.adapter.equals("google")))result.add(s);
        String language=title.matches(".*[А-Яа-яЁё].*")?"ru":"en";
        String code=isbn==null?"":isbn;
        // Stable order for equal priorities; a known publisher goes first, then the title's language.
        Collections.sort(result,(a,b)->{int prefix=Integer.compare(b.prefixLength(code),a.prefixLength(code));return prefix!=0?prefix:Integer.compare(a.get("language").equals(language)?0:1,b.get("language").equals(language)?0:1);});
        return result;
    }
    static List<String> isbnOrder(String isbn,boolean google){
        List<Source> eligible=new ArrayList<>();for(Source s:ALL)if(s.isbn()&&(google||!s.adapter.equals("google")))eligible.add(s);
        Source preferred=null;int best=0;for(Source s:eligible)if(s.prefixLength(isbn)>best){best=s.prefixLength(isbn);preferred=s;}
        if(preferred==null)preferred=named(isbn.startsWith("9785")?"Эксмо":google?"Google Books":"Open Library");
        Source second=named(google&&!preferred.adapter.equals("google")?"Google Books":preferred.adapter.equals("openLibrary")?"Эксмо":"Open Library");
        eligible.remove(preferred);eligible.remove(second);
        Collections.sort(eligible,(a,b)->Integer.compare(a.adapter.equals("web")?2:a.adapter.equals("html")?1:0,b.adapter.equals("web")?2:b.adapter.equals("html")?1:0));
        eligible.add(0,second);eligible.add(0,preferred);
        List<String> names=new ArrayList<>();for(Source s:eligible)names.add(s.name);return names;
    }
    private static List<Source> load(){
        try(InputStream input=BookSources.class.getResourceAsStream("/book-sources.json")){
            if(input==null)throw new IOException("Missing book-sources.json");ByteArrayOutputStream bytes=new ByteArrayOutputStream();byte[] buffer=new byte[4096];int n;while((n=input.read(buffer))!=-1)bytes.write(buffer,0,n);
            JSONObject root=new JSONObject(bytes.toString(StandardCharsets.UTF_8.name()));if(root.getInt("version")!=1)throw new IOException("Unsupported source schema");
            JSONArray array=root.getJSONArray("sources");List<Source> sources=new ArrayList<>();Set<String> ids=new HashSet<>(),names=new HashSet<>();
            for(int i=0;i<array.length();i++){Source source=new Source(array.getJSONObject(i));if(source.id.isEmpty()||source.name.isEmpty()||!ids.add(source.id)||!names.add(source.name))throw new IOException("Duplicate/empty source identity");sources.add(source);}
            return Collections.unmodifiableList(sources);
        }catch(IOException|JSONException e){throw new IllegalStateException("Invalid book source registry",e);}
    }
}
