package ru.homebooks.library;

import org.junit.*;
import static org.junit.Assert.*;
import org.json.*;
import org.jsoup.Jsoup;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;

public class PublisherCatalogTest {
    private BookSources.Source source()throws Exception{return new BookSources.Source(new JSONObject("{\"id\":\"test\",\"name\":\"Test\",\"adapter\":\"html\",\"base\":\"https://publisher.example\",\"titleSearch\":\"/search?q={query}\",\"isbnSearch\":\"/search?q={query}\",\"links\":\"main a\",\"path\":\"/book/\",\"isbn\":\".edition-isbn\"}"));}
    private String book(String title,String codes){return "<h1>"+title+"</h1>"+codes+"<meta property='og:image' content='http://publisher.example/cover.jpg'>";}
    @Test public void editionMustMatchOwnScopedIsbn()throws Exception{
        String html=book("Моя книга","<span class='edition-isbn'>9780306406157</span><aside>9785042296574</aside>");
        PublisherCatalog catalog=new PublisherCatalog((u,l)->(u.contains("/search?")?"<main><a href='/book/test'>Моя книга</a></main>":html).getBytes(StandardCharsets.UTF_8));
        assertTrue(catalog.find(source(),"","9785042296574").isEmpty());
        assertEquals("9780306406157",catalog.find(source(),"","9780306406157").get(0).isbn);
        assertEquals("https://publisher.example/cover.jpg",catalog.find(source(),"Моя книга","").get(0).book.cover);
    }
    @Test public void multipleEditionsHaveNoClaimedIsbn()throws Exception{
        TitleBookSearch.Candidate c=PublisherCatalog.parse(source(),Jsoup.parse(book("Моя книга","<p class='edition-isbn'>9780306406157</p><p class='edition-isbn'>9785042296574</p>"),"https://publisher.example/book/test"));
        assertEquals("",c.isbn);
    }
    @Test public void followsOnlyRelevantSameHostProductLinks()throws Exception{
        List<String> requests=new ArrayList<>();PublisherCatalog catalog=new PublisherCatalog((u,l)->{requests.add(u);return (u.contains("search?")?"<main><a href='https://evil.example/book/a'>Моя книга</a><a href='/news/a'>Моя книга</a><a href='/book/other'>Другая книга</a><a href='/book/test'>Моя книга</a><a href='/book/test?tracking=yes'>Моя книга</a></main>":book("Моя книга","")).getBytes(StandardCharsets.UTF_8);});
        assertEquals(1,catalog.find(source(),"Моя книга","").size());assertEquals(2,requests.size());
    }
    @Test public void stopsFetchingPagesAfterAcceptedCover()throws Exception{
        List<String> urls=new ArrayList<>();
        PublisherCatalog catalog=new PublisherCatalog((u,l)->{urls.add(u);return (u.contains("search?")?"<main><a href='/book/one'>Моя книга</a><a href='/book/two'>Моя книга</a></main>":book("Моя книга","<span class='edition-isbn'>9780306406157</span>")).getBytes(StandardCharsets.UTF_8);});
        assertEquals(1,catalog.find(source(),"Моя книга","9780306406157",c->true).size());
        assertEquals(2,urls.size());assertFalse(urls.get(1).endsWith("two"));
    }
    @Test public void registryRoutesAndFormatsWithoutDuplicateImprints(){
        assertEquals("Контэнт",BookSources.isbnOrder("9785001417163",false).get(0));
        assertEquals("978-5-00141-716-3",BookSources.named("Контэнт").formatIsbn("9785001417163"));
        assertFalse(BookSources.named("Азбука-Аттикус").enabled());
        assertEquals(1,Collections.frequency(BookSources.isbnOrder("9785171557447",true),"АСТ"));
        assertFalse(BookSources.isbnOrder("9785171557447",true).contains("ОГИЗ"));
        for(BookSources.Source s:BookSources.all())for(String field:new String[]{"links","title","isbn","author","cover"})if(!s.get(field).isEmpty())Jsoup.parse("").select(s.get(field));
    }
    /** Offline audit against pages captured during source onboarding; never calls Google. */
    @Test public void capturedPublisherPages()throws Exception{
        String dir=System.getenv("HOMEBOOKS_PUBLISHER_AUDIT");Assume.assumeTrue(dir!=null);
        for(BookSources.Source s:BookSources.all()){
            Path path=Paths.get(dir,s.id+"-book-search.html");if(!s.enabled()||!Files.exists(path))continue;
            TitleBookSearch.Candidate c=PublisherCatalog.parse(s,Jsoup.parse(Files.readString(path),s.get("base")));
            assertNotNull(s.name,c);assertFalse(s.name+" cover",c.book.cover.isEmpty());
            System.out.println(s.name+" | "+c.book.title+" | "+c.book.author+" | "+c.isbn+" | "+c.book.cover);
        }
    }
}
