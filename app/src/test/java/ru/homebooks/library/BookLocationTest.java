package ru.homebooks.library;
import org.junit.Test;
import static org.junit.Assert.*;
public class BookLocationTest {
    @Test public void legacyAndMissingLocationsMeanHome(){
        assertEquals(BookLocation.HOME,BookLocation.fromId(0));
        assertEquals(BookLocation.HOME,new Book("9780306406157","Книга","",0).location);
        assertEquals(BookLocation.HOME,new Book("9780306406157","Книга","",0,ReadingStatus.NONE,BookLocation.UNKNOWN).location);
        assertEquals(BookLocation.PARENTS,BookLocation.fromId(2));
    }
}
