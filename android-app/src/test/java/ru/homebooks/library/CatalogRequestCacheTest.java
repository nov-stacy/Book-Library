package ru.homebooks.library;
import org.junit.Test;
import static org.junit.Assert.*;
import java.io.*;
import java.net.SocketTimeoutException;
import java.util.concurrent.atomic.*;

public class CatalogRequestCacheTest {
    @Test public void cachesResponsesWithExpiryAndHonorsCallerLimit()throws Exception{
        AtomicLong now=new AtomicLong();AtomicInteger calls=new AtomicInteger();
        CatalogRequestCache cache=new CatalogRequestCache((u,l)->{calls.incrementAndGet();return new byte[]{1,2,3};},now::get);
        byte[] first=cache.get("https://publisher.test/book",10);first[0]=9;
        assertEquals(1,cache.get("https://publisher.test/book",10)[0]);assertEquals(1,calls.get());
        try{cache.get("https://publisher.test/book",2);fail();}catch(IOException expected){}
        now.set(31*60*1000);cache.get("https://publisher.test/book",10);assertEquals(2,calls.get());
    }
    @Test public void failingHostCoolsDownButNotOtherSources()throws Exception{
        AtomicLong now=new AtomicLong();AtomicInteger calls=new AtomicInteger();
        CatalogRequestCache cache=new CatalogRequestCache((u,l)->{calls.incrementAndGet();if(u.contains("slow.test"))throw new SocketTimeoutException();return new byte[]{1};},now::get);
        for(int i=0;i<3;i++)try{cache.get("https://slow.test/"+i,10);fail();}catch(IOException expected){}
        assertEquals(2,calls.get());assertEquals(1,cache.get("https://good.test/book",10)[0]);
        now.set(121000);try{cache.get("https://slow.test/retry",10);}catch(IOException expected){}assertEquals(4,calls.get());
    }
    @Test public void cancellationDoesNotPauseHostAndNotFoundIsPerUrl()throws Exception{
        AtomicInteger calls=new AtomicInteger();CatalogRequestCache cache=new CatalogRequestCache((u,l)->{calls.incrementAndGet();if(u.endsWith("missing"))throw new BookLookup.HttpFailure(404);if(u.endsWith("cancel"))throw new InterruptedIOException();return new byte[]{1};});
        for(int i=0;i<3;i++)try{cache.get("https://publisher.test/cancel",10);}catch(IOException expected){}
        assertEquals(1,cache.get("https://publisher.test/good",10)[0]);
        for(int i=0;i<2;i++)try{cache.get("https://publisher.test/missing",10);}catch(BookLookup.HttpFailure expected){assertEquals(404,expected.status);}
        assertEquals(5,calls.get());
    }
}
