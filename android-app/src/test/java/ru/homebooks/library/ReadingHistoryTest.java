package ru.homebooks.library;

import org.junit.Test;
import java.util.*;
import static org.junit.Assert.*;

public class ReadingHistoryTest {
    private Book book(String id,String date,ReadingStatus status){return new Book(id,"Название "+id,"Автор",0,status,BookLocation.UNKNOWN,date);}
    @Test public void filtersYearAndGroupsDescendingWithPartialDates(){
        List<ReadingHistory.Row> rows=ReadingHistory.rows(Arrays.asList(
            book("unknown","",ReadingStatus.READ),book("old","2024-12-31",ReadingStatus.READ),
            book("year","2025",ReadingStatus.READ),book("month","2025-02",ReadingStatus.READ),
            book("day","2025-02-03",ReadingStatus.READ),book("new","2025-03-01",ReadingStatus.READ),
            book("unread","2025-01-01",ReadingStatus.WANT)),"",2025);
        List<String> ids=new ArrayList<>(),headings=new ArrayList<>();
        for(ReadingHistory.Row row:rows)if(row.book==null)headings.add(row.heading);else ids.add(row.book.id);
        assertEquals(Arrays.asList("new","day","month","year"),ids);
        assertEquals(Arrays.asList("2025 год","Март 2025","Февраль 2025","Месяц не указан"),headings);
    }
    @Test public void searchDoesNotLeaveEmptyGroups(){
        List<Book> books=Arrays.asList(book("first","2025-02-01",ReadingStatus.READ),book("second","2024",ReadingStatus.READ));
        List<ReadingHistory.Row> rows=ReadingHistory.rows(books," SECOND ",2024);
        assertEquals(3,rows.size());assertEquals("2024 год",rows.get(0).heading);assertEquals("second",rows.get(2).book.id);
        assertTrue(ReadingHistory.rows(books,"missing",2024).isEmpty());
    }
}
