package ru.homebooks.library;

import android.app.job.*;
import android.content.Context;
import android.net.Uri;
import androidx.test.platform.app.InstrumentationRegistry;
import org.junit.Test;
import java.io.*;
import java.util.*;
import static org.junit.Assert.*;

public class DailyBackupTest {
    @Test public void dailyArchiveRestoresAndFailedCopyKeepsPrevious()throws Exception{
        LibraryApp app=(LibraryApp)InstrumentationRegistry.getInstrumentation().getTargetContext().getApplicationContext();
        assertEquals("ru.homebooks.library.testbed",app.getPackageName());
        String id=Book.newLocalId();long group=app.store.createCollection("Daily test "+System.nanoTime());
        List<File> files=new ArrayList<>();
        DailyBackup.Destination destination=new DailyBackup.Destination(){
            public Uri create(Uri tree,String name)throws IOException{File f=File.createTempFile("daily-test-",".zip",app.getCacheDir());files.add(f);return Uri.fromFile(f);}
            public OutputStream open(Uri file)throws IOException{return new FileOutputStream(new File(file.getPath()));}
            public void delete(Uri file){new File(file.getPath()).delete();}
        };
        try{
            app.store.saveWithCollections(new Book(id,"","Проверка копии","Автор",42L,ReadingStatus.READ,BookLocation.HOME,"2026-10-04"),Collections.singletonList(group));
            app.store.saveCover(id,new byte[]{1,2,3});
            DailyBackup.prefs(app).edit().putString("tree","content://test/tree/library").putLong("last",0).commit();
            assertTrue(app.io.submit(()->DailyBackup.writeIfDue(app,()->false,destination)).get());
            long last=DailyBackup.prefs(app).getLong("last",0);assertTrue(last>0);assertEquals(1,files.size());
            try(InputStream in=new FileInputStream(files.get(0));LibraryBackup.Archive archive=LibraryBackup.read(in,app.getCacheDir())){
                Book saved=null;for(Book b:archive.books)if(b.id.equals(id))saved=b;
                assertNotNull(saved);assertEquals("2026-10-04",saved.readOn);assertEquals(ReadingStatus.READ,saved.status);assertTrue(archive.cover(id).exists());
                assertTrue(archive.collections.stream().anyMatch(c->c.isbns.contains(id)));
            }
            assertTrue(app.io.submit(()->DailyBackup.writeIfDue(app,()->false,destination)).get());assertEquals(1,files.size());
            DailyBackup.schedule(app);DailyBackup.schedule(app);
            JobScheduler scheduler=(JobScheduler)app.getSystemService(Context.JOB_SCHEDULER_SERVICE);
            int count=0;for(JobInfo job:scheduler.getAllPendingJobs())if(job.getId()==DailyBackup.JOB_ID){count++;assertTrue(job.isPersisted());assertTrue(job.isPeriodic());}assertEquals(1,count);
            DailyBackup.prefs(app).edit().putLong("last",last-DailyBackup.DAY).commit();
            DailyBackup.Destination failing=new DailyBackup.Destination(){
                public Uri create(Uri tree,String name)throws IOException{return destination.create(tree,name);}
                public OutputStream open(Uri file)throws IOException{throw new IOException("Disk full");}
                public void delete(Uri file)throws IOException{destination.delete(file);}
            };
            assertFalse(app.io.submit(()->DailyBackup.writeIfDue(app,()->false,failing)).get());
            assertTrue(files.get(0).exists());assertFalse(files.get(1).exists());assertEquals(last-DailyBackup.DAY,DailyBackup.prefs(app).getLong("last",0));assertFalse(DailyBackup.prefs(app).getString("error","").isEmpty());
            assertFalse(app.io.submit(()->DailyBackup.writeIfDue(app,()->true,destination)).get());assertEquals(2,files.size());
            DailyBackup.disable(app);assertFalse(DailyBackup.enabled(app));
            for(JobInfo job:scheduler.getAllPendingJobs())assertNotEquals(DailyBackup.JOB_ID,job.getId());
        }finally{DailyBackup.disable(app);DailyBackup.prefs(app).edit().clear().commit();app.store.delete(Collections.singleton(id));app.store.deleteCollection(group);for(File file:files)file.delete();}
    }
}
