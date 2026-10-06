package ru.homebooks.library;

import org.junit.Test;
import static org.junit.Assert.*;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.*;
import java.util.concurrent.atomic.*;

public class ParallelBookLookupTest {
    private static byte[] bytes(String s){return s.getBytes(StandardCharsets.UTF_8);}
    @Test public void publisherPrefixPrioritizesFirstPair(){
        assertEquals("АСТ",BookLookup.sourceOrder("9785171708894",true).get(0));
        assertEquals("МИФ",BookLookup.sourceOrder("9785001178552",true).get(0));
        assertEquals("3dtotal",BookLookup.sourceOrder("9781912843978",true).get(0));
        assertEquals("Эксмо",BookLookup.sourceOrder("9785041729820",true).get(0));
        assertEquals("Google Books",BookLookup.sourceOrder("9785171708894",true).get(1));
        assertEquals("Веб-поиск",BookLookup.sourceOrder("9785171708894",true).get(BookLookup.sourceOrder("9785171708894",true).size()-1));
    }
    @Test public void bothSourcesStartTogetherAndLoserIsInterrupted()throws Exception{
        CountDownLatch both=new CountDownLatch(2),cancelled=new CountDownLatch(1);AtomicInteger later=new AtomicInteger();
        BookLookup service=new BookLookup((url,limit)->{
            if(url.contains("/search/?q=")){
                both.countDown();try{assertTrue(both.await(2,TimeUnit.SECONDS));}catch(InterruptedException e){throw new IOException(e);}
                return bytes("<a class='book__content' href='/book/test/'>Book</a>");
            }
            if(url.contains("openlibrary.org/isbn/")){
                both.countDown();try{new CountDownLatch(1).await(5,TimeUnit.SECONDS);throw new AssertionError("Loser was not cancelled");}
                catch(InterruptedException e){cancelled.countDown();throw new InterruptedIOException();}
            }
            if(url.endsWith("/book/test/"))return bytes("<h1 class='book-page__card-title'>Book</h1><div class='book-page__copy-isbn'><span class='copy__val'>9785041729820</span></div><meta property='og:image' content='https://cdn.eksmo.ru/test.jpg'>");
            if(url.equals("https://cdn.eksmo.ru/test.jpg"))return bytes("cover");
            later.incrementAndGet();throw new IOException("Should not reach later sources");
        },b->b);
        assertArrayEquals(bytes("cover"),service.findCover("9785041729820"));
        assertTrue(cancelled.await(2,TimeUnit.SECONDS));
    }
    @Test public void titleDiscoveredInParallelRetriesGoogleWithStrictIsbn()throws Exception{
        AtomicInteger titleQueries=new AtomicInteger();
        BookLookup service=new BookLookup((url,limit)->{
            assertFalse(url.contains("q=isbn%3A"));
            if(url.contains("eksmo.ru/search")){
                return bytes("<a class='book__content' href='/book/test/'>Book</a>");
            }
            if(url.endsWith("/book/test/"))return bytes("<h1 class='book-page__card-title'>Learned title</h1><div class='book-page__copy-isbn'><span class='copy__val'>9785041729820</span></div>");
            if(url.contains("q=%22Learned+title%22")){
                titleQueries.incrementAndGet();return bytes("{\"items\":[{\"volumeInfo\":{\"title\":\"Learned title\",\"industryIdentifiers\":[{\"type\":\"ISBN_13\",\"identifier\":\"9785041729820\"}],\"imageLinks\":{\"thumbnail\":\"https://books.google.com/test.jpg\"}}}]}");
            }
            if(url.equals("https://books.google.com/test.jpg"))return bytes("cover");
            throw new IOException("No match");
        },b->b,"test-key");
        assertArrayEquals(bytes("cover"),service.findCover("9785041729820"));assertEquals(1,titleQueries.get());
    }
}
