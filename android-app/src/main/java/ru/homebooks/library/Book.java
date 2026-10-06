package ru.homebooks.library;

public final class Book {
    public final String id, isbn, title, author;
    public final long addedAt;
    public final String readOn;
    public final ReadingStatus status;
    public final BookLocation location;
    public Book(String isbn, String title, String author, long addedAt) {
        this(isbn,title,author,addedAt,ReadingStatus.NONE);
    }
    public Book(String isbn, String title, String author, long addedAt, ReadingStatus status) {
        this(isbn,title,author,addedAt,status,BookLocation.UNKNOWN);
    }
    public Book(String isbn,String title,String author,long addedAt,ReadingStatus status,BookLocation location){
        this(isbn,title,author,addedAt,status,location,"");
    }
    public Book(String isbn,String title,String author,long addedAt,ReadingStatus status,BookLocation location,String readOn){
        this(isbn.isEmpty()?newLocalId():isbn,isLocalId(isbn)?"":isbn,title,author,addedAt,status,location,readOn);
    }
    public Book(String id,String isbn,String title,String author,long addedAt,ReadingStatus status,BookLocation location,String readOn){
        if(!ReadingDate.valid(readOn))throw new IllegalArgumentException("Invalid reading date");
        this.readOn=readOn;
        this.location=location==BookLocation.UNKNOWN?BookLocation.HOME:location;
        this.status = status;
        this.id = id;
        this.isbn = isbn; this.title = title; this.author = author; this.addedAt = addedAt;
    }
    public static String newLocalId(){return "local-"+java.util.UUID.randomUUID();}
    public static boolean isLocalId(String id){return id!=null&&id.matches("local-[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}");}
    public static boolean validId(String id){return isLocalId(id)||(id!=null&&id.equals(Isbn.normalize(id)));}
}

