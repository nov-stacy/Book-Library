package ru.homebooks.library;

import org.junit.*;
import org.json.*;
import java.io.*;
import java.lang.reflect.*;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.*;

/** Opt-in integration check against a supplied sample, never writes library data. */
public class BookLookupLiveTest {
    @Test public void selectedBooks()throws Exception{
        String path=System.getenv("HOMEBOOKS_LIVE_SAMPLE");Assume.assumeTrue(path!=null);
        JSONArray books=new JSONArray(Files.readString(Path.of(path)));
        ExecutorService pool=Executors.newFixedThreadPool(2,r->{Thread t=new Thread(r);t.setDaemon(true);return t;});
        List<Future<JSONObject>> futures=new ArrayList<>();
        for(int i=0;i<books.length();i++){
            JSONObject book=books.getJSONObject(i);
            futures.add(pool.submit(()->{
                JSONObject report=new JSONObject().put("isbn",book.getString("isbn")).put("originalTitle",book.getString("title"));
                BookLookup lookup=new BookLookup((url,limit)->{
                    try{
                        Method m=BookLookup.class.getDeclaredMethod("download",String.class,int.class);m.setAccessible(true);
                        return (byte[])m.invoke(null,url,limit);
                    }catch(InvocationTargetException e){throw e.getCause() instanceof IOException?(IOException)e.getCause():new IOException("Network failed");}
                    catch(ReflectiveOperationException e){throw new IOException("Probe failed");}
                },bytes->{try{Object image=Class.forName("javax.imageio.ImageIO").getMethod("read",InputStream.class).invoke(null,new ByteArrayInputStream(bytes));if(image==null)throw new IOException("Not an image");
                    return bytes;}catch(Exception e){throw new IOException("Image decode failed");}},BuildConfig.GOOGLE_BOOKS_API_KEY);
                long started=System.nanoTime();
                BookLookup.Result result=lookup.find(book.getString("isbn"),book.getString("title"));
                report.put("elapsedMs",(System.nanoTime()-started)/1_000_000);
                if(result.cover!=null){
                    Object image=Class.forName("javax.imageio.ImageIO").getMethod("read",InputStream.class).invoke(null,new ByteArrayInputStream(result.cover));
                    report.put("width",image.getClass().getMethod("getWidth").invoke(image)).put("height",image.getClass().getMethod("getHeight").invoke(image));
                }
                report.put("found",result.cover!=null).put("notice",result.notice).put("bytes",result.cover==null?0:result.cover.length);
                return report;
            }));
        }
        JSONArray reports=new JSONArray();
        try{for(int i=0;i<futures.size();i++){
            JSONObject result;
            try{result=futures.get(i).get(60,TimeUnit.SECONDS);}catch(Exception e){futures.get(i).cancel(true);result=new JSONObject().put("isbn",books.getJSONObject(i).getString("isbn")).put("error",e.getClass().getSimpleName());}
            reports.put(result);System.out.println(result);
        }}finally{pool.shutdownNow();}
        Files.writeString(Path.of(path).resolveSibling("publisher-live-results.json"),reports.toString(2));
    }
}
