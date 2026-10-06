package ru.homebooks.library;

import org.junit.Test;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.net.SocketTimeoutException;
import static org.junit.Assert.*;

public class BookLookupTest {
    private static final String ISBN="9785042296574";
    private static final String PAGE="<h1 class='book-page__card-title'>Перспектива без ошибок</h1><a class='book-page__card-author-link'>Ким Дон Хо</a><span class='book-page__copy-isbn'><span class='copy__val'>978-5-04-229657-4</span></span><meta property='og:image' content='https://cdn.eksmo.ru/cover.jpg'>";
    private static byte[] bytes(String s){return s.getBytes(StandardCharsets.UTF_8);}
    @Test public void publisherFindsRussianIsbnAndKeepsDataWhenCoverFails(){
        BookLookup service=new BookLookup((url,limit)->{
            if(url.contains("/search/?q="))return bytes("<a class='book__content' href='/book/perspektiva/'>Книга</a>");
            if(url.endsWith("/book/perspektiva/"))return bytes(PAGE);
            throw new IOException("cover unavailable");
        },b->b);
        BookLookup.Result result=service.find("978-5-04-229657-4");
        assertEquals("Перспектива без ошибок",result.title);assertEquals("Ким Дон Хо",result.author);assertNull(result.cover);assertTrue(result.notice.contains("Обложка"));
    }
    @Test public void coverSearchContinuesWhenFirstCatalogHasNoImage(){
        BookLookup service=new BookLookup((url,limit)->{
            if(url.contains("/search/?q="))return bytes("<a class='book__content' href='/book/perspektiva/'>Книга</a>");
            if(url.endsWith("/book/perspektiva/"))return bytes(PAGE.replace("https://cdn.eksmo.ru/cover.jpg",""));
            if(url.contains("openlibrary.org/isbn/"))return bytes("{\"title\":\"Книга\",\"by_statement\":\"Автор\",\"covers\":[123]}");
            if(url.contains("/b/id/123-M.jpg"))return bytes("second cover");
            throw new IOException("Unexpected URL: "+url);
        },b->b);
        assertArrayEquals(bytes("second cover"),service.findCover(ISBN));
    }
    @Test public void coverSearchContinuesWhenFirstImageCannotBeDecoded(){
        BookLookup service=new BookLookup((url,limit)->{
            if(url.contains("/search/?q="))return bytes("<a class='book__content' href='/book/perspektiva/'>Книга</a>");
            if(url.endsWith("/book/perspektiva/"))return bytes(PAGE);
            if(url.contains("cdn.eksmo.ru"))return bytes("broken");
            if(url.contains("openlibrary.org/isbn/"))return bytes("{\"title\":\"Книга\",\"by_statement\":\"Автор\",\"covers\":[123]}");
            if(url.contains("/b/id/123-M.jpg"))return bytes("valid");
            throw new IOException("Unexpected URL");
        },b->{if(new String(b,StandardCharsets.UTF_8).equals("broken"))throw new IOException("Not an image");return b;});
        assertArrayEquals(bytes("valid"),service.findCover(ISBN));
    }
    private static boolean isConfiguredPublisher(String url){for(BookSources.Source source:BookSources.all())if(source.adapter.equals("html")&&!source.get("base").isEmpty()&&url.startsWith(source.get("base")+"/"))return true;return false;}
    private static String webPage(String isbn, String cover) {
        return "<h1>Book title</h1><meta property='book:isbn' content='"+isbn+"'><meta name='author' content='Author'><meta property='og:image' content='"+cover+"'>";
    }
    @Test public void removedSourcesAreNeverRequested(){
        BookLookup service=new BookLookup((url,limit)->{
            assertFalse(url.contains("googleapis"));assertFalse(url.contains("livelib"));
            throw new IOException("Unavailable");
        },b->b);
        assertNull(service.findCover(ISBN));
    }
    @Test public void allSourcesAreTriedWhenNoCoverExists(){
        java.util.List<String> urls=java.util.Collections.synchronizedList(new java.util.ArrayList<>());
        BookLookup service=new BookLookup((url,limit)->{
            urls.add(url);
            if(url.contains("eksmo")||url.contains("ast.ru")||url.contains("3dtotal"))return bytes("<title>Поиск</title>");
            if(url.contains("mann-ivanov-ferber"))return bytes("{\"products\":[]}");
            if(url.contains("openlibrary"))return bytes("{\"title\":\"Book\"}");
            if(url.contains("bing"))return bytes("<rss><channel></channel></rss>");
            if(isConfiguredPublisher(url))return bytes("<title>Поиск</title>");
            throw new AssertionError(url);
        },b->b);
        BookLookup.Result result=service.find(ISBN);
        assertEquals("Book",result.title);assertNull(result.cover);
        for(String host:new String[]{"eksmo","ast.ru","mann-ivanov-ferber","3dtotal","openlibrary","bing"})assertTrue(host,urls.stream().anyMatch(url->url.contains(host)));
    }
    @Test public void webSearchContinuesAcrossPagesAndStopsAtFirstDecodedImage(){
        BookLookup service=new BookLookup((url,limit)->{
            if(url.contains("livelib")||url.contains("eksmo")||url.contains("openlibrary")||url.contains("ast.ru")||url.contains("mann-ivanov-ferber")||url.contains("3dtotal"))throw new IOException("Unavailable");
            if(isConfiguredPublisher(url))throw new IOException("Unavailable");
            if(url.contains("bing"))return bytes("<rss><channel><item><title>"+ISBN+"</title><link>https://93.184.216.34/first</link></item><item><title>"+ISBN+"</title><link>https://93.184.216.34/second</link></item><item><title>"+ISBN+"</title><link>https://93.184.216.34/third</link></item></channel></rss>");
            if(url.endsWith("/first"))return bytes(webPage(ISBN,"https://93.184.216.34/invalid.jpg"));
            if(url.endsWith("/second"))return bytes(webPage(ISBN,"https://93.184.216.34/valid.jpg"));
            if(url.endsWith("/invalid.jpg"))return bytes("invalid");
            if(url.endsWith("/valid.jpg"))return bytes("valid");
            throw new AssertionError("Search should already have stopped: "+url);
        },b->new String(b,StandardCharsets.UTF_8).equals("invalid")?null:b);
        assertArrayEquals(bytes("valid"),service.find(ISBN).cover);
    }
    @Test public void publisherRejectsDifferentEdition(){assertNull(BookLookup.parsePublisher(PAGE,"9780306406157"));}
    @Test public void editionApiReplacesBrokenBooksEndpoint(){
        BookLookup service=new BookLookup((url,limit)->{
            if(!url.equals("https://openlibrary.org/isbn/9780306406157.json"))throw new IOException("Unavailable");
            return bytes("{\"title\":\"Error-correction coding\",\"by_statement\":\"George Clark\"}");
        },b->b);
        assertEquals("Error-correction coding",service.find("9780306406157").title);
    }
    @Test public void partialCatalogFailureStillTriesOtherSource(){
        BookLookup service=new BookLookup((url,limit)->{
            if(url.contains("eksmo"))throw new SocketTimeoutException();
            return bytes("{\"title\":\"Книга\",\"by_statement\":\"Автор\"}");
        },b->b);
        assertEquals("Книга",service.find(ISBN).title);
    }
    @Test public void distinguishesNetworkErrorFromNotFound(){
        BookLookup.Result result=new BookLookup((url,limit)->{throw new SocketTimeoutException();},b->b).find(ISBN);
        assertEquals("",result.title);assertTrue(result.notice.contains("сервер не ответил вовремя"));assertFalse(result.notice.contains("ISBN не найден"));
    }
    @Test public void genericWebPageMatchesIsbnAndMultipleAuthors(){
        String html="<script type='application/ld+json'>{\"@graph\":[{\"@type\":\"Book\",\"isbn\":\"978-5-04-229657-4\",\"name\":\"Название\",\"author\":[{\"name\":\"Первый\"},{\"name\":\"Второй\"}],\"image\":{\"url\":\"https://example.com/cover.jpg\"}}]}</script>";
        BookLookup.Metadata result=WebBookParser.parse(html,ISBN,"example.com");
        assertNotNull(result);assertEquals("Первый, Второй",result.author);assertEquals("https://example.com/cover.jpg",result.cover);
        assertNull(WebBookParser.parse(html,"9780306406157","example.com"));
    }
    @Test public void genericWebPageRejectsIsbnMentionedOnlyInText(){
        assertNull(WebBookParser.parse("<h1>Рекомендации</h1><p>9785042296574</p>",ISBN,"example.com"));
    }
}
