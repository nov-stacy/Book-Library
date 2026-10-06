package ru.homebooks.library;

import org.junit.*;import static org.junit.Assert.*;
import org.json.*;import org.jsoup.Jsoup;
import java.nio.file.*;import java.util.*;

public class AuthorNamesTest {
 @Test public void readsAllAuthorsDeduplicatesAndKeepsNamePunctuation()throws Exception{
  assertEquals("Doe, Jane, John Smith",AuthorNames.json(new JSONArray("[\" Doe, Jane \",{\"name\":\"John Smith\"},{\"name\":\"John  Smith\"},{\"name\":\"Editor\",\"roleName\":\"editor\"}]")));
  assertEquals("Первый, Второй",AuthorNames.elements(Jsoup.parse("<meta property='books:author' content='Первый'><meta property='books:author' content='Второй'><meta property='books:author' content='Первый'>"),"meta[property=books:author]"));
 }
 @Test public void registryKeepsOnlyMainAuthorRoles(){
  String html="<h1>A World History of Art</h1><ul class='contributors__list'><li><span class='contributors__role'>Authors</span><ul><li class='author-item'><span class='contributors__name'>Hugh Honour</span></li><li class='author-item'><span class='contributors__name'>John Fleming</span></li></ul></li><li><span class='contributors__role'>Editor</span><ul><li class='author-item'><span class='contributors__name'>Wrong Editor</span></li></ul></li></ul>";
  assertEquals("Hugh Honour, John Fleming",PublisherCatalog.parse(BookSources.named("Laurence King"),Jsoup.parse(html,"https://www.laurenceking.com/")).book.author);
  String alpina="<h1>Title</h1><div class='b-book-primary__authors'><div itemprop='author'><span itemprop='name'>Author</span></div></div><footer itemprop='author'>Publisher</footer>";
  assertEquals("Author",PublisherCatalog.parse(BookSources.named("Альпина"),Jsoup.parse(alpina,"https://alpinabook.ru/")).book.author);
 }
 @Test public void threeTotalUsesExplicitCreditNeverVendorOrContributors()throws Exception{
  JSONObject product=new JSONObject().put("vendor","3dtotal").put("description","<p>Product details<br><strong>Author</strong>: Jane Doe<br>Pages: 100</p><p>Illustrated by Other Artist</p>");
  assertEquals("Jane Doe",PublisherPages.threeTotalAuthor(product));
  assertEquals("",PublisherPages.threeTotalAuthor(new JSONObject().put("vendor","Publisher").put("description","Art by Artist. Introduction by Someone.")));
  assertEquals("",PublisherPages.threeTotalAuthorPage("<main><h1>Book</h1><div class='product__description'>Art by Artist.</div><aside class='review'><span itemprop='author'>Reviewer</span></aside></main>","Book"));
 }
 @Test public void structuredAuthorArraysDoNotIncludeReviewers()throws Exception{
  String html="<h1>Book</h1><script type='application/ld+json'>{\"@type\":[\"Book\",\"Product\"],\"name\":\"Book\",\"author\":[{\"name\":\"First\"},{\"name\":\"Second\"}],\"review\":{\"author\":{\"name\":\"Reviewer\"}}}</script>";
  assertEquals("First, Second",WebBookParser.byTitle(html,"Book","","test").book.author);
 }
 @Test public void primaryMicrodataExcludesReviewAuthors(){
  String html="<div itemscope itemtype='https://schema.org/Book'><h1 itemprop='name'>Book</h1><span itemprop='author'>Author</span><div itemprop='review'><span itemprop='author'>Reviewer</span></div></div>";
  assertEquals("Author",WebBookParser.byTitle(html,"Book","","test").book.author);
 }
 @Test public void laterMatchingSourceFillsOnlyMissingAuthor()throws Exception{
  java.util.concurrent.CountDownLatch first=new java.util.concurrent.CountDownLatch(1);
  BookLookup lookup=new BookLookup((url,limit)->{
   if(url.contains("openlibrary.org/isbn"))return "{\"title\":\"Original title\",\"covers\":[123]}".getBytes(java.nio.charset.StandardCharsets.UTF_8);
   if(url.contains("/b/id/123"))return new byte[]{1};
   if(url.contains("eksmo.ru/search")){try{assertTrue(first.await(3,java.util.concurrent.TimeUnit.SECONDS));}catch(InterruptedException e){throw new java.io.IOException(e);}return "<a class='book__content' href='/book/test/'>Book</a>".getBytes(java.nio.charset.StandardCharsets.UTF_8);}
   if(url.endsWith("/book/test/"))return "<h1 class='book-page__card-title'>Another spelling</h1><div class='book-page__copy-isbn'><span class='copy__val'>9780306406157</span></div><a class='book-page__card-author-link'>Correct Author</a><meta property='og:image' content='https://cdn.eksmo.ru/cover.jpg'>".getBytes(java.nio.charset.StandardCharsets.UTF_8);
   if(url.equals("https://cdn.eksmo.ru/cover.jpg"))return new byte[]{2};throw new java.io.IOException("No fixture");
  },bytes->{if(bytes[0]==1){first.countDown();return null;}return bytes;});
  BookLookup.Result result=lookup.find("9780306406157");assertEquals("Original title",result.title);assertEquals("Correct Author",result.author);assertNotNull(result.cover);
 }
 @Test public void auditCapturedPages()throws Exception{
  String dir=System.getenv("HOMEBOOKS_PUBLISHER_AUDIT");Assume.assumeTrue(dir!=null);
  String[][] expected={{"content","Кодзима Кон"},{"alpina","Патрик Кинг"},{"piter","Маликова С. И., Феофанова О. В."},{"bhv","Стародумов Василий Пантелеймонович"},{"admarginem","Адольф Лоос"},{"art","Ирина Данилова"},{"hobbyteka","Курто Роза Мария"},{"taschen","Gilles Néret"},{"phaidon","Phaidon Editors"},{"thames","Martin Gayford"},{"king","Hugh Honour, John Fleming"},{"searchpress","Giovanni Civardi"}};
  for(String[] row:expected){BookSources.Source source=null;for(BookSources.Source s:BookSources.all())if(s.id.equals(row[0]))source=s;assertNotNull(source);
   String html=Files.readString(Paths.get(dir,row[0]+"-book-search.html"));
   assertEquals(source.name,row[1],PublisherCatalog.parse(source,Jsoup.parse(html,source.get("base"))).book.author);
  }

 }
}
