package ru.homebooks.library;

import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.util.Log;
import androidx.test.platform.app.InstrumentationRegistry;
import org.junit.Test;
import org.junit.Assume;
import java.io.*;
import java.lang.reflect.*;
import java.util.*;
import java.util.concurrent.*;
import static org.junit.Assert.*;

/** Explicit opt-in, real Android network/decoder; never writes library data or calls Google. */
public class PublisherSourcesDeviceTest {
    @Test public void publishersOnPhone()throws Exception{
        Assume.assumeTrue("true".equals(InstrumentationRegistry.getArguments().getString("livePublishers")));
        assertEquals("ru.homebooks.library.testbed",InstrumentationRegistry.getInstrumentation().getTargetContext().getPackageName());
        android.app.Activity activity=InstrumentationRegistry.getInstrumentation().startActivitySync(
            new android.content.Intent(InstrumentationRegistry.getInstrumentation().getTargetContext(),MainActivity.class).addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK));
        InstrumentationRegistry.getInstrumentation().runOnMainSync(()->activity.getWindow().addFlags(android.view.WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON));
        String filter=InstrumentationRegistry.getArguments().getString("publisherFilter","");
        Method download=BookLookup.class.getDeclaredMethod("download",String.class,int.class);download.setAccessible(true);
        BookLookup.Transport transport=(url,limit)->{assertFalse("Google must not be requested",url.contains("googleapis"));try{return(byte[])download.invoke(null,url,limit);}catch(InvocationTargetException e){throw new IOException(e.getCause());}catch(Exception e){throw new IOException(e);}};
        String[][] cases={{"Питер","Раз, два, три, четыре, пять — я умею рисовать","9785496004749"},{"Контэнт","Рисуем мангу акварельными карандашами","9785001417163"},{"Альпина","Искусство самопознания",""},{"БХВ","Сказки озера Байкал","9785977535984"},{"Ad Marginem","Почему архитектура не искусство","9785908038645"},{"Арт-Волхонка","Картина как общая формула мировидения","9785907387430"},{"Хоббитека","Как рисовать мультяшек с характером","9785990940994"},{"TASCHEN","Klimt",""},{"Phaidon","The Art Book",""},{"Thames & Hudson","My Heart is This","9780500031018"},{"Laurence King","A World History of Art","9781856695848"},{"Search Press","Drawing Portraits","9781800924192"}};
        ScheduledExecutorService timer=Executors.newScheduledThreadPool(2);
        ExecutorService pool=Executors.newFixedThreadPool(2);List<Future<String>> jobs=new ArrayList<>();
        try{
            for(String[] sample:cases)if(filter.isEmpty()||filter.contains(sample[0]))jobs.add(pool.submit(()->{
                long start=System.currentTimeMillis();BookLookup.LookupControl control=new BookLookup.LookupControl();BookLookup.ACTIVE.set(control);
                ScheduledFuture<?> timeout=timer.schedule(control::cancel,20,TimeUnit.SECONDS);
                try{
                    List<TitleBookSearch.Candidate> found=new PublisherCatalog(transport).find(BookSources.named(sample[0]),sample[1],sample[2]);
                    if(found.isEmpty())throw new IOException("No title results");
                    TitleBookSearch.Candidate chosen=null;for(TitleBookSearch.Candidate c:found)if(sample[2].isEmpty()||sample[2].equals(c.isbn)){chosen=c;break;}
                    if(chosen==null)throw new IOException("No matching edition");
                    byte[] image=transport.get(chosen.book.cover,5_000_000);Bitmap bitmap=BitmapFactory.decodeByteArray(image,0,image.length);
                    if(bitmap==null)throw new IOException("Cover cannot be decoded");
                    String dimensions=bitmap.getWidth()+"x"+bitmap.getHeight();bitmap.recycle();
                    String result=sample[0]+" OK | "+chosen.book.title+" | ISBN="+chosen.isbn+" | "+dimensions+" | "+(System.currentTimeMillis()-start)+"ms";
                    Log.i("PublisherProbe",result);return "";
                }catch(Exception e){String result=sample[0]+" FAILED | "+e+" | "+(System.currentTimeMillis()-start)+"ms";Log.i("PublisherProbe",result);return result;}
                finally{timeout.cancel(false);control.cancel();BookLookup.ACTIVE.remove();}
            }));
            List<String> failures=new ArrayList<>();for(Future<String> job:jobs){String error=job.get(90,TimeUnit.SECONDS);if(!error.isEmpty())failures.add(error);}
            // Also exercise the complete ISBN source routing and early-stop path on Android.
            for(String isbn:new String[]{"9785496004749","9785001417163"}){
                BookLookup.Result result=new BookLookup(transport,bytes->{Bitmap b=BitmapFactory.decodeByteArray(bytes,0,bytes.length);if(b==null)throw new IOException("Bad image");b.recycle();return bytes;},"").find(isbn);
                if(result.cover==null||result.title.isEmpty())failures.add("ISBN pipeline failed: "+isbn+" "+result.notice);
                else Log.i("PublisherProbe","ISBN pipeline OK | "+isbn+" | "+result.title);
            }
            assertTrue(failures.toString(),failures.isEmpty());
        }finally{for(Future<?> job:jobs)job.cancel(true);pool.shutdownNow();timer.shutdownNow();InstrumentationRegistry.getInstrumentation().runOnMainSync(activity::finish);}
    }
}
