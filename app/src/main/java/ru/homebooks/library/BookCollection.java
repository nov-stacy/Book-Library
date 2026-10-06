package ru.homebooks.library;
import java.util.*;
final class BookCollection {
    static String label(Map<String,String> labels,String bookId){String name=labels.get(bookId);return name==null?"Без коллекции":name;}
    final long id;
    final String name;
    final List<String> isbns;
    BookCollection(long id,String name,List<String> isbns){this.id=id;this.name=name;this.isbns=new ArrayList<>(isbns);}
}
