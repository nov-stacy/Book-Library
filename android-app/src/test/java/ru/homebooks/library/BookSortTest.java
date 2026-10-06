package ru.homebooks.library;

import org.junit.Test;
import java.util.*;
import static org.junit.Assert.*;

public class BookSortTest {
    private List<Book> books() {
        return new ArrayList<>(Arrays.asList(
            new Book("3", "Яблоко", "Борис", 30),
            new Book("1", "Азбука", "", 10),
            new Book("2", "берег", "Анна", 20)));
    }
    private List<String> order(BookSort sort) {
        List<Book> books=books();books.sort(sort.comparator());List<String> result=new ArrayList<>();
        for(Book book:books)result.add(book.isbn);return result;
    }
    @Test public void bothDateDirections(){
        assertEquals(Arrays.asList("3","2","1"),order(BookSort.NEWEST));
        assertEquals(Arrays.asList("1","2","3"),order(BookSort.OLDEST));
    }
    @Test public void russianAlphabetIgnoresCase(){
        assertEquals(Arrays.asList("1","2","3"),order(BookSort.TITLE_ASC));
        assertEquals(Arrays.asList("3","2","1"),order(BookSort.TITLE_DESC));
    }
    @Test public void missingAuthorsStayLastInBothDirections(){
        assertEquals(Arrays.asList("2","3","1"),order(BookSort.AUTHOR_ASC));
        assertEquals(Arrays.asList("3","2","1"),order(BookSort.AUTHOR_DESC));
    }
    @Test public void tiesAreStableAndInvalidPreferenceFallsBack(){
        Book a=new Book("1","Одинаково","Автор",10), b=new Book("2","Одинаково","Автор",10);
        for(BookSort sort:BookSort.values())assertTrue(sort.comparator().compare(a,b)<0);
        assertEquals(BookSort.NEWEST,BookSort.restore("obsolete"));
        assertEquals(BookSort.NEWEST,BookSort.restore(null));
        assertEquals(BookSort.AUTHOR_DESC,BookSort.restore("AUTHOR_DESC"));
    }
    @Test public void removedStatusPreferenceFallsBack(){assertEquals(BookSort.NEWEST,BookSort.restore("STATUS"));}
}
