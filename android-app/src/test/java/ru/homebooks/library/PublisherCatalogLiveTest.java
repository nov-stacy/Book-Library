package ru.homebooks.library;
import org.junit.*;import static org.junit.Assert.*;
import java.lang.reflect.*;import java.io.*;import java.util.*;import java.util.concurrent.*;
public class PublisherCatalogLiveTest {
 @Test public void actualPublisherTitleSearches()throws Exception{
  Assume.assumeTrue("1".equals(System.getenv("HOMEBOOKS_LIVE_PUBLISHERS")));
  Method download=BookLookup.class.getDeclaredMethod("download",String.class,int.class);download.setAccessible(true);
  PublisherCatalog catalog=new PublisherCatalog((url,limit)->{try{byte[] data=(byte[])download.invoke(null,url,limit);String capture=System.getenv("HOMEBOOKS_CAPTURE_PUBLISHERS");if(capture!=null&&url.contains("/products/"))java.nio.file.Files.write(java.nio.file.Paths.get(capture,new java.net.URL(url).getHost()+"-product-java.html"),data);return data;}catch(InvocationTargetException e){throw new IOException(e.getCause());}catch(Exception e){throw new IOException(e);}});
  String[][] queries={{"Контэнт","Рисуем мангу акварельными карандашами"},{"Альпина","Искусство самопознания"},{"Питер","Раз, два, три, четыре, пять — я умею рисовать"},{"БХВ","Сказки озера Байкал"},{"Ad Marginem","Почему архитектура не искусство"},{"Арт-Волхонка","Картина как общая формула мировидения"},{"Хоббитека","Как рисовать мультяшек с характером"},{"TASCHEN","Klimt"},{"Phaidon","The Art Book"},{"Thames & Hudson","My Heart is This"},{"Laurence King","A World History of Art"},{"Search Press","Drawing Portraits"}};
  ExecutorService pool=Executors.newFixedThreadPool(2);List<Future<String>> jobs=new ArrayList<>();
  try{for(String[] q:queries)if(System.getenv("HOMEBOOKS_PUBLISHER_FILTER")==null||System.getenv("HOMEBOOKS_PUBLISHER_FILTER").contains(q[0]))jobs.add(pool.submit(()->{try{List<TitleBookSearch.Candidate> found=catalog.find(BookSources.named(q[0]),q[1],"");System.out.println(q[0]+" results="+found.size());return found.isEmpty()?q[0]+" empty":"";}catch(Exception e){return q[0]+" "+e;}}));List<String> failures=new ArrayList<>();for(Future<String> job:jobs){String f=job.get(60,TimeUnit.SECONDS);if(!f.isEmpty())failures.add(f);}assertTrue(failures.toString(),failures.isEmpty());}finally{pool.shutdownNow();}
 }
 @Test public void piterAndContentIsbnAndCover()throws Exception{
  Assume.assumeTrue("1".equals(System.getenv("HOMEBOOKS_LIVE_PUBLISHER_ISBN")));
  Method download=BookLookup.class.getDeclaredMethod("download",String.class,int.class);download.setAccessible(true);
  BookLookup.Transport transport=(url,limit)->{try{return(byte[])download.invoke(null,url,limit);}catch(InvocationTargetException e){throw new IOException(e.getCause());}catch(Exception e){throw new IOException(e);}};
  for(String[] sample:new String[][]{{"Питер","9785496004749"},{"Контэнт","9785001417163"}}){
   List<TitleBookSearch.Candidate> books=new PublisherCatalog(transport).find(BookSources.named(sample[0]),"",sample[1]);
   assertFalse(sample[0],books.isEmpty());assertEquals(sample[1],books.get(0).isbn);
   byte[] image=transport.get(books.get(0).book.cover,5_000_000);
   assertNotNull(sample[0]+" cover must decode",Class.forName("javax.imageio.ImageIO").getMethod("read",InputStream.class).invoke(null,new ByteArrayInputStream(image)));
  }
 }

}
