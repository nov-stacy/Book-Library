package ru.homebooks.library;

import org.junit.Test;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.atomic.AtomicInteger;
import static org.junit.Assert.*;

public class TitleBookSearchTest {
    private static byte[] bytes(String s){return s.getBytes(StandardCharsets.UTF_8);}
    @Test public void titleResultsAllowMissingIsbnButPrioritizeExactEdition(){
        TitleBookSearch search=new TitleBookSearch((url,limit)->{
            if(url.contains("googleapis.com")){
                assertTrue(url.contains("q=%22Art%22"));assertTrue(url.contains("inauthor%3A%22Author%22"));
                return bytes("{\"items\":[{\"volumeInfo\":{\"title\":\"Art unknown edition\"}},{\"volumeInfo\":{\"title\":\"Art exact edition\",\"industryIdentifiers\":[{\"type\":\"ISBN_13\",\"identifier\":\"9785001178552\"}]}}]}");
            }
            if(url.contains("openlibrary"))return bytes("{\"docs\":[{\"title\":\"Art collected editions\",\"isbn\":[\"9785001178552\"],\"cover_i\":42}]}");
            if(url.contains("mann-ivanov"))return bytes("{\"products\":[]}");
            return bytes("<main></main>");
        },"test-key");
        TitleBookSearch.Result result=search.find("Art","Author","9785001178552");
        assertFalse(result.incomplete);assertEquals(3,result.candidates.size());
        assertEquals("Art exact edition",result.candidates.get(0).book.title);
        assertEquals("ISBN совпадает",result.candidates.get(0).editionNote("978-5-00117-855-2"));
        for(TitleBookSearch.Candidate c:result.candidates)if(c.book.source.equals("Open Library"))assertEquals("",c.isbn);
    }
    @Test public void unavailableCatalogDoesNotHideOtherResults(){
        TitleBookSearch.Result result=new TitleBookSearch((url,limit)->{
            if(url.contains("openlibrary"))return bytes("{\"docs\":[{\"title\":\"Book\",\"author_name\":[\"Writer\"]}]}");
            throw new IOException("offline");
        },"").find("Book","","");
        assertTrue(result.incomplete);assertEquals(1,result.candidates.size());assertEquals("Writer",result.candidates.get(0).book.author);
    }
    @Test public void emptyTitleDoesNotContactSources(){
        AtomicInteger calls=new AtomicInteger();
        assertTrue(new TitleBookSearch((url,limit)->{calls.incrementAndGet();return bytes("");},"").find("  ","","").candidates.isEmpty());assertEquals(0,calls.get());
    }
    @Test public void differentIsbnIsExplicit(){
        TitleBookSearch.Candidate candidate=new TitleBookSearch.Candidate("Book","","","Publisher","9785041729820");
        assertTrue(candidate.editionNote("9785001178552").contains("другое издание"));
        assertEquals("ISBN у источника не указан",new TitleBookSearch.Candidate("Book","","","Publisher","").editionNote("9785001178552"));
    }
    @Test public void publisherTitleSearchReadsEditionAndCover()throws Exception{
        TitleBookSearch.Result result=new TitleBookSearch((url,limit)->{
            if(url.contains("product/search"))return bytes("{\"products\":[{\"url\":\"https://www.mann-ivanov-ferber.ru/catalog/product/art/\"}]}");
            if(url.endsWith("/catalog/product/art/"))return bytes("<script id='__NEXT_DATA__'>{\"props\":{\"pageProps\":{\"storeSnapshot\":{\"productCardStore\":{\"product\":{\"releaseParameters\":\"<p>ISBN 978-5-00117-855-2</p>\",\"baseData\":{\"title\":\"Art\",\"authors\":[{\"name\":\"Artist\"}],\"cover\":{\"large\":\"/assets/art.jpg\"}}}}}}}}</script>");
            if(url.contains("openlibrary"))return bytes("{\"docs\":[]}");
            return bytes("<main></main>");
        },"").find("Art","","9785001178552");
        assertEquals(1,result.candidates.size());TitleBookSearch.Candidate c=result.candidates.get(0);
        assertEquals("9785001178552",c.isbn);assertEquals("Artist",c.book.author);assertEquals("https://www.mann-ivanov-ferber.ru/assets/art.jpg",c.book.cover);
    }
    @Test public void rejectsUnrelatedTitlesAndAllowsSubtitleAndTypography(){
        assertFalse(TitleBookSearch.relevant(new TitleBookSearch.Candidate("Искусство кино","","","", ""),"Непонятное искусство",""));
        assertFalse(TitleBookSearch.relevant(new TitleBookSearch.Candidate("Непонятное искусствоведение","","","", ""),"Непонятное искусство",""));
        assertTrue(TitleBookSearch.relevant(new TitleBookSearch.Candidate("Непонятное искусство. От Моне до Бэнкси","Уилл Гомперц","","", ""),"«НЕПОНЯТНОЕ искусство»","Гомперц"));
        assertTrue(TitleBookSearch.relevant(new TitleBookSearch.Candidate("Всё об искусстве","","","", ""),"Все об искусстве",""));
        assertFalse(TitleBookSearch.relevant(new TitleBookSearch.Candidate("Непонятное искусство","Другой автор","","", ""),"Непонятное искусство","Гомперц"));
    }
    @Test public void googleUsesOnePlainPhraseAndRejectsIrrelevantResults(){
        AtomicInteger googleCalls=new AtomicInteger();
        TitleBookSearch.Result result=new TitleBookSearch((url,limit)->{
            if(url.contains("googleapis")){
                googleCalls.incrementAndGet();
                assertFalse(url.contains("q=intitle"));
                return bytes("{\"items\":[{\"volumeInfo\":{\"title\":\"Непонятное искусство. От Моне до Бэнкси\",\"authors\":[\"Уилл Гомперц\"],\"imageLinks\":{\"thumbnail\":\"https://books.google.com/cover.jpg\"}}},{\"volumeInfo\":{\"title\":\"Искусство\"}}]}");
            }
            if(url.contains("openlibrary"))return bytes("{\"docs\":[{\"title\":\"Искусство кино\"}]}");
            if(url.contains("mann-ivanov"))return bytes("{\"products\":[]}");
            return bytes("<main></main>");
        },"test-key").find("Непонятное искусство","","");
        assertEquals(1,googleCalls.get());assertEquals(1,result.candidates.size());
        assertEquals("Уилл Гомперц",result.candidates.get(0).book.author);assertFalse(result.candidates.get(0).book.cover.isEmpty());
    }
    @Test public void sindbadReadsOnlyOwnEditionAndAuthor(){
        TitleBookSearch.Candidate c=TitleBookSearch.parseSindbad("<div id='content'><h1>Непонятное искусство</h1><div class='product-info'><div class='description'><span>Автор:</span> Уилл Гомперц<br><span>Переводчик:</span> Другой человек</div><div class='image'><a href='https://sindbadbooks.ru/image/cover.png'>Cover</a></div></div><div id='tab-attribute'><table><tr><td>5 - ISBN</td><td>978-5-905891-62-5</td></tr></table></div><div>Рекомендации ISBN 9785906837424</div></div>");
        assertNotNull(c);assertEquals("Уилл Гомперц",c.book.author);assertEquals("9785905891625",c.isbn);
        assertTrue(c.editionNote("9785906837424").contains("другое издание"));assertEquals("https://sindbadbooks.ru/image/cover.png",c.book.cover);
    }
}
