package ru.homebooks.library;
import org.junit.Test;
import static org.junit.Assert.*;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.*;
import java.util.concurrent.atomic.*;

public class SearchSpeedTest {
    private static byte[] bytes(String s){return s.getBytes(StandardCharsets.UTF_8);}
    @Test public void exactCoverFinishesWithoutWaitingForSlowCatalog()throws Exception{
        CountDownLatch slowStarted=new CountDownLatch(1),cancelled=new CountDownLatch(1);
        BookLookup lookup=new BookLookup((url,limit)->{
            if(url.contains("openlibrary.org")){
                slowStarted.countDown();try{new CountDownLatch(1).await(4,TimeUnit.SECONDS);}catch(InterruptedException e){cancelled.countDown();throw new InterruptedIOException();}return bytes("{}");
            }
            if(url.contains("eksmo.ru/search")){
                try{assertTrue(slowStarted.await(2,TimeUnit.SECONDS));}catch(InterruptedException e){throw new InterruptedIOException();}
                return bytes("<a class='book__content' href='/book/speed/'>Книга</a>");
            }
            if(url.endsWith("/book/speed/"))return bytes("<h1 class='book-page__card-title'>Книга</h1><div class='book-page__copy-isbn'><span class='copy__val'>9785041729820</span></div><meta property='og:image' content='https://cdn.eksmo.ru/speed.jpg'>");
            if(url.endsWith("speed.jpg"))return bytes("image");
            throw new IOException("No fixture");
        },b->b);
        long start=System.nanoTime();TitleBookSearch.Result result=lookup.findCoverCandidates("Книга","","9785041729820");
        assertFalse(result.candidates.isEmpty());assertEquals("9785041729820",result.candidates.get(0).isbn);
        assertTrue(TimeUnit.NANOSECONDS.toMillis(System.nanoTime()-start)<2500);assertTrue(cancelled.await(1,TimeUnit.SECONDS));
    }
    @Test public void earlyMatchRequiresSameEditionAndExactTitleWithoutIsbn(){
        TitleBookSearch.Candidate other=new TitleBookSearch.Candidate("Книга","Автор","https://cover","Source","9780306406157");
        assertFalse(TitleBookSearch.strong(other,"Книга","Автор","9785041729820"));
        assertTrue(TitleBookSearch.strong(other,"Книга","Автор",""));
        assertFalse(TitleBookSearch.strong(other,"Книга","Другой автор",""));
        assertFalse(TitleBookSearch.strong(other,"Книга часть вторая","",""));
    }
    @Test public void webFallbackSharesRemainingDeadline()throws Exception{
        BookLookup.LookupControl outer=new BookLookup.LookupControl();outer.deadline=System.nanoTime()+TimeUnit.MILLISECONDS.toNanos(250);
        BookLookup.ACTIVE.set(outer);
        long start=System.nanoTime();
        try{
            TitleBookSearch.Result result=new TitleBookSearch((url,limit)->{
                try{new CountDownLatch(1).await(4,TimeUnit.SECONDS);}catch(InterruptedException e){throw new InterruptedIOException();}
                return bytes("{}");
            },"").find("Книга","","");
            assertTrue(result.incomplete);assertTrue(result.candidates.isEmpty());
            assertTrue(TimeUnit.NANOSECONDS.toMillis(System.nanoTime()-start)<1500);
        }finally{BookLookup.ACTIVE.remove();}
    }
    @Test public void metadataSearchNeverDownloadsCover(){
        AtomicInteger imageCalls=new AtomicInteger();
        BookLookup lookup=new BookLookup((url,limit)->{
            if(url.contains("eksmo.ru/search"))return bytes("<a class='book__content' href='/book/meta/'>Книга</a>");
            if(url.endsWith("/book/meta/"))return bytes("<h1 class='book-page__card-title'>Книга</h1><a class='book-page__card-author-link'>Автор</a><div class='book-page__copy-isbn'><span class='copy__val'>9785041729820</span></div><meta property='og:image' content='https://cdn.eksmo.ru/meta.jpg'>");
            if(url.endsWith("meta.jpg")){imageCalls.incrementAndGet();return bytes("image");}
            throw new IOException("No fixture");
        },b->b);
        BookLookup.Result found=lookup.findMetadata("9785041729820");assertEquals("Книга",found.title);assertEquals("Автор",found.author);assertNull(found.cover);assertEquals(0,imageCalls.get());
    }
    @Test public void expiredDeadlineStopsFurtherNetworkWork()throws Exception{
        BookLookup.LookupControl parent=new BookLookup.LookupControl();parent.deadline=System.nanoTime()-1;
        try{parent.child(5).check();fail();}catch(InterruptedIOException expected){}
        BookLookup.ACTIVE.set(parent);AtomicInteger calls=new AtomicInteger();
        try{
            CatalogRequestCache cache=new CatalogRequestCache((u,l)->{calls.incrementAndGet();return bytes("image");});
            try{cache.get("https://publisher.test/book",100);fail();}catch(InterruptedIOException expected){}
            assertEquals(0,calls.get());
        }finally{BookLookup.ACTIVE.remove();}
    }
}
