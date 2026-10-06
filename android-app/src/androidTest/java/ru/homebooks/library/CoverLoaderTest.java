package ru.homebooks.library;

import androidx.test.platform.app.InstrumentationRegistry;
import org.junit.Test;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import static org.junit.Assert.*;

public class CoverLoaderTest {
    @Test public void existingAndNewlyAddedCoversAreNeverReplaced()throws Exception{
        LibraryApp app=(LibraryApp)InstrumentationRegistry.getInstrumentation().getTargetContext().getApplicationContext();assertEquals("ru.homebooks.library.testbed",app.getPackageName());
        String existing=Book.newLocalId(),manual=Book.newLocalId(),queue="test-cover-"+UUID.randomUUID();
        CountDownLatch requested=new CountDownLatch(1),release=new CountDownLatch(1);AtomicInteger calls=new AtomicInteger();
        CoverLoader loader=new CoverLoader(app,isbn->{calls.incrementAndGet();requested.countDown();try{release.await(5,TimeUnit.SECONDS);}catch(InterruptedException e){Thread.currentThread().interrupt();}return new byte[]{9};},queue);
        try{
            app.io.submit(()->{app.store.save(new Book(existing,"9780306406157","Первая","",0,ReadingStatus.NONE,BookLocation.HOME,""));app.store.save(new Book(manual,"9785042296574","Вторая","",0,ReadingStatus.NONE,BookLocation.HOME,""));app.store.saveCover(existing,new byte[]{7});return null;}).get();
            loader.enqueue(Arrays.asList(existing,manual));assertTrue(requested.await(5,TimeUnit.SECONDS));
            app.io.submit(()->{app.store.saveCover(manual,new byte[]{8});return null;}).get();release.countDown();
            long until=System.currentTimeMillis()+5000;while(System.currentTimeMillis()<until){InstrumentationRegistry.getInstrumentation().waitForIdleSync();if(!loader.state.getValue().running)break;Thread.sleep(20);}
            assertFalse(loader.state.getValue().running);assertEquals(1,calls.get());assertEquals(0,loader.state.getValue().saved);
            try(java.io.FileInputStream in=new java.io.FileInputStream(app.store.cover(existing))){assertEquals(7,in.read());}
            try(java.io.FileInputStream in=new java.io.FileInputStream(app.store.cover(manual))){assertEquals(8,in.read());}
        }finally{release.countDown();loader.cancel();app.io.submit(()->app.store.delete(Arrays.asList(existing,manual))).get();app.getSharedPreferences(queue,0).edit().clear().commit();}
    }

    @Test public void cancelDiscardsLateResponseClearsQueueAndAllowsRetry()throws Exception{
        LibraryApp app=(LibraryApp)InstrumentationRegistry.getInstrumentation().getTargetContext().getApplicationContext();assertEquals("ru.homebooks.library.testbed",app.getPackageName());
        String first=Book.newLocalId(),second=Book.newLocalId(),queue="test-cover-"+UUID.randomUUID();
        CountDownLatch requested=new CountDownLatch(1),release=new CountDownLatch(1),returned=new CountDownLatch(1);AtomicInteger calls=new AtomicInteger();
        CoverLoader loader=new CoverLoader(app,isbn->{int call=calls.incrementAndGet();if(call==1){requested.countDown();try{release.await(10,TimeUnit.SECONDS);}catch(InterruptedException e){Thread.currentThread().interrupt();}finally{returned.countDown();}}return new byte[]{1,2,3};},queue);
        try{
            app.io.submit(()->{app.store.save(new Book(first,"9780306406157","Первая","",0,ReadingStatus.NONE,BookLocation.HOME,""));app.store.save(new Book(second,"9785042296574","Вторая","",0,ReadingStatus.NONE,BookLocation.HOME,""));}).get();
            loader.enqueue(Arrays.asList(first,second));assertTrue(requested.await(5,TimeUnit.SECONDS));loader.cancel();
            InstrumentationRegistry.getInstrumentation().waitForIdleSync();CoverLoader.State cancelled=loader.state.getValue();assertTrue(cancelled.cancelled);assertFalse(cancelled.running);assertEquals(2,cancelled.total);assertEquals(0,cancelled.percent());assertTrue(app.getSharedPreferences(queue,0).getStringSet("isbns",Collections.emptySet()).isEmpty());
            release.countDown();assertTrue(returned.await(5,TimeUnit.SECONDS));
            // A retry may start while the cancelled worker is unwinding.
            loader.enqueue(Collections.singleton(second));
            long until=System.currentTimeMillis()+5000;
            while(System.currentTimeMillis()<until){InstrumentationRegistry.getInstrumentation().waitForIdleSync();CoverLoader.State state=loader.state.getValue();if(!state.running&&!state.cancelled&&state.saved==1)break;Thread.sleep(20);}
            assertEquals(2,calls.get());assertFalse(app.store.cover(first).exists());assertTrue(app.store.cover(second).isFile());assertEquals(100,loader.state.getValue().percent());
        }finally{release.countDown();loader.cancel();app.io.submit(()->app.store.delete(Arrays.asList(first,second))).get();app.getSharedPreferences(queue,0).edit().clear().commit();}
    }
}
