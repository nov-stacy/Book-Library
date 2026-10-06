package ru.homebooks.library;

import org.junit.*;
import java.io.*;
import java.lang.reflect.*;
import static org.junit.Assert.*;

/** Opt-in regression check using the real network; never writes library data. */
public class TitleSearchLiveTest {
    @Test public void findsIncomprehensibleArtWithCover()throws Exception{
        Assume.assumeTrue("1".equals(System.getenv("HOMEBOOKS_LIVE_TITLE")));
        BookLookup lookup=new BookLookup((url,limit)->{
            try{
                Method m=BookLookup.class.getDeclaredMethod("download",String.class,int.class);m.setAccessible(true);return (byte[])m.invoke(null,url,limit);
            }catch(InvocationTargetException e){throw new IOException("Request failed",e.getCause());}
            catch(ReflectiveOperationException e){throw new IOException(e);}
        },bytes->{
            try{Object image=Class.forName("javax.imageio.ImageIO").getMethod("read",InputStream.class).invoke(null,new ByteArrayInputStream(bytes));if(image==null)throw new IOException("Invalid image");return bytes;}
            catch(ReflectiveOperationException e){throw new IOException(e);}
        },BuildConfig.GOOGLE_BOOKS_API_KEY);
        TitleBookSearch.Result result=lookup.findByTitle("Непонятное искусство","","");
        assertFalse("No candidates",result.candidates.isEmpty());
        TitleBookSearch.Candidate found=null;
        for(TitleBookSearch.Candidate c:result.candidates){
            assertTrue(c.book.title.toLowerCase().contains("непонятное"));
            if(c.book.author.contains("Гомперц"))found=c;
        }
        assertNotNull("Gompertz missing",found);assertNotNull("Cover must decode",lookup.previewTitle(found).cover);
        System.out.println("Confirmed: "+found.book.title+" / "+found.book.author+" / "+found.book.source);
    }
}
