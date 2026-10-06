package ru.homebooks.library;
import org.junit.Test;
import java.io.*;
import java.nio.charset.StandardCharsets;
import static org.junit.Assert.*;
public class WebDiscoveryTest {
 @Test public void primaryBookWithSubtitleAndMissingIsbnCanBeConfirmed(){
  String html="<h1>Непонятное искусство. От Моне до Бэнкси</h1><script type='application/ld+json'>{\"@type\":\"Book\",\"name\":\"Непонятное искусство\",\"author\":{\"name\":\"Уилл Гомперц\"},\"image\":\"https://example.com/cover.jpg\"}</script>";
  TitleBookSearch.Candidate c=WebBookParser.byTitle(html,"Непонятное искусство","Гомперц","example.com");assertNotNull(c);assertEquals("",c.isbn);assertEquals("Уилл Гомперц",c.book.author);
 }
 @Test public void recommendationsAndArticlesDoNotBecomeBooks(){
  String json="<script type='application/ld+json'>{\"@type\":\"ItemList\",\"itemListElement\":[{\"@type\":\"Book\",\"name\":\"Непонятное искусство\"}]}</script>";
  assertNull(WebBookParser.byTitle("<h1>Другие книги</h1>"+json,"Непонятное искусство","","example.com"));
  assertNull(WebBookParser.byTitle("<h1>Непонятное искусство</h1>"+json,"Непонятное искусство","","example.com"));
  assertNull(WebBookParser.byTitle("<h1>Непонятное искусство</h1><meta property='og:type' content='article'>","Непонятное искусство","","example.com"));
 }
 @Test public void irrelevantFeedAndCaptchaAreUnavailable()throws Exception{
  try{WebDiscovery.bingLinks("<rss><channel><item><title>Unrelated</title><link>https://example.com/</link></item></channel></rss>","Непонятное искусство",null);fail();}catch(IOException expected){}
  try{WebDiscovery.duckLinks("<div class='anomaly-modal'>Captcha</div>","Book",null);fail();}catch(IOException expected){}
 }
 @Test public void feedTitleAloneIsNotEnoughToAcceptPage(){
  TitleBookSearch.Result r=new WebDiscovery((url,limit)->{
   if(url.contains("bing.com"))return "<rss><channel><item><title>Непонятное искусство</title><link>https://example.com/book</link></item></channel></rss>".getBytes(StandardCharsets.UTF_8);
   if(url.contains("example.com"))return "<h1>Посторонняя книга</h1>".getBytes(StandardCharsets.UTF_8);
   throw new IOException();
  }).find("Непонятное искусство","","");assertTrue(r.candidates.isEmpty());
 }
}
