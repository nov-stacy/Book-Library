package ru.homebooks.library;
import org.junit.*;
import org.junit.rules.TemporaryFolder;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.*;
import java.util.concurrent.atomic.*;
import static org.junit.Assert.*;
public class GoogleRequestCacheTest {
 @Rule public TemporaryFolder folder=new TemporaryFolder();
 private final String url="https://www.googleapis.com/books/v1/volumes?q=Book&key=test-key";
 private byte[] json(String text){return text.getBytes(StandardCharsets.UTF_8);}
 @Test public void positiveAndEmptyResponsesSurviveRestartAndExpire()throws Exception{
  AtomicLong now=new AtomicLong(1000);AtomicInteger calls=new AtomicInteger();File dir=folder.newFolder();
  BookLookup.Transport source=(u,l)->{calls.incrementAndGet();return json("{\"items\":[{\"id\":\"book\"}]}");};
  new GoogleRequestCache(dir,source,now::get).get(url,1024);
  new GoogleRequestCache(dir,source,now::get).get(url,1024);assertEquals(1,calls.get());
  now.addAndGet(7L*24*3600000+1);new GoogleRequestCache(dir,source,now::get).get(url,1024);assertEquals(2,calls.get());
  File empty=folder.newFolder();BookLookup.Transport missing=(u,l)->{calls.incrementAndGet();return json("{\"totalItems\":0}");};
  GoogleRequestCache cache=new GoogleRequestCache(empty,missing,now::get);cache.get(url,1024);cache.get(url,1024);assertEquals(3,calls.get());
  now.addAndGet(6L*3600000+1);cache.get(url,1024);assertEquals(4,calls.get());
 }
 @Test public void dailyQuotaStopsOtherQueriesAfterRestart()throws Exception{
  AtomicLong now=new AtomicLong(1000);AtomicInteger calls=new AtomicInteger();File dir=folder.newFolder();
  BookLookup.Transport source=(u,l)->{calls.incrementAndGet();throw new BookLookup.HttpFailure(429,true);};
  try{new GoogleRequestCache(dir,source,now::get).get(url,1024);fail();}catch(BookLookup.HttpFailure expected){}
  try{new GoogleRequestCache(dir,source,now::get).get(url.replace("Book","Another"),1024);fail();}catch(BookLookup.HttpFailure expected){}
  assertEquals(1,calls.get());now.addAndGet(24L*3600000+1);
  try{new GoogleRequestCache(dir,source,now::get).get(url,1024);fail();}catch(BookLookup.HttpFailure expected){}assertEquals(2,calls.get());
 }
 @Test public void networkErrorsAreNotCachedAndOtherSourcesContinue()throws Exception{
  AtomicInteger calls=new AtomicInteger();GoogleRequestCache cache=new GoogleRequestCache(folder.newFolder(),(u,l)->{calls.incrementAndGet();throw new IOException();});
  for(int i=0;i<2;i++)try{cache.get(url,1024);}catch(IOException expected){}assertEquals(2,calls.get());
  try{cache.get("https://example.com/cover.jpg",1024);}catch(IOException expected){}assertEquals(3,calls.get());
 }
 @Test public void concurrentIdenticalQueriesUseOneRequest()throws Exception{
  AtomicInteger calls=new AtomicInteger();GoogleRequestCache cache=new GoogleRequestCache(folder.newFolder(),(u,l)->{calls.incrementAndGet();return json("{\"items\":[]}");});
  ExecutorService pool=Executors.newFixedThreadPool(2);
  try{Future<byte[]> one=pool.submit(()->cache.get(url,1024)),two=pool.submit(()->cache.get(url,1024));one.get();two.get();assertEquals(1,calls.get());}finally{pool.shutdownNow();}
 }
}
