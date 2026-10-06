package ru.homebooks.library;

import android.content.Context;
import android.database.sqlite.SQLiteDatabase;
import androidx.test.platform.app.InstrumentationRegistry;
import org.junit.Test;
import java.io.*;
import java.util.*;
import static org.junit.Assert.*;

public class SingleCollectionTest {
    @Test public void migrationKeepsAmbiguousLinksUntilChoiceAndEnforcesOneActiveCollection(){
        Context context=InstrumentationRegistry.getInstrumentation().getTargetContext();assertEquals("ru.homebooks.library.testbed",context.getPackageName());
        String name="single-migration-"+UUID.randomUUID()+".db",id=Book.newLocalId();LibraryStore store=new LibraryStore(context,name);
        try{
            store.save(new Book(id,"Книга","Автор",42,ReadingStatus.READ,BookLocation.PARENTS,"2026-01-03"));
            long a=store.createCollection("А"),b=store.createCollection("Б");store.setBookCollections(id,Collections.singleton(a));store.close();
            SQLiteDatabase old=context.openOrCreateDatabase(name,0,null);
            old.execSQL("DROP INDEX one_collection_per_book");old.execSQL("DROP TABLE collection_choices");
            old.execSQL("INSERT INTO collection_books VALUES (?,?)",new Object[]{b,id});old.setVersion(6);old.close();
            store=new LibraryStore(context,name);
            assertEquals("2026-01-03",store.find(id).readOn);assertEquals(Collections.singleton(id),store.pendingCollectionBooks());
            assertEquals(Collections.singletonList(id),store.collectionIsbns(a));assertEquals(Collections.singletonList(id),store.collectionIsbns(b));
            assertEquals("Выбрать коллекцию",store.collectionLabels().get(id));
            try{store.setBookCollections(id,Arrays.asList(a,b));fail();}catch(IllegalArgumentException expected){}
            assertEquals(Collections.singleton(id),store.pendingCollectionBooks());
            store.setBookCollections(id,Collections.singleton(b));store.close();store=new LibraryStore(context,name);
            assertTrue(store.pendingCollectionBooks().isEmpty());assertTrue(store.collectionIsbns(a).isEmpty());assertEquals("Б",store.collectionLabels().get(id));
            try{store.getWritableDatabase().execSQL("INSERT INTO collection_books VALUES (?,?)",new Object[]{a,id});fail();}catch(android.database.sqlite.SQLiteConstraintException expected){}
            store.setCollectionBooks(a,Collections.singleton(id));assertTrue(store.collectionIsbns(b).isEmpty());assertEquals("А",store.collectionLabels().get(id));
            store.setBookCollections(id,Collections.emptyList());assertFalse(store.collectedBookIds().contains(id));assertNotNull(store.find(id));
        }finally{store.close();context.deleteDatabase(name);}
    }
    @Test public void oldZipPreservesAllChoicesAndDoesNotOverrideLaterDecision()throws Exception{
        Context context=InstrumentationRegistry.getInstrumentation().getTargetContext();assertEquals("ru.homebooks.library.testbed",context.getPackageName());
        String name="single-import-"+UUID.randomUUID()+".db",id=Book.newLocalId();LibraryStore store=new LibraryStore(context,name);
        try{
            List<Book> books=Collections.singletonList(new Book(id,"Книга","",1));
            List<BookCollection> groups=Arrays.asList(new BookCollection(1,"А",Collections.singletonList(id)),new BookCollection(2,"Б",Collections.singletonList(id)));
            ByteArrayOutputStream data=new ByteArrayOutputStream();LibraryBackup.write(data,books,code->new File(context.getCacheDir(),"no-cover-"+id),groups);
            try(LibraryBackup.Archive archive=LibraryBackup.read(new ByteArrayInputStream(data.toByteArray()),context.getCacheDir())){
                assertEquals(1,store.restoreMissing(archive));assertEquals(Collections.singleton(id),store.pendingCollectionBooks());
                ByteArrayOutputStream exported=new ByteArrayOutputStream();LibraryBackup.write(exported,store.all(),store::cover,store.collections());
                try(LibraryBackup.Archive backup=LibraryBackup.read(new ByteArrayInputStream(exported.toByteArray()),context.getCacheDir())){assertEquals(2,backup.collections.size());for(BookCollection group:backup.collections)assertEquals(Collections.singletonList(id),group.isbns);}
                long chosen=store.collections().get(1).id;store.setBookCollections(id,Collections.singleton(chosen));
                assertEquals(0,store.restoreMissing(archive));assertTrue(store.pendingCollectionBooks().isEmpty());assertEquals("Б",store.collectionLabels().get(id));
            }
        }finally{store.close();context.deleteDatabase(name);}
    }
}
