package ru.homebooks.library;
import org.junit.Test;
import static org.junit.Assert.*;
import java.io.*;
import java.util.*;
public class ReadingDateTest {
    @Test public void validatesCalendarDates(){assertTrue(ReadingDate.valid(""));assertTrue(ReadingDate.valid("2024-02"));assertTrue(ReadingDate.valid("2024"));assertFalse(ReadingDate.valid("2024-13"));assertTrue(ReadingDate.valid("2024-02-29"));assertFalse(ReadingDate.valid("2023-02-29"));assertFalse(ReadingDate.valid("2024-13-01"));assertFalse(ReadingDate.valid("2024-01-01junk"));}
    @Test public void csvPreservesDateAndMissingDate()throws Exception{
        Book dated=new Book("9780306406157","Книга","Автор",1,ReadingStatus.READ,BookLocation.HOME,"2024-02-29");
        StringWriter writer=new StringWriter();Csv.write(writer,Arrays.asList(dated,new Book("","Другая","",2)));
        List<Book> restored=Csv.read(new StringReader(writer.toString()));assertEquals("2024-02-29",restored.get(0).readOn);assertEquals("",restored.get(1).readOn);
    }
}
