package ru.homebooks.library;

import org.junit.*;
import org.junit.rules.TemporaryFolder;
import org.json.*;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.zip.*;
import static org.junit.Assert.*;

public class LibraryBackupTest {
    @Rule public TemporaryFolder temp=new TemporaryFolder();
    private final String isbn="9780306406157";
    @Test public void localIdentityAndIsbnRoundTripTogether()throws Exception{
        String id=Book.newLocalId();Book book=new Book(id,isbn,"Книга","Автор",42,ReadingStatus.READ,BookLocation.HOME,"2026-06-05");
        ByteArrayOutputStream out=new ByteArrayOutputStream();LibraryBackup.write(out,Collections.singletonList(book),code->new File(temp.getRoot(),"absent"));
        try(LibraryBackup.Archive archive=LibraryBackup.read(new ByteArrayInputStream(out.toByteArray()),temp.getRoot())){Book b=archive.books.get(0);assertEquals(id,b.id);assertEquals(isbn,b.isbn);assertEquals(book.readOn,b.readOn);}
        StringWriter csv=new StringWriter();Csv.write(csv,Collections.singletonList(book));Book restored=Csv.read(new StringReader(csv.toString())).get(0);assertEquals(id,restored.id);assertEquals(isbn,restored.isbn);
    }
    @Test public void roundTripPreservesTextDateStatusAndCover()throws Exception{
        File cover=temp.newFile("cover.jpg");byte[] image={1,3,5,7};try(OutputStream out=new FileOutputStream(cover)){out.write(image);}
        Book book=new Book(isbn,"Название, \"с кавычками\"\nи новой строкой","Автор Ё",123456789,ReadingStatus.READ,BookLocation.HOME,"2024-02-29");
        ByteArrayOutputStream out=new ByteArrayOutputStream();LibraryBackup.write(out,Collections.singletonList(book),code->cover);
        try(LibraryBackup.Archive archive=LibraryBackup.read(new ByteArrayInputStream(out.toByteArray()),temp.getRoot())){
            Book restored=archive.books.get(0);assertEquals(book.title,restored.title);assertEquals(book.author,restored.author);
            assertEquals(book.readOn,restored.readOn);assertEquals(book.addedAt,restored.addedAt);assertEquals(book.status,restored.status);
            assertArrayEquals(image,java.nio.file.Files.readAllBytes(archive.cover(isbn).toPath()));
        }
    }
    @Test public void roundTripPreservesBookTypes()throws Exception{
        Book book=new Book(isbn,"Книга","",1);BookType type=new BookType(7,"Программирование","code",0xFF648499,Collections.singletonList(isbn));
        ByteArrayOutputStream out=new ByteArrayOutputStream();LibraryBackup.write(out,Collections.singletonList(book),code->new File(temp.getRoot(),"absent"),Collections.emptyList(),Collections.singletonList(type));
        try(LibraryBackup.Archive archive=LibraryBackup.read(new ByteArrayInputStream(out.toByteArray()),temp.getRoot())){
            assertEquals(1,archive.bookTypes.size());BookType restored=archive.bookTypes.get(0);assertEquals(type.name,restored.name);assertEquals(type.icon,restored.icon);assertEquals(type.color,restored.color);assertEquals(Collections.singletonList(isbn),restored.bookIds);
        }
    }
    @Test public void roundTripPreservesReadingChallenges()throws Exception{
        Map<Integer,Integer> goals=new LinkedHashMap<>();goals.put(2026,30);goals.put(2025,24);
        ByteArrayOutputStream out=new ByteArrayOutputStream();LibraryBackup.write(out,Collections.emptyList(),code->new File(temp.getRoot(),"absent"),Collections.emptyList(),Collections.emptyList(),goals);
        try(LibraryBackup.Archive archive=LibraryBackup.read(new ByteArrayInputStream(out.toByteArray()),temp.getRoot())){assertEquals(goals,archive.readingChallenges);}
    }
    @Test public void roundTripPreservesSeriesAndPeriodicals()throws Exception{
        Book first=new Book(isbn,"Крещение огнём","",1),second=new Book("9780804429573","Мир фантастики № 7","",2);List<Book> books=Arrays.asList(first,second);
        BookSeries novel=new BookSeries(1,"Ведьмак",BookSeries.BOOKS,8,Collections.singletonList(new BookSeries.Member(first.id,"5","","")));
        BookSeries magazine=new BookSeries(2,"Мир фантастики",BookSeries.PERIODICAL,null,Collections.singletonList(new BookSeries.Member(second.id,"","7","2025-07")));
        ByteArrayOutputStream out=new ByteArrayOutputStream();LibraryBackup.write(out,books,code->new File(temp.getRoot(),"absent"),Collections.emptyList(),Collections.emptyList(),Collections.emptyMap(),Arrays.asList(novel,magazine));
        try(LibraryBackup.Archive archive=LibraryBackup.read(new ByteArrayInputStream(out.toByteArray()),temp.getRoot())){assertEquals(2,archive.series.size());BookSeries restoredBook=null,restoredMagazine=null;for(BookSeries item:archive.series)if(item.kind==BookSeries.BOOKS)restoredBook=item;else restoredMagazine=item;assertNotNull(restoredBook);assertNotNull(restoredMagazine);assertEquals("5",restoredBook.members.get(0).position);assertEquals("2025-07",restoredMagazine.members.get(0).issueDate);}
    }
    @Test public void emptyLibraryAndMissingCoverAreSupported()throws Exception{
        for(List<Book> books:Arrays.asList(Collections.<Book>emptyList(),Collections.singletonList(new Book(isbn,"Книга","",0)))){
            ByteArrayOutputStream out=new ByteArrayOutputStream();LibraryBackup.write(out,books,code->new File(temp.getRoot(),"absent"));
            try(LibraryBackup.Archive archive=LibraryBackup.read(new ByteArrayInputStream(out.toByteArray()),temp.getRoot())){assertEquals(books.size(),archive.books.size());assertFalse(archive.cover(isbn).exists());}
        }
    }
    private String manifest(boolean cover)throws Exception{
        JSONObject row=new JSONObject().put("isbn",isbn).put("title","Книга").put("author","").put("addedAt",1).put("status",2).put("cover",cover);
        return new JSONObject().put("format","home-library-backup").put("version",1).put("books",new JSONArray().put(row)).toString();
    }
    private byte[] zip(String name,String data)throws Exception{
        ByteArrayOutputStream bytes=new ByteArrayOutputStream();try(ZipOutputStream zip=new ZipOutputStream(bytes)){zip.putNextEntry(new ZipEntry(name));zip.write(data.getBytes(StandardCharsets.UTF_8));zip.closeEntry();}return bytes.toByteArray();
    }
    private void rejected(byte[] input)throws Exception{
        int before=temp.getRoot().list().length;
        try(LibraryBackup.Archive ignored=LibraryBackup.read(new ByteArrayInputStream(input),temp.getRoot())){fail("Must reject archive");}catch(IOException expected){}
        assertEquals("Staging must be removed",before,temp.getRoot().list().length);
    }
    @Test public void rejectsTraversalMissingCoversAndUnsupportedVersions()throws Exception{
        rejected(zip("../escaped","bad"));rejected(zip("library.json",manifest(true)));
        rejected(zip("library.json",manifest(false).replace("\"version\":1","\"version\":99")));
        assertFalse(new File(temp.getRoot().getParentFile(),"escaped").exists());
    }
    @Test public void rejectsDuplicateIsbnsAndInvalidStatuses()throws Exception{
        JSONObject data=new JSONObject(manifest(false));data.getJSONArray("books").put(data.getJSONArray("books").getJSONObject(0));rejected(zip("library.json",data.toString()));
        data=new JSONObject(manifest(false));data.getJSONArray("books").getJSONObject(0).put("status",99);rejected(zip("library.json",data.toString()));
    }
    @Test public void rejectsOversizedExpandedEntry()throws Exception{
        ByteArrayOutputStream bytes=new ByteArrayOutputStream();try(ZipOutputStream zip=new ZipOutputStream(bytes)){zip.putNextEntry(new ZipEntry("books.csv"));byte[] block=new byte[1024*1024];for(int i=0;i<17;i++)zip.write(block);zip.closeEntry();}
        rejected(bytes.toByteArray());
    }
}
