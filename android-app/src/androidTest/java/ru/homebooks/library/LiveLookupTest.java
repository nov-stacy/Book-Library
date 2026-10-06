package ru.homebooks.library;

import androidx.test.ext.junit.runners.AndroidJUnit4;
import org.junit.Test;
import org.junit.runner.RunWith;
import static org.junit.Assert.*;

/** Explicit network smoke test, run on a connected device with internet. Does not save books. */
@RunWith(AndroidJUnit4.class)
public class LiveLookupTest {
    @Test public void findsProvidedRussianBook(){
        BookLookup.Result result=new BookLookup().find("978-5-04-229657-4");
        assertTrue(result.notice,result.title.startsWith("Перспектива без ошибок"));
        assertEquals("Ким Дон Хо",result.author);
        assertNotNull("Cover should be downloaded",result.cover);
    }
}
