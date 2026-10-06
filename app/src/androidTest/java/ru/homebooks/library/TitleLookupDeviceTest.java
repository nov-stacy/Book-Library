package ru.homebooks.library;
import androidx.test.platform.app.InstrumentationRegistry;
import org.junit.Test;
import java.io.*;
import java.lang.reflect.*;
import java.net.URI;
import static org.junit.Assert.*;
public class TitleLookupDeviceTest {
 @Test public void realTitleSearch()throws Exception{
  org.junit.Assume.assumeTrue("true".equals(InstrumentationRegistry.getArguments().getString("liveTitle")));
  assertEquals("ru.homebooks.library.testbed",InstrumentationRegistry.getInstrumentation().getTargetContext().getPackageName());
  BookLookup lookup=new BookLookup((url,limit)->{
   long started=System.currentTimeMillis();String host=URI.create(url).getHost();
   try{Method m=BookLookup.class.getDeclaredMethod("download",String.class,int.class);m.setAccessible(true);byte[] b=(byte[])m.invoke(null,url,limit);android.util.Log.i("TitleProbe",host+" OK "+(System.currentTimeMillis()-started)+"ms");return b;}
   catch(Exception e){Throwable cause=e instanceof InvocationTargetException?e.getCause():e;android.util.Log.i("TitleProbe",host+" "+cause.getClass().getSimpleName()+" status="+(cause instanceof BookLookup.HttpFailure?((BookLookup.HttpFailure)cause).status:0)+" "+(System.currentTimeMillis()-started)+"ms");throw cause instanceof IOException?(IOException)cause:new IOException("Probe failed");}
  },bytes->{android.graphics.Bitmap b=android.graphics.BitmapFactory.decodeByteArray(bytes,0,bytes.length);if(b==null)throw new IOException("Invalid image");b.recycle();return bytes;},BuildConfig.GOOGLE_BOOKS_API_KEY);
  android.util.Log.i("TitleProbe","Key configured: "+!BuildConfig.GOOGLE_BOOKS_API_KEY.isEmpty());
  TitleBookSearch.Result result=lookup.findByTitle("Непонятное искусство","","9785906837424");
  android.util.Log.i("TitleProbe","Candidates="+result.candidates.size()+" incomplete="+result.incomplete);
  assertFalse("No candidates",result.candidates.isEmpty());
  TitleBookSearch.Candidate first=result.candidates.get(0);android.util.Log.i("TitleProbe",first.book.title+" / "+first.book.author);
  assertNotNull(lookup.previewTitle(first).cover);
 }
}
