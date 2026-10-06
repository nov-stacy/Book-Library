package ru.homebooks.library;

import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;
import org.junit.*;
import org.junit.runner.RunWith;
import static org.junit.Assert.*;

@RunWith(AndroidJUnit4.class)
public class LibraryStoreTest {
    @Test public void bookSeriesStoreOrderAndIssueMetadataWithoutOwningBooks(){
        android.content.Context context=InstrumentationRegistry.getInstrumentation().getTargetContext();String name="series-"+java.util.UUID.randomUUID()+".db";LibraryStore store=new LibraryStore(context,name);String book=Book.newLocalId(),issue=Book.newLocalId();
        try{store.save(new Book(book,"Крещение огнём","",1));store.save(new Book(issue,"Мир фантастики № 7","",2));long series=store.createBookSeries("Ведьмак",BookSeries.BOOKS,8),periodical=store.createBookSeries("Мир фантастики",BookSeries.PERIODICAL,null);store.setBookSeries(book,series,"5","","");store.setBookSeries(issue,periodical,"","7","2025-07");assertEquals(series,store.bookSeriesIdForBook(book).longValue());assertEquals("5",store.bookSeriesMembership(book).position);assertEquals("2025-07",store.bookSeriesMembership(issue).issueDate);store.updateBookSeries(series,"Сага о Ведьмаке",9);assertEquals(Integer.valueOf(9),store.bookSeries(series).totalParts);store.deleteBookSeries(series);assertNull(store.bookSeriesIdForBook(book));assertNotNull(store.find(book));store.close();store=new LibraryStore(context,name);assertEquals("Мир фантастики",store.bookSeries(periodical).name);}
        finally{store.close();context.deleteDatabase(name);}
    }
    @Test public void readingChallengesPersistAndCanBeRemoved(){
        android.content.Context context=InstrumentationRegistry.getInstrumentation().getTargetContext();String name="challenges-"+java.util.UUID.randomUUID()+".db";LibraryStore store=new LibraryStore(context,name);
        try{store.setReadingChallenge(2026,30);store.setReadingChallenge(2025,24);assertEquals(Integer.valueOf(30),store.readingChallenge(2026));store.setReadingChallenge(2026,36);store.close();store=new LibraryStore(context,name);assertEquals(Integer.valueOf(36),store.readingChallenges().get(2026));store.deleteReadingChallenge(2026);assertNull(store.readingChallenge(2026));try{store.setReadingChallenge(2026,0);fail();}catch(IllegalArgumentException expected){}}
        finally{store.close();context.deleteDatabase(name);}
    }
    @Test public void bookTypesUseUniqueNamesAndColorsAndDeletionKeepsBooks(){
        android.content.Context context=InstrumentationRegistry.getInstrumentation().getTargetContext();String name="types-"+java.util.UUID.randomUUID()+".db";LibraryStore store=new LibraryStore(context,name);String id=Book.newLocalId();
        try{
            assertEquals(9,store.bookTypes().size());store.save(new Book(id,"Книга","",1));long type=store.createBookType("Фотография","camera",0xFF456789);store.setBookType(id,type);assertEquals(type,store.bookTypeIdForBook(id).longValue());assertEquals("Фотография",store.bookTypeLabels().get(id).name);
            try{store.createBookType("Другой","tag",0xFF456789);fail("Duplicate color must fail");}catch(android.database.sqlite.SQLiteConstraintException expected){}
            store.deleteBookType(type);assertNull(store.bookTypeIdForBook(id));assertNotNull(store.find(id));
        }finally{store.close();context.deleteDatabase(name);}
    }
    @Test public void collectionMembershipTracksLastGroupAndDraftSaveIsAtomic(){
        android.content.Context context=InstrumentationRegistry.getInstrumentation().getTargetContext();
        assertEquals("ru.homebooks.library.testbed",context.getPackageName());
        String name="test-collections-"+java.util.UUID.randomUUID()+".db";
        LibraryStore store=new LibraryStore(context,name);
        String id=Book.newLocalId(),other=Book.newLocalId();
        try{
            long a=store.createCollection("Первая"),b=store.createCollection("Вторая");
            store.saveWithCollections(new Book(id,"Без ISBN","Автор",1),java.util.Collections.singleton(a));
            store.setBookCollections(id,java.util.Collections.singleton(b));
            store.saveWithCollections(new Book(other,"Без коллекции","Автор",2),java.util.Collections.emptyList());
            assertEquals(java.util.Collections.singleton(id),store.collectedBookIds());
            store.deleteCollection(a);assertTrue(store.collectedBookIds().contains(id));
            store.deleteCollection(b);assertTrue(store.collectedBookIds().isEmpty());assertNotNull(store.find(id));
            String draft=Book.newLocalId();
            try{store.saveWithCollections(new Book(draft,"Черновик","",3),java.util.Collections.singletonList(b));fail("Deleted collection must fail");}catch(android.database.sqlite.SQLiteConstraintException expected){}
            assertNull(store.find(draft));assertEquals(2,store.all().size());
        }finally{store.close();context.deleteDatabase(name);}
    }

