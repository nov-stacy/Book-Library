package ru.homebooks.library;

import org.json.*;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.zip.*;

/** Versioned portable archive. Input is staged and fully validated before touching the library. */
final class LibraryBackup {
    private static final long MAX_ENTRY=16L*1024*1024, MAX_TOTAL=512L*1024*1024;
    interface Covers { File get(String isbn); }
    static final class Archive implements Closeable {
        final List<Book> books;
        final File directory;
        final List<BookCollection> collections;
        final List<BookType> bookTypes;
        final Map<Integer,Integer> readingChallenges;
        final List<BookSeries> series;
        Archive(List<Book> books,File directory){this(books,directory,Collections.emptyList());}
        Archive(List<Book> books,File directory,List<BookCollection> collections){this(books,directory,collections,Collections.emptyList());}
        Archive(List<Book> books,File directory,List<BookCollection> collections,List<BookType> bookTypes){this(books,directory,collections,bookTypes,Collections.emptyMap());}
        Archive(List<Book> books,File directory,List<BookCollection> collections,List<BookType> bookTypes,Map<Integer,Integer> readingChallenges){this(books,directory,collections,bookTypes,readingChallenges,Collections.emptyList());}
        Archive(List<Book> books,File directory,List<BookCollection> collections,List<BookType> bookTypes,Map<Integer,Integer> readingChallenges,List<BookSeries> series){this.books=books;this.directory=directory;this.collections=collections;this.bookTypes=bookTypes;this.readingChallenges=readingChallenges;this.series=series;}
        File cover(String isbn){return new File(directory,"covers/"+isbn+".jpg");}
        public void close(){remove(directory);}
    }
    static void write(OutputStream output,List<Book> books,Covers covers) throws IOException {
        write(output,books,covers,Collections.emptyList());
    }
    static void write(OutputStream output,List<Book> books,Covers covers,List<BookCollection> collections) throws IOException {
        write(output,books,covers,collections,Collections.emptyList());
    }
    static void write(OutputStream output,List<Book> books,Covers covers,List<BookCollection> collections,List<BookType> bookTypes) throws IOException {
        write(output,books,covers,collections,bookTypes,Collections.emptyMap());
    }
    static void write(OutputStream output,List<Book> books,Covers covers,List<BookCollection> collections,List<BookType> bookTypes,Map<Integer,Integer> readingChallenges) throws IOException {
        write(output,books,covers,collections,bookTypes,readingChallenges,Collections.emptyList());
    }
    static void write(OutputStream output,List<Book> books,Covers covers,List<BookCollection> collections,List<BookType> bookTypes,Map<Integer,Integer> readingChallenges,List<BookSeries> series) throws IOException {
        if(books.size()>50000)throw new IOException("Слишком много книг в копии");
        try(ZipOutputStream zip=new ZipOutputStream(new BufferedOutputStream(output))){
            JSONObject manifest=new JSONObject();JSONArray rows=new JSONArray();
            manifest.put("format","home-library-backup");manifest.put("version",7);
            manifest.put("createdAt",System.currentTimeMillis());
            for(Book b:books){
                JSONObject row=new JSONObject();row.put("isbn",b.isbn);row.put("id",b.id);row.put("title",b.title);row.put("author",b.author);
                row.put("readOn",b.readOn);row.put("addedAt",b.addedAt);row.put("status",b.status.id);row.put("location",b.location.id);row.put("cover",covers.get(b.id).isFile());rows.put(row);
            }
            manifest.put("books",rows);
            JSONArray groups=new JSONArray();for(BookCollection group:collections){JSONObject item=new JSONObject();item.put("name",group.name);item.put("isbns",new JSONArray(group.isbns));groups.put(item);}manifest.put("collections",groups);
            JSONArray types=new JSONArray();for(BookType type:bookTypes){JSONObject item=new JSONObject();item.put("name",type.name);item.put("icon",type.icon);item.put("color",BookType.hex(type.color));item.put("isbns",new JSONArray(type.bookIds));types.put(item);}manifest.put("bookTypes",types);
            JSONArray challenges=new JSONArray();for(Map.Entry<Integer,Integer> challenge:readingChallenges.entrySet()){JSONObject item=new JSONObject();item.put("year",challenge.getKey());item.put("goal",challenge.getValue());challenges.put(item);}manifest.put("readingChallenges",challenges);
            JSONArray seriesRows=new JSONArray();for(BookSeries group:series){JSONObject item=new JSONObject();item.put("name",group.name);item.put("kind",group.kind);if(group.totalParts==null)item.put("totalParts",JSONObject.NULL);else item.put("totalParts",group.totalParts);JSONArray members=new JSONArray();for(BookSeries.Member member:group.members){JSONObject link=new JSONObject();link.put("isbn",member.bookId);link.put("position",member.position);link.put("issueNumber",member.issueNumber);link.put("issueDate",member.issueDate);members.put(link);}item.put("members",members);seriesRows.put(item);}manifest.put("series",seriesRows);
            byte[] metadata=manifest.toString().getBytes(StandardCharsets.UTF_8);
            StringWriter csv=new StringWriter();Csv.write(csv,books);byte[] table=csv.toString().getBytes(StandardCharsets.UTF_8);
            if(metadata.length>MAX_ENTRY||table.length>MAX_ENTRY)throw new IOException("Превышен допустимый размер копии");
            long total=metadata.length+table.length;
            entry(zip,"library.json",metadata);entry(zip,"books.csv",table);
            for(int i=0;i<books.size();i++)if(rows.getJSONObject(i).getBoolean("cover")){
                Book b=books.get(i);zip.putNextEntry(new ZipEntry("covers/"+b.id+".jpg"));
                try(InputStream in=new FileInputStream(covers.get(b.id))){total+=copy(in,zip,Math.min(MAX_ENTRY,MAX_TOTAL-total));}
                zip.closeEntry();
            }
        }catch(JSONException e){throw new IOException("Не удалось собрать копию",e);}
    }
    private static void entry(ZipOutputStream zip,String name,byte[] bytes)throws IOException{
        zip.putNextEntry(new ZipEntry(name));zip.write(bytes);zip.closeEntry();
    }
    static Archive read(InputStream input,File cache)throws IOException{
        File directory=new File(cache,"restore-"+UUID.randomUUID());
        if(!directory.mkdirs())throw new IOException("Недостаточно места для проверки копии");
        boolean valid=false;
        try{
            Set<String> names=new HashSet<>();long total=0;
            try(ZipInputStream zip=new ZipInputStream(new BufferedInputStream(input))){
                ZipEntry item;
                while((item=zip.getNextEntry())!=null){
                    String name=item.getName();
                    if(item.isDirectory()||!(name.equals("library.json")||name.equals("books.csv")||name.matches("covers/(?:[0-9]{13}|local-[0-9a-f-]{36})\\.jpg"))||!names.add(name))throw new IOException("Неверная структура резервной копии");
                    File file=new File(directory,name);File parent=file.getParentFile();
                    if(!parent.isDirectory()&&!parent.mkdirs())throw new IOException("Не удалось подготовить файлы");
                    try(OutputStream out=new FileOutputStream(file)){total+=copy(zip,out,Math.min(MAX_ENTRY,MAX_TOTAL-total));}
                    zip.closeEntry();
                }
            }
            File metadata=new File(directory,"library.json");
            if(!metadata.isFile())throw new IOException("В файле нет данных резервной копии");
            ByteArrayOutputStream bytes=new ByteArrayOutputStream();try(InputStream in=new FileInputStream(metadata)){copy(in,bytes,MAX_ENTRY);}
            JSONObject manifest=new JSONObject(new String(bytes.toByteArray(),StandardCharsets.UTF_8));
            if(!"home-library-backup".equals(manifest.getString("format"))||(manifest.getInt("version")<1||manifest.getInt("version")>7))throw new IOException("Этот формат копии не поддерживается");
            JSONArray rows=manifest.getJSONArray("books");if(rows.length()>50000)throw new IOException("Слишком много книг в копии");
            List<Book> books=new ArrayList<>();Set<String> codes=new HashSet<>();
            for(int i=0;i<rows.length();i++){
                JSONObject row=rows.getJSONObject(i);String isbn=row.getString("isbn"),title=row.getString("title"),author=row.getString("author");
                String key=manifest.getInt("version")>=3?row.getString("id"):isbn;
                int location=row.optInt("location",0);if(location<0||location>2)throw new IOException("Некорректное местонахождение книги");
                String readOn=row.optString("readOn","");if(!ReadingDate.valid(readOn))throw new IOException("Некорректная дата прочтения");
                int status=row.getInt("status");long date=row.getLong("addedAt");
                if(!Book.validId(key)||(!isbn.isEmpty()&&(!isbn.equals(Isbn.normalize(isbn))||(!Book.isLocalId(key)&&!isbn.equals(key))))||(isbn.isEmpty()&&!Book.isLocalId(key))||!codes.add(key)||title.trim().isEmpty()||title.length()>10000||author.length()>10000||date<0||status<0||status>3)throw new IOException("Некорректные данные книги в копии");
                boolean cover=row.getBoolean("cover");String coverName="covers/"+key+".jpg";
                if(cover!=names.contains(coverName)||(cover&&new File(directory,coverName).length()==0))throw new IOException("В копии отсутствует обложка или нарушена структура");
                names.remove(coverName);books.add(new Book(key,isbn,title,author,date,ReadingStatus.fromId(status),BookLocation.fromId(location),readOn));
            }
            names.remove("library.json");names.remove("books.csv");if(!names.isEmpty())throw new IOException("В копии есть обложки неизвестных книг");
            List<BookCollection> groups=new ArrayList<>();Set<String> groupNames=new HashSet<>();
            if(manifest.getInt("version")>=2){
                JSONArray data=manifest.getJSONArray("collections");if(data.length()>10000)throw new IOException("Слишком много коллекций");
                for(int i=0;i<data.length();i++){
                    JSONObject group=data.getJSONObject(i);String name=group.getString("name").trim();
                    if(name.isEmpty()||name.length()>200||!groupNames.add(name.toLowerCase(Locale.ROOT)))throw new IOException("Некорректная коллекция");
                    JSONArray members=group.getJSONArray("isbns");Set<String> isbns=new LinkedHashSet<>();for(int j=0;j<members.length();j++){String isbn=members.getString(j);if(!codes.contains(isbn))throw new IOException("Неизвестная книга в коллекции");isbns.add(isbn);}
                    groups.add(new BookCollection(0,name,new ArrayList<>(isbns)));
                }
            }
            List<BookType> types=new ArrayList<>();Set<String> typeNames=new HashSet<>();Set<Integer> typeColors=new HashSet<>();Set<String> typedBooks=new HashSet<>();
            if(manifest.getInt("version")>=5){
                JSONArray data=manifest.getJSONArray("bookTypes");if(data.length()>10000)throw new IOException("Слишком много типов книг");
                for(int i=0;i<data.length();i++){
                    JSONObject item=data.getJSONObject(i);String name=item.getString("name").trim(),icon=item.getString("icon");int color;
                    try{color=BookType.parseColor(item.getString("color"));}catch(IllegalArgumentException e){throw new IOException("Некорректный цвет типа книги");}
                    if(name.isEmpty()||name.length()>80||!typeNames.add(name.toLowerCase(Locale.ROOT))||!BookType.validIcon(icon)||!typeColors.add(color))throw new IOException("Некорректный тип книги");
                    JSONArray members=item.getJSONArray("isbns");Set<String> ids=new LinkedHashSet<>();for(int j=0;j<members.length();j++){String id=members.getString(j);if(!codes.contains(id)||!typedBooks.add(id))throw new IOException("Некорректная книга в типе");ids.add(id);}
                    types.add(new BookType(0,name,icon,color,new ArrayList<>(ids)));
                }
            }
            Map<Integer,Integer> challenges=new LinkedHashMap<>();
            if(manifest.getInt("version")>=6){
                JSONArray data=manifest.getJSONArray("readingChallenges");if(data.length()>1000)throw new IOException("Слишком много целей чтения");
                for(int i=0;i<data.length();i++){JSONObject item=data.getJSONObject(i);int year=item.getInt("year"),goal=item.getInt("goal");if(year<1||year>9999||goal<1||goal>999||challenges.put(year,goal)!=null)throw new IOException("Некорректная цель чтения");}
            }
            List<BookSeries> series=new ArrayList<>();Set<String> seriesNames=new HashSet<>(),seriesBooks=new HashSet<>();
            if(manifest.getInt("version")>=7){JSONArray data=manifest.getJSONArray("series");if(data.length()>10000)throw new IOException("Слишком много серий");for(int i=0;i<data.length();i++){JSONObject item=data.getJSONObject(i);String name=item.getString("name").trim();int kind=item.getInt("kind");Integer totalParts=item.isNull("totalParts")?null:item.getInt("totalParts");if(name.isEmpty()||name.length()>200||!seriesNames.add(name.toLowerCase(Locale.ROOT))||(kind!=BookSeries.BOOKS&&kind!=BookSeries.PERIODICAL)||(totalParts!=null&&(totalParts<1||totalParts>999))||(kind==BookSeries.PERIODICAL&&totalParts!=null))throw new IOException("Некорректная серия");JSONArray links=item.getJSONArray("members");List<BookSeries.Member> members=new ArrayList<>();for(int j=0;j<links.length();j++){JSONObject link=links.getJSONObject(j);String id=link.getString("isbn"),position=link.getString("position"),number=link.getString("issueNumber"),date=link.getString("issueDate");if(!codes.contains(id)||!seriesBooks.add(id)||position.length()>40||number.length()>40||!ReadingDate.valid(date))throw new IOException("Некорректная книга в серии");members.add(new BookSeries.Member(id,position,number,date));}series.add(new BookSeries(0,name,kind,totalParts,members));}}
            valid=true;return new Archive(books,directory,groups,types,challenges,series);
        }catch(JSONException e){throw new IOException("Повреждены данные резервной копии",e);}
        finally{if(!valid)remove(directory);}
    }
    private static long copy(InputStream in,OutputStream out,long limit)throws IOException{
        byte[] buffer=new byte[16384];long size=0;int n;
        while((n=in.read(buffer))!=-1){size+=n;if(size>limit)throw new IOException("Превышен допустимый размер копии");out.write(buffer,0,n);}return size;
    }
    private static void remove(File file){File[] children=file.listFiles();if(children!=null)for(File child:children)remove(child);file.delete();}
}
