package ru.homebooks.library;

import org.junit.Test;
import static org.junit.Assert.*;
import java.io.*;
import java.nio.charset.StandardCharsets;

public class PublisherLookupTest {
    private String fixture(String name)throws Exception{try(InputStream in=getClass().getResourceAsStream("/publishers/"+name)){return new String(in.readAllBytes(),StandardCharsets.UTF_8);}}
    private static byte[] bytes(String s){return s.getBytes(StandardCharsets.UTF_8);}
    @Test public void eksmoSearchAcceptsMatchingEbookAndRejectsDifferentPrintIsbn()throws Exception{
        String html=fixture("eksmo-ebook.html");
        assertNotNull(BookLookup.parsePublisher(html,"9785041729820"));
        assertNull(BookLookup.parsePublisher(html,"9785041655273"));
        BookLookup lookup=new BookLookup((url,limit)->{
            if(url.contains("/search/?q="))return bytes("<a class='book__content' href='/ebook/action-manga-ITDA39704/'>Action-манга</a>");
            if(url.endsWith("/ebook/action-manga-ITDA39704/"))return bytes(html);
            if(url.startsWith("https://cdn.eksmo.ru/"))return bytes("image");
            throw new IOException("Other source unavailable");
        },b->b);
        assertArrayEquals(bytes("image"),lookup.findCover("9785041729820"));
    }
    @Test public void realPublisherFieldsValidateExactEdition()throws Exception{
        String ast=fixture("ast.html"),mif=fixture("mif.html"),three=fixture("3dtotal.json");
        assertEquals("Mugenoumi. ARTBOOK",PublisherPages.ast(ast,"9785171708894").title);
        assertNull(PublisherPages.ast(ast,"9785001178552"));
        BookLookup.Metadata m=PublisherPages.mif(mif,"9785001178552");assertEquals("Уил Фриборн",m.author);assertTrue(m.cover.startsWith("https://www.mann-ivanov-ferber.ru/assets/"));
        assertNull(PublisherPages.mif(mif,"9785171708894"));
        assertTrue(PublisherPages.threeTotal(three,"9781912843978").cover.startsWith("https://cdn.shopify.com/"));
        assertNull(PublisherPages.threeTotal(three,"9785171708894"));
        assertNull(PublisherPages.mif("<p>ISBN 9785001178552</p>","9785001178552"));
    }
    @Test public void astUsesHyphenatedIsbnAndStopsAfterCover()throws Exception{
        String html=fixture("ast.html");
        BookLookup lookup=new BookLookup((url,limit)->{
            if(url.contains("eksmo.ru"))return bytes("<title>Поиск</title>");
            if(url.equals("https://ast.ru/search/?q=978-5-17-170889-4"))return bytes(html);
            if(url.startsWith("https://cdn.ast.ru/"))return bytes("image");
            throw new IOException("Other source unavailable");
        },b->b);
        assertArrayEquals(bytes("image"),lookup.findCover("9785171708894"));
    }
    @Test public void mifUsesStoredTitleButStillChecksIsbn()throws Exception{
        String html=fixture("mif.html");
        BookLookup lookup=new BookLookup((url,limit)->{
            if(url.contains("eksmo.ru")||url.contains("ast.ru"))return bytes("<title>Поиск</title>");
            if(url.endsWith("query=9785001178552"))return bytes("{\"products\":[]}");
            if(url.contains("/product/search?query="))return bytes("{\"products\":[{\"url\":\"http://www.mann-ivanov-ferber.ru/catalog/product/watercolor/\"}]}");
            if(url.contains("/catalog/product/"))return bytes(html);
            if(url.contains("/assets/"))return bytes("image");
            throw new IOException("Other source unavailable");
        },b->b);
        assertArrayEquals(bytes("image"),lookup.findCover("9785001178552","50 акварельных этюдов"));
    }
    @Test public void threeTotalIgnoresNavigationAndMatchesBarcode()throws Exception{
        String json=fixture("3dtotal.json");
        BookLookup lookup=new BookLookup((url,limit)->{
            if(url.contains("/search?q="))return bytes("<nav><a href='/products/wrong'>Navigation</a></nav><main><ul class='product-grid'><li><a href='/products/tarot?variant=1'>Book</a></li></ul></main>");
            if(url.equals("https://store.3dtotal.com/products/tarot.js"))return bytes(json);
            if(url.startsWith("https://cdn.shopify.com/"))return bytes("image");
            throw new IOException("Other source unavailable");
        },b->b);
        assertArrayEquals(bytes("image"),lookup.findCover("9781912843978"));
    }
    @Test public void googleTitleFallbackRejectsOtherEditionAndStopsOnMatch(){
        java.util.List<String> queries=new java.util.ArrayList<>();
        BookLookup lookup=new BookLookup((url,limit)->{
            if(url.contains("googleapis")){
                queries.add(url);
                assertFalse(url.contains("q=isbn%3A"));
                return bytes("{\"items\":[{\"volumeInfo\":{\"title\":\"Wrong edition\",\"industryIdentifiers\":[{\"type\":\"ISBN_13\",\"identifier\":\"9780306406157\"}],\"imageLinks\":{\"thumbnail\":\"https://books.google.com/wrong\"}}},{\"volumeInfo\":{\"title\":\"Book\",\"industryIdentifiers\":[{\"type\":\"ISBN_10\",\"identifier\":\"500117855X\"}],\"imageLinks\":{\"thumbnail\":\"http://books.google.com/right\"}}}]}");
            }
            if(url.equals("https://books.google.com/right"))return bytes("image");
            if(url.equals("https://books.google.com/wrong"))throw new AssertionError("Wrong edition requested");
            throw new IOException("Other source unavailable");
        },b->b,"test-key");
        assertArrayEquals(bytes("image"),lookup.findCover("9785001178552","50 акварельных этюдов"));assertEquals(1,queries.size());assertTrue(queries.get(0).contains("q=%22"));
    }
}