    @Test public void editedLocationPersistsWithoutChangingReadingStatus(){
        android.content.Context context=InstrumentationRegistry.getInstrumentation().getTargetContext();String name="edit-location-"+java.util.UUID.randomUUID()+".db";LibraryStore store=new LibraryStore(context,name);
        String id="9780306406157";
        try{
            store.save(new Book(id,"Книга","Автор",42,ReadingStatus.READ,BookLocation.HOME,"2025-01-01"));
            store.saveEdited(new Book(id,"Книга","Автор",42,ReadingStatus.NONE,BookLocation.PARENTS,"2025-01-01"));
            store.close();store=new LibraryStore(context,name);
            assertEquals(BookLocation.PARENTS,store.find(id).location);assertEquals(ReadingStatus.READ,store.find(id).status);
        }finally{store.close();context.deleteDatabase(name);}
    }

    @Test public void restoreEnrichesIsbnWithoutDuplicatingOrReplacingUserEdits()throws Exception{
        android.content.Context context=InstrumentationRegistry.getInstrumentation().getTargetContext();String name="test-enrich-"+java.util.UUID.randomUUID()+".db";LibraryStore store=new LibraryStore(context,name);
        String id=Book.newLocalId(),isbn="9780306406157";java.io.File directory=new java.io.File(context.getCacheDir(),"enrich-"+java.util.UUID.randomUUID());directory.mkdirs();
        try{
            store.save(new Book(id,"Мои правки","Автор",42,ReadingStatus.READ,BookLocation.PARENTS,"2026-06-05"));long group=store.createCollection("Моя");store.setCollectionBooks(group,java.util.Collections.singleton(id));
            Book incoming=new Book(id,isbn,"Из LiveLib","Другой автор",99,ReadingStatus.WANT,BookLocation.UNKNOWN,"");
            LibraryBackup.Archive archive=new LibraryBackup.Archive(java.util.Collections.singletonList(incoming),directory);
            assertEquals(0,store.restoreMissing(archive));assertEquals(0,store.restoreMissing(archive));store.close();store=new LibraryStore(context,name);
            Book saved=store.find(id);assertEquals(1,store.all().size());assertEquals(isbn,saved.isbn);assertEquals(id,store.findByIsbn(isbn).id);assertEquals("Мои правки",saved.title);assertEquals("2026-06-05",saved.readOn);assertEquals(BookLocation.PARENTS,saved.location);assertEquals(java.util.Collections.singletonList(id),store.collectionIsbns(group));archive.close();
        }finally{store.close();context.deleteDatabase(name);directory.delete();}
    }
    @Test public void migrationPreservesBooksAndReadingDatesSurviveEdits(){
        android.content.Context context=InstrumentationRegistry.getInstrumentation().getTargetContext();
        String name="test-date-"+java.util.UUID.randomUUID()+".db";
        android.database.sqlite.SQLiteDatabase old=context.openOrCreateDatabase(name,0,null);
        old.execSQL("CREATE TABLE books (isbn TEXT PRIMARY KEY NOT NULL,title TEXT NOT NULL,author TEXT NOT NULL,added_at INTEGER NOT NULL,reading_status INTEGER NOT NULL DEFAULT 0,location INTEGER NOT NULL DEFAULT 0)");
        old.execSQL("CREATE TABLE collections (id INTEGER PRIMARY KEY AUTOINCREMENT,name TEXT NOT NULL,name_key TEXT NOT NULL UNIQUE)");
        old.execSQL("CREATE TABLE collection_books (collection_id INTEGER NOT NULL REFERENCES collections(id) ON DELETE CASCADE,isbn TEXT NOT NULL REFERENCES books(isbn) ON DELETE CASCADE,PRIMARY KEY(collection_id,isbn))");
        old.execSQL("INSERT INTO books VALUES ('9780306406157','Книга','Автор',100,3,1)");old.setVersion(4);old.close();
        LibraryStore store=new LibraryStore(context,name);
        try{
            Book b=store.find("9780306406157");assertEquals("",b.readOn);assertEquals(ReadingStatus.READ,b.status);
            store.saveEdited(new Book(b.id,b.title,b.author,b.addedAt,b.status,b.location,"2024-02-29"));
            store.save(new Book(b.id,"Новое название",b.author,200));store.close();store=new LibraryStore(context,name);
            assertEquals("2024-02-29",store.find(b.id).readOn);assertEquals(100,store.find(b.id).addedAt);
            store.saveEdited(new Book(b.id,b.title,b.author,100,b.status,b.location,""));assertEquals("",store.find(b.id).readOn);
        }finally{store.close();context.deleteDatabase(name);}
    }
    @Test public void savesSurviveReopenAndRepeatedIsbnUpdatesOneBook() {
        android.content.Context context = InstrumentationRegistry.getInstrumentation().getTargetContext();
        String databaseName = "test-library-" + java.util.UUID.randomUUID() + ".db";
        LibraryStore store = new LibraryStore(context, databaseName);
        try {
            store.save(new Book("9780306406157", "Первая", "Автор", 100));
            store.save(new Book("9780306406157", "Исправленная", "Автор", 200));
            assertEquals(1, store.all().size()); store.close();
            store = new LibraryStore(context, databaseName);
            Book saved = store.find("9780306406157");
            assertEquals("Исправленная", saved.title); assertEquals(100, saved.addedAt);
        } finally { store.close(); context.deleteDatabase(databaseName); }
    }
    @Test public void deletesOnlySelectedBooksAndTheirCovers() throws Exception {
        android.content.Context context = InstrumentationRegistry.getInstrumentation().getTargetContext();
        String databaseName = "test-delete-" + java.util.UUID.randomUUID() + ".db";
        LibraryStore store = new LibraryStore(context, databaseName);
        String a="9780306406157", b="9780804429573", c="9785042296574";
        try {
            for(String code : java.util.Arrays.asList(a,b,c)) {
                store.save(new Book(code,"Книга", "Автор",100));
                store.saveCover(code,new byte[]{1,2,3});
            }
            assertEquals(2,store.delete(java.util.Arrays.asList(a,b,a)));
            assertNull(store.find(a));assertNull(store.find(b));assertNotNull(store.find(c));
            assertFalse(store.cover(a).exists());assertFalse(store.cover(b).exists());assertTrue(store.cover(c).exists());
            store.close();store=new LibraryStore(context,databaseName);
            assertEquals(1,store.all().size());
            assertEquals(1,store.delete(java.util.Collections.singleton(c)));
            assertTrue(store.all().isEmpty());assertFalse(store.cover(c).exists());
            assertEquals(0,store.delete(java.util.Collections.singleton(c)));
        } finally {store.delete(java.util.Arrays.asList(a,b,c));store.close();context.deleteDatabase(databaseName);}
    }
    @Test public void invalidSelectionCannotPartiallyDeleteBooks() {
        android.content.Context context = InstrumentationRegistry.getInstrumentation().getTargetContext();
        String databaseName = "test-delete-" + java.util.UUID.randomUUID() + ".db";
        LibraryStore store = new LibraryStore(context, databaseName);
        String code="9780306406157";
        try {
            store.save(new Book(code,"Книга","Автор",100));
            try {store.delete(java.util.Arrays.asList(code,"invalid"));fail("Invalid batch must fail");}
            catch(IllegalArgumentException expected) {assertNotNull(store.find(code));}
        } finally {store.close();context.deleteDatabase(databaseName);}
    }
    @Test public void migrationPreservesOldBooksAndStatusSurvivesEditing(){
        android.content.Context context=InstrumentationRegistry.getInstrumentation().getTargetContext();
        String databaseName="test-migration-"+java.util.UUID.randomUUID()+".db";
        String isbn="9780306406157";
        android.database.sqlite.SQLiteDatabase legacy=context.openOrCreateDatabase(databaseName,0,null);
        legacy.execSQL("CREATE TABLE books (isbn TEXT PRIMARY KEY NOT NULL, title TEXT NOT NULL, author TEXT NOT NULL, added_at INTEGER NOT NULL)");
        legacy.execSQL("INSERT INTO books VALUES (?, ?, ?, ?)",new Object[]{isbn,"Старая книга","Автор",123L});
        legacy.setVersion(1);legacy.close();
        LibraryStore store=new LibraryStore(context,databaseName);
        try {
            Book old=store.find(isbn);assertEquals("Старая книга",old.title);assertEquals(123L,old.addedAt);assertEquals(ReadingStatus.NONE,old.status);
            store.setStatus(isbn,ReadingStatus.READING);
            store.save(new Book(isbn,"Исправлено","Автор",999L));store.close();store=new LibraryStore(context,databaseName);
            Book updated=store.find(isbn);assertEquals(ReadingStatus.READING,updated.status);assertEquals("Исправлено",updated.title);assertEquals(123L,updated.addedAt);
        }finally{store.close();context.deleteDatabase(databaseName);}
    }
    @Test public void backupRestoresMissingBooksAndPreservesExistingData() throws Exception {
        android.content.Context context=InstrumentationRegistry.getInstrumentation().getTargetContext();
        String name="test-backup-"+java.util.UUID.randomUUID()+".db";
        LibraryStore store=new LibraryStore(context,name);
        String existing="9780306406157", fresh="9780804429573";
        java.io.File source=java.io.File.createTempFile("cover",".jpg",context.getCacheDir());
        try {
            try(java.io.OutputStream out=new java.io.FileOutputStream(source)){out.write(new byte[]{7,8,9});}
            java.util.List<Book> books=java.util.Arrays.asList(new Book(existing,"Из копии","Автор",42,ReadingStatus.READ),new Book(fresh,"Новая","Ё",123,ReadingStatus.WANT));
            java.io.ByteArrayOutputStream bytes=new java.io.ByteArrayOutputStream();LibraryBackup.write(bytes,books,code->source);
            store.save(new Book(existing,"Мои правки","Я",100,ReadingStatus.READING));store.saveCover(existing,new byte[]{1,2});
            try(LibraryBackup.Archive archive=LibraryBackup.read(new java.io.ByteArrayInputStream(bytes.toByteArray()),context.getCacheDir())){
                java.util.List<Integer> progress=new java.util.ArrayList<>();
                assertEquals(1,store.restoreMissing(archive,(done,total,stage)->{assertTrue(done>=0&&done<=total);progress.add(done);}));
                assertEquals(Integer.valueOf(0),progress.get(0));assertEquals(Integer.valueOf(archive.books.size()+archive.collections.size()+1),progress.get(progress.size()-1));
                progress.clear();assertEquals(0,store.restoreMissing(archive,(done,total,stage)->progress.add(done)));
                assertEquals(Integer.valueOf(archive.books.size()+archive.collections.size()+1),progress.get(progress.size()-1));
            }
            store.close();store=new LibraryStore(context,name);
            assertEquals("Мои правки",store.find(existing).title);assertEquals(ReadingStatus.READING,store.find(existing).status);
            assertEquals(2,store.cover(existing).length());assertEquals(3,store.cover(fresh).length());
            assertEquals("Новая",store.find(fresh).title);assertEquals(123,store.find(fresh).addedAt);assertEquals(ReadingStatus.WANT,store.find(fresh).status);
        } finally {store.delete(java.util.Arrays.asList(existing,fresh));store.close();context.deleteDatabase(name);source.delete();}
    }
    @Test public void failedRestoreRollsBackRowsAndNewCovers() throws Exception {
        android.content.Context context=InstrumentationRegistry.getInstrumentation().getTargetContext();
        String name="test-rollback-"+java.util.UUID.randomUUID()+".db";LibraryStore store=new LibraryStore(context,name);
        String isbn="9780804429573";java.io.File directory=new java.io.File(context.getCacheDir(),"rollback-"+java.util.UUID.randomUUID());
        LibraryBackup.Archive archive=new LibraryBackup.Archive(java.util.Arrays.asList(new Book(isbn,"Новая","",1),new Book("invalid","Ошибка","",1)),directory);
        try {
            archive.cover(isbn).getParentFile().mkdirs();try(java.io.OutputStream out=new java.io.FileOutputStream(archive.cover(isbn))){out.write(new byte[]{1,2});}
            try{store.restoreMissing(archive,(done,total,stage)->assertTrue("Failure must not report completion",done<total));fail("Must fail");}catch(IllegalArgumentException expected){}
            assertTrue(store.all().isEmpty());assertFalse(store.cover(isbn).exists());
        }finally{archive.close();store.close();context.deleteDatabase(name);}
    }
    @Test public void csvImportSkipsDuplicatesAndPreservesExistingMetadata() throws Exception {
        android.content.Context context=InstrumentationRegistry.getInstrumentation().getTargetContext();
        String name="test-csv-"+java.util.UUID.randomUUID()+".db";LibraryStore store=new LibraryStore(context,name);
        try{
            store.save(new Book("9780306406157","Моя книга","Мой автор",123,ReadingStatus.READING));
            java.util.List<Book> books=Csv.read(new java.io.StringReader("ISBN,Название,Статус\n9780306406157,Чужое,Прочитано\n9780804429573,Новая,Хочу прочитать\n9780804429573,Дубль,Прочитано"));
            assertEquals(1,store.importMissing(books));assertEquals(0,store.importMissing(books));
            assertEquals("Моя книга",store.find("9780306406157").title);assertEquals(ReadingStatus.READING,store.find("9780306406157").status);
            assertEquals("Новая",store.find("9780804429573").title);assertEquals(ReadingStatus.WANT,store.find("9780804429573").status);
            try{store.importMissing(java.util.Arrays.asList(new Book("9785042296574","Откат","",1),new Book("invalid","Ошибка","",1)));fail();}catch(IllegalArgumentException expected){}
            assertNull(store.find("9785042296574"));
        }finally{store.close();context.deleteDatabase(name);}
    }
    @Test public void collectionsMoveBooksAndDeletionOnlyRemovesMembership() throws Exception {
        android.content.Context context=InstrumentationRegistry.getInstrumentation().getTargetContext();String name="collections-"+java.util.UUID.randomUUID()+".db";LibraryStore store=new LibraryStore(context,name);
        String isbn="9780306406157";
        try{
            store.save(new Book(isbn,"Книга","",1));long a=store.createCollection("Любимые"),b=store.createCollection("Фантастика");
            store.setCollectionBooks(a,java.util.Collections.singleton(isbn));store.setCollectionBooks(b,java.util.Collections.singleton(isbn));
            store.setLocation(isbn,BookLocation.PARENTS);store.save(new Book(isbn,"Новое название","",9));
            assertEquals(BookLocation.PARENTS,store.find(isbn).location);assertEquals(0,store.collectionIsbns(a).size());
            try{store.createCollection(" любимые ");fail();}catch(android.database.sqlite.SQLiteConstraintException expected){}
            store.renameCollection(a,"Избранное");assertEquals("Избранное",store.collection(a).name);
            store.deleteCollection(a);assertNotNull(store.find(isbn));assertEquals(1,store.collectionIsbns(b).size());
            store.delete(java.util.Collections.singleton(isbn));assertTrue(store.collectionIsbns(b).isEmpty());assertNotNull(store.collection(b));
        }finally{store.close();context.deleteDatabase(name);}
    }
    @Test public void versionTwoMigrationPreservesBooksAndAddsCollectionsAndLocation(){
        android.content.Context context=InstrumentationRegistry.getInstrumentation().getTargetContext();String name="v2-"+java.util.UUID.randomUUID()+".db";
        android.database.sqlite.SQLiteDatabase db=context.openOrCreateDatabase(name,0,null);db.execSQL("CREATE TABLE books (isbn TEXT PRIMARY KEY NOT NULL,title TEXT NOT NULL,author TEXT NOT NULL,added_at INTEGER NOT NULL,reading_status INTEGER NOT NULL DEFAULT 0)");
        db.execSQL("INSERT INTO books VALUES ('9780306406157','Книга','Автор',42,2)");db.setVersion(2);db.close();LibraryStore store=new LibraryStore(context,name);
        try{Book b=store.find("9780306406157");assertEquals(42,b.addedAt);assertEquals(ReadingStatus.READING,b.status);assertEquals(BookLocation.HOME,b.location);assertTrue(store.collections().isEmpty());store.createCollection("Новая");}finally{store.close();context.deleteDatabase(name);}
    }
    @Test public void backupRestoresCollectionsAndLocationAndMergesByName() throws Exception {
        android.content.Context context=InstrumentationRegistry.getInstrumentation().getTargetContext();String name="groups-backup-"+java.util.UUID.randomUUID()+".db";LibraryStore store=new LibraryStore(context,name);
        String isbn="9780306406157";
        try{
            java.util.List<Book> books=java.util.Collections.singletonList(new Book(isbn,"Книга","",1,ReadingStatus.READ,BookLocation.PARENTS));
            java.util.List<BookCollection> groups=java.util.Arrays.asList(new BookCollection(0,"Любимые",java.util.Collections.singletonList(isbn)),new BookCollection(0,"Пустая",java.util.Collections.emptyList()));
            java.io.ByteArrayOutputStream bytes=new java.io.ByteArrayOutputStream();LibraryBackup.write(bytes,books,code->new java.io.File(context.getCacheDir(),"absent-cover"),groups);
            long id=store.createCollection("любимые");
            try(LibraryBackup.Archive archive=LibraryBackup.read(new java.io.ByteArrayInputStream(bytes.toByteArray()),context.getCacheDir())){
                assertEquals(1,store.restoreMissing(archive));assertEquals(0,store.restoreMissing(archive));assertEquals(2,store.collections().size());assertEquals(java.util.Collections.singletonList(isbn),store.collectionIsbns(id));assertEquals(BookLocation.PARENTS,store.find(isbn).location);
            }
        }finally{store.close();context.deleteDatabase(name);}
    }
    @Test public void assigningCollectionsFromBookPreservesOtherMembersAndRollsBackInvalidIds(){
        android.content.Context context=InstrumentationRegistry.getInstrumentation().getTargetContext();String name="book-groups-"+java.util.UUID.randomUUID()+".db";LibraryStore store=new LibraryStore(context,name);
        String first="9780306406157",second="9780804429573";
        try{
            store.save(new Book(first,"Первая","",1));store.save(new Book(second,"Вторая","",1));
            long a=store.createCollection("А"),b=store.createCollection("Б");store.setCollectionBooks(a,java.util.Arrays.asList(first,second));
            store.setBookCollections(first,java.util.Collections.singleton(b));
            assertEquals(java.util.Collections.singletonList(second),store.collectionIsbns(a));assertEquals(java.util.Collections.singletonList(first),store.collectionIsbns(b));
            try{store.setBookCollections(first,java.util.Collections.singletonList(99999L));fail();}catch(android.database.sqlite.SQLiteConstraintException expected){}
            assertEquals(java.util.Collections.singletonList(first),store.collectionIsbns(b));assertEquals(java.util.Collections.singletonList(second),store.collectionIsbns(a));
            store.setBookCollections(first,java.util.Collections.emptyList());assertTrue(store.collectionIsbns(b).isEmpty());assertNotNull(store.find(first));
        }finally{store.close();context.deleteDatabase(name);}
    }
    @Test public void bookWithoutIsbnSupportsEditingCoversCollectionsAndBackup()throws Exception{
        android.content.Context context=InstrumentationRegistry.getInstrumentation().getTargetContext();String name="local-book-"+java.util.UUID.randomUUID()+".db";LibraryStore store=new LibraryStore(context,name);
        Book book=new Book("","Старая книга","Автор",42,ReadingStatus.READ,BookLocation.PARENTS);
        try{
            store.save(book);store.saveCover(book.id,new byte[]{1,2,3});long collection=store.createCollection("Старые книги");store.setBookCollections(book.id,java.util.Collections.singleton(collection));
            store.save(new Book(book.id,"Исправленное название","Автор",99));assertEquals("",store.find(book.id).isbn);assertEquals(42,store.find(book.id).addedAt);assertEquals(BookLocation.PARENTS,store.find(book.id).location);
            java.io.ByteArrayOutputStream bytes=new java.io.ByteArrayOutputStream();LibraryBackup.write(bytes,store.all(),store::cover,store.collections());
            try(LibraryBackup.Archive archive=LibraryBackup.read(new java.io.ByteArrayInputStream(bytes.toByteArray()),context.getCacheDir())){
                store.delete(java.util.Collections.singleton(book.id));assertTrue(store.collectionIsbns(collection).isEmpty());
                assertEquals(1,store.restoreMissing(archive));assertEquals(0,store.restoreMissing(archive));assertEquals(3,store.cover(book.id).length());assertEquals(java.util.Collections.singletonList(book.id),store.collectionIsbns(collection));
                assertEquals("Исправленное название",store.find(book.id).title);
            }
        }finally{store.delete(java.util.Collections.singleton(book.id));store.close();context.deleteDatabase(name);}
    }
}
