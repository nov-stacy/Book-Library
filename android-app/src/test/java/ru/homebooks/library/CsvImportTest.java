package ru.homebooks.library;
import org.junit.Test;
import java.io.*;
import java.util.*;
import static org.junit.Assert.*;
public class CsvImportTest {
    @Test public void exportedDataRoundTripsIncludingQuotedMultilineAndFormulaText()throws Exception{
        Book book=new Book("9780306406157","=Название, \"часть\"\nПродолжение","Ёж",123000,ReadingStatus.WANT,BookLocation.PARENTS);
        StringWriter out=new StringWriter();Csv.write(out,Collections.singletonList(book));
        Book restored=Csv.read(new StringReader(out.toString())).get(0);
        assertEquals(book.title,restored.title);assertEquals(book.author,restored.author);assertEquals(book.addedAt,restored.addedAt);assertEquals(book.status,restored.status);assertEquals(book.location,restored.location);
    }
    @Test public void supportsSemicolonEnglishHeadersAndIsbn10()throws Exception{
        List<Book> books=Csv.read(new StringReader("ISBN;title;author\r\n0-306-40615-2;\"Название; часть\";Автор\r\n"));
        assertEquals("9780306406157",books.get(0).isbn);assertEquals("Название; часть",books.get(0).title);assertEquals(ReadingStatus.NONE,books.get(0).status);
    }
    @Test public void acceptsOldFourColumnExport()throws Exception{
        Book book=Csv.read(new StringReader("ISBN,Название,Автор,Дата добавления (UTC)\n9780306406157,Книга,,1970-01-01T00:00:00Z")).get(0);
        assertEquals(0,book.addedAt);assertEquals("",book.author);assertEquals(ReadingStatus.NONE,book.status);
    }
    @Test public void rejectsMalformedRowsStatusesDatesAndQuotes()throws Exception{
        for(String csv:Arrays.asList("ISBN,Название\n9780306406158,Книга","ISBN,Название\n9780306406157,\"Книга","ISBN,Название\n9780306406157,Книга,лишнее","ISBN,Название,Статус\n9780306406157,Книга,неизвестно","ISBN,Название,Дата добавления (UTC)\n9780306406157,Книга,2026-02-30T00:00:00Z")){
            try{Csv.read(new StringReader(csv));fail(csv);}catch(IOException expected){}
        }
    }
    @Test public void booksWithoutIsbnKeepTheirIdentityAcrossExports()throws Exception{
        java.util.List<Book> books=Csv.read(new java.io.StringReader("Название,Автор\nБез ISBN,Автор\nБез ISBN,Автор"));
        assertEquals("",books.get(0).isbn);assertNotEquals(books.get(0).id,books.get(1).id);
        java.io.StringWriter csv=new java.io.StringWriter();Csv.write(csv,books);java.util.List<Book> restored=Csv.read(new java.io.StringReader(csv.toString()));
        assertEquals(books.get(0).id,restored.get(0).id);assertEquals(books.get(1).id,restored.get(1).id);assertEquals("",restored.get(0).isbn);
    }
}
