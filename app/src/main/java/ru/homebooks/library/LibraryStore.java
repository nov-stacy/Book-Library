package ru.homebooks.library;

import android.content.*;
import android.database.Cursor;
import android.database.sqlite.*;
import android.util.AtomicFile;
import java.io.*;
import java.util.*;

public final class LibraryStore extends SQLiteOpenHelper {
    private final File covers;
    public LibraryStore(Context context) { this(context, "library.db"); }
    LibraryStore(Context context, String databaseName) {
        super(context, databaseName, null, 9);
        covers = new File(context.getFilesDir(), "covers");
    }
    @Override public void onConfigure(SQLiteDatabase db){db.setForeignKeyConstraintsEnabled(true);}
    @Override public void onCreate(SQLiteDatabase db) {
        db.execSQL("CREATE TABLE books (isbn TEXT PRIMARY KEY NOT NULL, title TEXT NOT NULL, author TEXT NOT NULL, added_at INTEGER NOT NULL, reading_status INTEGER NOT NULL DEFAULT 0, location INTEGER NOT NULL DEFAULT 0, read_on TEXT NOT NULL DEFAULT '', isbn_value TEXT NOT NULL DEFAULT '')");
        createCollections(db);
        migrateSingleCollection(db);
        createBookTypes(db);
        createReadingChallenges(db);
    }
    @Override public void onUpgrade(SQLiteDatabase db, int from, int to) {
        if(from<2)db.execSQL("ALTER TABLE books ADD COLUMN reading_status INTEGER NOT NULL DEFAULT 0");
        if(from<3)createCollections(db);
        if(from<4)db.execSQL("ALTER TABLE books ADD COLUMN location INTEGER NOT NULL DEFAULT 0");
        if(from<5)db.execSQL("ALTER TABLE books ADD COLUMN read_on TEXT NOT NULL DEFAULT ''");
        if(from<6){db.execSQL("ALTER TABLE books ADD COLUMN isbn_value TEXT NOT NULL DEFAULT ''");db.execSQL("UPDATE books SET isbn_value=isbn WHERE isbn NOT LIKE 'local-%'");}
        if(from<7)migrateSingleCollection(db);
        if(from<8)createBookTypes(db);
        if(from<9)createReadingChallenges(db);
        if(to>9)throw new IllegalStateException("Migration required: " + from + " → " + to);
    }
    private void createCollections(SQLiteDatabase db){
        db.execSQL("CREATE TABLE collections (id INTEGER PRIMARY KEY AUTOINCREMENT, name TEXT NOT NULL, name_key TEXT NOT NULL UNIQUE)");
        db.execSQL("CREATE TABLE collection_books (collection_id INTEGER NOT NULL REFERENCES collections(id) ON DELETE CASCADE, isbn TEXT NOT NULL REFERENCES books(isbn) ON DELETE CASCADE, PRIMARY KEY(collection_id,isbn))");
        db.execSQL("CREATE INDEX collection_books_isbn ON collection_books(isbn)");
    }
    private void migrateSingleCollection(SQLiteDatabase db){
        // Preserve every ambiguous old link until the owner explicitly picks one collection.
        db.execSQL("CREATE TABLE collection_choices (collection_id INTEGER NOT NULL REFERENCES collections(id) ON DELETE CASCADE, isbn TEXT NOT NULL REFERENCES books(isbn) ON DELETE CASCADE, PRIMARY KEY(collection_id,isbn))");
        db.execSQL("INSERT INTO collection_choices SELECT collection_id,isbn FROM collection_books WHERE isbn IN (SELECT isbn FROM collection_books GROUP BY isbn HAVING COUNT(*)>1)");
        db.execSQL("DELETE FROM collection_books WHERE isbn IN (SELECT isbn FROM collection_choices)");
        db.execSQL("CREATE UNIQUE INDEX one_collection_per_book ON collection_books(isbn)");
    }
    private void createBookTypes(SQLiteDatabase db){
        db.execSQL("CREATE TABLE book_types (id INTEGER PRIMARY KEY AUTOINCREMENT, name TEXT NOT NULL, name_key TEXT NOT NULL UNIQUE, icon TEXT NOT NULL, color INTEGER NOT NULL UNIQUE)");
        db.execSQL("CREATE TABLE book_type_books (type_id INTEGER NOT NULL REFERENCES book_types(id) ON DELETE CASCADE, isbn TEXT NOT NULL UNIQUE REFERENCES books(isbn) ON DELETE CASCADE, PRIMARY KEY(type_id,isbn))");
        for(int i=0;i<BookType.DEFAULTS.length;i++){
            ContentValues values=new ContentValues();values.put("name",BookType.DEFAULTS[i][0]);values.put("name_key",BookType.DEFAULTS[i][0].toLowerCase(Locale.ROOT));values.put("icon",BookType.DEFAULTS[i][1]);values.put("color",BookType.PALETTE[i]);db.insertOrThrow("book_types",null,values);
        }
    }
    private void createReadingChallenges(SQLiteDatabase db){
        db.execSQL("CREATE TABLE reading_challenges (year INTEGER PRIMARY KEY NOT NULL, goal INTEGER NOT NULL CHECK(goal BETWEEN 1 AND 999))");
    }
    public Map<Integer,Integer> readingChallenges(){
        Map<Integer,Integer> result=new LinkedHashMap<>();
        try(Cursor c=getReadableDatabase().rawQuery("SELECT year,goal FROM reading_challenges ORDER BY year DESC",null)){while(c.moveToNext())result.put(c.getInt(0),c.getInt(1));}
        return result;
    }
    public Integer readingChallenge(int year){
        try(Cursor c=getReadableDatabase().rawQuery("SELECT goal FROM reading_challenges WHERE year=?",new String[]{Integer.toString(year)})){return c.moveToFirst()?c.getInt(0):null;}
    }
    public void setReadingChallenge(int year,int goal){
        if(year<1||year>9999||goal<1||goal>999)throw new IllegalArgumentException("Цель должна быть от 1 до 999 книг");
        ContentValues values=new ContentValues();values.put("year",year);values.put("goal",goal);
        getWritableDatabase().insertWithOnConflict("reading_challenges",null,values,SQLiteDatabase.CONFLICT_REPLACE);
    }
    public void deleteReadingChallenge(int year){getWritableDatabase().delete("reading_challenges","year=?",new String[]{Integer.toString(year)});}
    public Map<String,String> collectionLabels(){
        Map<String,String> labels=new HashMap<>();
        try(Cursor c=getReadableDatabase().rawQuery("SELECT links.isbn,c.name FROM (SELECT * FROM collection_books UNION SELECT * FROM collection_choices) links JOIN collections c ON c.id=links.collection_id ORDER BY c.name_key",null)){
            while(c.moveToNext()){String key=c.getString(0);labels.put(key,labels.containsKey(key)?"Выбрать коллекцию":c.getString(1));}
        }return labels;
    }
    public Set<String> pendingCollectionBooks(){
        Set<String> ids=new LinkedHashSet<>();try(Cursor c=getReadableDatabase().rawQuery("SELECT isbn FROM collection_choices GROUP BY isbn HAVING COUNT(*)>1 ORDER BY isbn",null)){while(c.moveToNext())ids.add(c.getString(0));}return ids;
    }
    private String validCollectionName(String name){
        String value=name.trim();if(value.isEmpty()||value.length()>200)throw new IllegalArgumentException("Название должно содержать от 1 до 200 символов");return value;
    }
    public long createCollection(String name){
        name=validCollectionName(name);ContentValues v=new ContentValues();v.put("name",name);v.put("name_key",name.toLowerCase(Locale.ROOT));return getWritableDatabase().insertOrThrow("collections",null,v);
    }
    public void renameCollection(long id,String name){
        name=validCollectionName(name);ContentValues v=new ContentValues();v.put("name",name);v.put("name_key",name.toLowerCase(Locale.ROOT));
        if(getWritableDatabase().update("collections",v,"id=?",new String[]{Long.toString(id)})!=1)throw new IllegalStateException("Коллекция удалена");
    }
    public void deleteCollection(long id){getWritableDatabase().delete("collections","id=?",new String[]{Long.toString(id)});}
    public List<BookCollection> collections(){
        List<BookCollection> result=new ArrayList<>();
        try(Cursor c=getReadableDatabase().rawQuery("SELECT id,name FROM collections ORDER BY name_key",null)){
            while(c.moveToNext())result.add(new BookCollection(c.getLong(0),c.getString(1),collectionIsbns(c.getLong(0))));
        }return result;
    }
    public Set<String> collectedBookIds(){
        Set<String> ids=new HashSet<>();
        try(Cursor c=getReadableDatabase().rawQuery("SELECT isbn FROM collection_books UNION SELECT isbn FROM collection_choices",null)){while(c.moveToNext())ids.add(c.getString(0));}
        return ids;
    }
    public void saveWithCollections(Book book,Collection<Long> ids){
        SQLiteDatabase db=getWritableDatabase();db.beginTransaction();
        try{saveEdited(book);setBookCollections(book.id,ids);db.setTransactionSuccessful();}finally{db.endTransaction();}
    }
    public List<String> collectionIsbns(long id){
        List<String> result=new ArrayList<>();try(Cursor c=getReadableDatabase().rawQuery("SELECT isbn FROM collection_books WHERE collection_id=? UNION SELECT isbn FROM collection_choices WHERE collection_id=?",new String[]{Long.toString(id),Long.toString(id)})){while(c.moveToNext())result.add(c.getString(0));}return result;
    }
    public BookCollection collection(long id){for(BookCollection group:collections())if(group.id==id)return group;return null;}
    public void setCollectionBooks(long id,Collection<String> isbns){
        SQLiteDatabase db=getWritableDatabase();db.beginTransaction();
        try{
            if(collection(id)==null)throw new IllegalStateException("Коллекция удалена");
            db.delete("collection_books","collection_id=?",new String[]{Long.toString(id)});
            db.delete("collection_choices","collection_id=?",new String[]{Long.toString(id)});
            for(String isbn:new LinkedHashSet<>(isbns)){db.delete("collection_books","isbn=?",new String[]{isbn});db.delete("collection_choices","isbn=?",new String[]{isbn});ContentValues v=new ContentValues();v.put("collection_id",id);v.put("isbn",isbn);db.insertOrThrow("collection_books",null,v);}
            db.setTransactionSuccessful();
        }finally{db.endTransaction();}
    }
    public void setBookCollections(String isbn,Collection<Long> collectionIds){
        if(new HashSet<>(collectionIds).size()>1)throw new IllegalArgumentException("Выберите одну коллекцию");
        SQLiteDatabase db=getWritableDatabase();db.beginTransaction();
        try{
            if(find(isbn)==null)throw new IllegalStateException("Книга удалена");
            db.delete("collection_books","isbn=?",new String[]{isbn});
            db.delete("collection_choices","isbn=?",new String[]{isbn});
            for(long id:new LinkedHashSet<>(collectionIds)){ContentValues values=new ContentValues();values.put("isbn",isbn);values.put("collection_id",id);db.insertOrThrow("collection_books",null,values);}
            db.setTransactionSuccessful();
        }finally{db.endTransaction();}
    }
    public void removeFromCollection(long id,Collection<String> isbns){
        SQLiteDatabase db=getWritableDatabase();db.beginTransaction();try{for(String isbn:isbns){db.delete("collection_books","collection_id=? AND isbn=?",new String[]{Long.toString(id),isbn});db.delete("collection_choices","collection_id=? AND isbn=?",new String[]{Long.toString(id),isbn});}db.setTransactionSuccessful();}finally{db.endTransaction();}
    }
    private String validBookTypeName(String name){
        String value=name.trim();if(value.isEmpty()||value.length()>80)throw new IllegalArgumentException("Название должно содержать от 1 до 80 символов");return value;
    }
    public long createBookType(String name,String icon,int color){
        name=validBookTypeName(name);if(!BookType.validIcon(icon))throw new IllegalArgumentException("Неизвестная иконка");
        ContentValues values=new ContentValues();values.put("name",name);values.put("name_key",name.toLowerCase(Locale.ROOT));values.put("icon",icon);values.put("color",BookType.opaque(color));
        return getWritableDatabase().insertOrThrow("book_types",null,values);
    }
    public void updateBookType(long id,String name,String icon,int color){
        name=validBookTypeName(name);if(!BookType.validIcon(icon))throw new IllegalArgumentException("Неизвестная иконка");
        ContentValues values=new ContentValues();values.put("name",name);values.put("name_key",name.toLowerCase(Locale.ROOT));values.put("icon",icon);values.put("color",BookType.opaque(color));
        if(getWritableDatabase().update("book_types",values,"id=?",new String[]{Long.toString(id)})!=1)throw new IllegalStateException("Тип удалён");
    }
    public void deleteBookType(long id){getWritableDatabase().delete("book_types","id=?",new String[]{Long.toString(id)});}
    public List<BookType> bookTypes(){
        Map<Long,BookType> result=new LinkedHashMap<>();
        try(Cursor c=getReadableDatabase().rawQuery("SELECT t.id,t.name,t.icon,t.color,b.isbn FROM book_types t LEFT JOIN book_type_books b ON b.type_id=t.id ORDER BY t.name_key,b.isbn",null)){
            while(c.moveToNext()){long id=c.getLong(0);BookType type=result.get(id);if(type==null){type=new BookType(id,c.getString(1),c.getString(2),c.getInt(3),Collections.emptyList());result.put(id,type);}if(!c.isNull(4))type.bookIds.add(c.getString(4));}
        }return new ArrayList<>(result.values());
    }
    public BookType bookType(long id){for(BookType type:bookTypes())if(type.id==id)return type;return null;}
    public List<String> bookTypeBookIds(long id){
        List<String> result=new ArrayList<>();try(Cursor c=getReadableDatabase().rawQuery("SELECT isbn FROM book_type_books WHERE type_id=? ORDER BY isbn",new String[]{Long.toString(id)})){while(c.moveToNext())result.add(c.getString(0));}return result;
    }
    public Map<String,BookType> bookTypeLabels(){
        Map<String,BookType> result=new HashMap<>();
        try(Cursor c=getReadableDatabase().rawQuery("SELECT links.isbn,t.id,t.name,t.icon,t.color FROM book_type_books links JOIN book_types t ON t.id=links.type_id",null)){
            while(c.moveToNext())result.put(c.getString(0),new BookType(c.getLong(1),c.getString(2),c.getString(3),c.getInt(4),Collections.emptyList()));
        }return result;
    }
    public Long bookTypeIdForBook(String isbn){
        try(Cursor c=getReadableDatabase().rawQuery("SELECT type_id FROM book_type_books WHERE isbn=?",new String[]{isbn})){return c.moveToFirst()?c.getLong(0):null;}
    }
    public Set<String> typedBookIds(){
        Set<String> result=new HashSet<>();try(Cursor c=getReadableDatabase().rawQuery("SELECT isbn FROM book_type_books",null)){while(c.moveToNext())result.add(c.getString(0));}return result;
    }
    public void setBookType(String isbn,Long typeId){
        SQLiteDatabase db=getWritableDatabase();db.beginTransaction();try{
            if(find(isbn)==null)throw new IllegalStateException("Книга удалена");
            db.delete("book_type_books","isbn=?",new String[]{isbn});
            if(typeId!=null){ContentValues values=new ContentValues();values.put("type_id",typeId);values.put("isbn",isbn);db.insertOrThrow("book_type_books",null,values);}
            db.setTransactionSuccessful();
        }finally{db.endTransaction();}
    }
    public void setBookTypeBooks(long typeId,Collection<String> bookIds){
        SQLiteDatabase db=getWritableDatabase();db.beginTransaction();try{
            if(bookType(typeId)==null)throw new IllegalStateException("Тип удалён");
            db.delete("book_type_books","type_id=?",new String[]{Long.toString(typeId)});
            for(String isbn:new LinkedHashSet<>(bookIds)){db.delete("book_type_books","isbn=?",new String[]{isbn});ContentValues values=new ContentValues();values.put("type_id",typeId);values.put("isbn",isbn);db.insertOrThrow("book_type_books",null,values);}
            db.setTransactionSuccessful();
        }finally{db.endTransaction();}
    }
    public void saveWithCollectionsAndType(Book book,Collection<Long> collectionIds,Long typeId){
        SQLiteDatabase db=getWritableDatabase();db.beginTransaction();try{saveEdited(book);setBookCollections(book.id,collectionIds);setBookType(book.id,typeId);db.setTransactionSuccessful();}finally{db.endTransaction();}
    }
    private void mergeCollections(List<BookCollection> groups,Set<String> preserve){
        for(BookCollection group:groups){
            long id=-1;for(BookCollection current:collections())if(current.name.toLowerCase(Locale.ROOT).equals(group.name.toLowerCase(Locale.ROOT))){id=current.id;break;}
            if(id<0)id=createCollection(group.name);
            for(String code:group.isbns){
                if(preserve.contains(code))continue;
                SQLiteDatabase db=getWritableDatabase();
                ContentValues values=new ContentValues();values.put("collection_id",id);values.put("isbn",code);
                db.insertWithOnConflict("collection_choices",null,values,SQLiteDatabase.CONFLICT_IGNORE);
            }
        }
    }
    private void mergeBookTypes(List<BookType> incoming,Set<String> preserve){
        for(BookType item:incoming){
            BookType target=null;for(BookType current:bookTypes())if(current.name.equalsIgnoreCase(item.name)){target=current;break;}
            if(target==null){int color=item.color;Set<Integer> used=new HashSet<>();for(BookType current:bookTypes())used.add(current.color);if(used.contains(color)){int[] suggestions=BookType.suggestions(bookTypes(),1);if(suggestions.length==0)continue;color=suggestions[0];}long id=createBookType(item.name,BookType.validIcon(item.icon)?item.icon:"tag",color);target=bookType(id);}
            for(String isbn:item.bookIds)if(!preserve.contains(isbn)&&find(isbn)!=null)setBookType(isbn,target.id);
        }
    }
    public List<Book> all() {
        List<Book> books = new ArrayList<>();
        try (Cursor c = getReadableDatabase().rawQuery("SELECT isbn,title,author,added_at,reading_status,location,read_on,isbn_value FROM books ORDER BY added_at DESC, isbn", null)) {
            while (c.moveToNext()) books.add(read(c));
        }
        return books;
    }
    public Book find(String isbn) {
        try (Cursor c = getReadableDatabase().rawQuery("SELECT isbn,title,author,added_at,reading_status,location,read_on,isbn_value FROM books WHERE isbn=?", new String[]{isbn})) {
            return c.moveToFirst() ? read(c) : null;
        }
    }
    // The legacy SQLite column "isbn" stores the stable key: ISBN or local UUID.
    private Book read(Cursor c) { return new Book(c.getString(0),c.getString(7), c.getString(1), c.getString(2), c.getLong(3), ReadingStatus.fromId(c.getInt(4)),BookLocation.fromId(c.getInt(5)),c.getString(6)); }
    public Book findByIsbn(String isbn){
        if(Isbn.normalize(isbn)==null)return null;
        try(Cursor c=getReadableDatabase().rawQuery("SELECT isbn,title,author,added_at,reading_status,location,read_on,isbn_value FROM books WHERE isbn_value=? ORDER BY added_at LIMIT 1",new String[]{Isbn.normalize(isbn)})){return c.moveToFirst()?read(c):null;}
    }
    public File cover(String isbn) {
        if (!Book.validId(isbn)) throw new IllegalArgumentException("Invalid ISBN");
        return new File(covers, isbn + ".jpg");
    }
    public void save(Book book) { save(book,false); }
    public void saveEdited(Book book) { save(book,true); }
    private void save(Book book,boolean editing) {
        if (!Book.validId(book.id) || (!book.isbn.isEmpty()&&!book.isbn.equals(Isbn.normalize(book.isbn))) || book.title.trim().isEmpty()) throw new IllegalArgumentException("Invalid book");
        ContentValues values = new ContentValues();
        if(editing){values.put("read_on",book.readOn);values.put("location",book.location.id);}
        values.put("title", book.title.trim()); values.put("author", book.author.trim());
        SQLiteDatabase db = getWritableDatabase();
        db.beginTransaction();
        try {
            if (db.update("books", values, "isbn=?", new String[]{book.id}) == 0) {
                values.put("isbn_value",book.isbn);values.put("read_on",book.readOn);values.put("isbn", book.id); values.put("added_at", book.addedAt);values.put("reading_status",book.status.id);values.put("location",book.location.id);
                db.insertOrThrow("books", null, values);
            }
            db.setTransactionSuccessful();
        } finally { db.endTransaction(); }
    }
    public void setLocation(String isbn,BookLocation location){
        ContentValues values=new ContentValues();values.put("location",location.id);
        if(getWritableDatabase().update("books",values,"isbn=?",new String[]{isbn})!=1)throw new IllegalStateException("Book no longer exists");
    }
    public void setStatus(String isbn, ReadingStatus status) {
        ContentValues values=new ContentValues();values.put("reading_status",status.id);
        if(getWritableDatabase().update("books",values,"isbn=?",new String[]{isbn})!=1)throw new IllegalStateException("Book no longer exists");
    }
    /** Deletes exactly the selected ISBNs in one transaction, then removes their covers. */
    public int delete(Collection<String> selected) {
        Set<String> isbns = new LinkedHashSet<>(selected);
        for (String isbn : isbns) {
            if (!Book.validId(isbn)) throw new IllegalArgumentException("Invalid ISBN");
        }
        SQLiteDatabase db = getWritableDatabase();
        int removed = 0;
        db.beginTransaction();
        try {
            for (String isbn : isbns) removed += db.delete("books", "isbn=?", new String[]{isbn});
            db.setTransactionSuccessful();
        } finally { db.endTransaction(); }
        for (String isbn : isbns) new AtomicFile(cover(isbn)).delete();
        return removed;
    }
    /** Merge only missing ISBNs; existing books and covers remain intact. */
    interface RestoreProgress { void update(int done,int total,String stage); }
    public int restoreMissing(LibraryBackup.Archive archive) throws IOException {
        return restoreMissing(archive,(done,total,stage)->{});
    }
    public int restoreMissing(LibraryBackup.Archive archive,RestoreProgress progress) throws IOException {
        SQLiteDatabase db=getWritableDatabase();List<File> written=new ArrayList<>();int added=0;boolean committed=false,successful=false;
        int done=0,total=archive.books.size()+archive.collections.size()+archive.bookTypes.size()+archive.readingChallenges.size()+1;
        progress.update(0,total,"Книги: 0 из "+archive.books.size());
        db.beginTransaction();
        try {
            for(Book book:archive.books){
                Book existing=find(book.id);
                if(existing!=null&&existing.isbn.isEmpty()&&!book.isbn.isEmpty()){
                    ContentValues extra=new ContentValues();extra.put("isbn_value",book.isbn);db.update("books",extra,"isbn=?",new String[]{book.id});
                }
                if(existing==null){
                File source=archive.cover(book.id),target=cover(book.id);
                if(source.isFile()&&!target.exists()){
                    ByteArrayOutputStream bytes=new ByteArrayOutputStream();
                    try(InputStream in=new FileInputStream(source)){byte[] buffer=new byte[16384];int n;while((n=in.read(buffer))!=-1)bytes.write(buffer,0,n);}
                    saveCover(book.id,bytes.toByteArray());written.add(target);
                }
                save(book);added++;
                }
                done++;progress.update(done,total,"Книги: "+done+" из "+archive.books.size());
            }
            Set<String> preserve=collectedBookIds(),preserveTypes=typedBookIds();
            int groupsDone=0;
            for(BookCollection group:archive.collections){
                mergeCollections(Collections.singletonList(group),preserve);groupsDone++;
                progress.update(++done,total,"Коллекции: "+groupsDone+" из "+archive.collections.size());
            }
            int typesDone=0;
            for(BookType type:archive.bookTypes){mergeBookTypes(Collections.singletonList(type),preserveTypes);typesDone++;progress.update(++done,total,"Типы книг: "+typesDone+" из "+archive.bookTypes.size());}
            int goalsDone=0;
            for(Map.Entry<Integer,Integer> challenge:archive.readingChallenges.entrySet()){if(readingChallenge(challenge.getKey())==null)setReadingChallenge(challenge.getKey(),challenge.getValue());goalsDone++;progress.update(++done,total,"Цели чтения: "+goalsDone+" из "+archive.readingChallenges.size());}
            db.execSQL("INSERT OR IGNORE INTO collection_books SELECT collection_id,isbn FROM collection_choices WHERE isbn IN (SELECT isbn FROM collection_choices GROUP BY isbn HAVING COUNT(*)=1)");
            db.execSQL("DELETE FROM collection_choices WHERE isbn IN (SELECT isbn FROM collection_books)");
            progress.update(done,total,"Завершаем сохранение…");
            db.setTransactionSuccessful();successful=true;
        } finally {
            try{db.endTransaction();committed=successful;}
            finally{if(!committed)for(File file:written)new AtomicFile(file).delete();}
        }
        progress.update(total,total,"Библиотека восстановлена");
        return added;
    }
    public int importMissing(List<Book> books) {
        SQLiteDatabase db=getWritableDatabase();int added=0;db.beginTransaction();
        try{for(Book book:books)if(find(book.id)==null){save(book);added++;}db.setTransactionSuccessful();}
        finally{db.endTransaction();}return added;
    }
    public void saveCover(String isbn, byte[] bytes) throws IOException {
        if (!covers.isDirectory() && !covers.mkdirs()) throw new IOException("Cannot create cover directory");
        AtomicFile file = new AtomicFile(cover(isbn));
        FileOutputStream stream = null;
        try { stream = file.startWrite(); stream.write(bytes); file.finishWrite(stream); }
        catch (IOException e) { if (stream != null) file.failWrite(stream); throw e; }
    }
}
