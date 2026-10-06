package ru.homebooks.library;

import java.util.*;

final class ReadingHistory {
    static final class Row {
        final String heading;
        final Book book;
        Row(String heading,Book book){this.heading=heading;this.book=book;}
    }
    static List<Row> rows(List<Book> library,String query){
        String q=query.trim().toLowerCase(Locale.ROOT);
        List<Book> books=new ArrayList<>();
        for(Book b:library)if(b.status==ReadingStatus.READ&&(b.title+" "+b.author+" "+b.isbn).toLowerCase(Locale.ROOT).contains(q))books.add(b);
        Comparator<Book> tie=BookSort.TITLE_ASC.comparator();
        Collections.sort(books,(a,b)->{int order=b.readOn.compareTo(a.readOn);return order==0?tie.compare(a,b):order;});
        List<Row> rows=new ArrayList<>();String lastYear=null,lastMonth=null;
        for(Book b:books){
            String year=b.readOn.isEmpty()?"":b.readOn.substring(0,4);
            String month=b.readOn.length()>=7?b.readOn.substring(0,7):"";
            if(!year.equals(lastYear)){
                rows.add(new Row(year.isEmpty()?"Дата не указана":year+" год",null));lastYear=year;lastMonth=null;
            }
            if(!year.isEmpty()&&!month.equals(lastMonth)){
                rows.add(new Row(month.isEmpty()?"Месяц не указан":ReadingDate.label(month),null));lastMonth=month;
            }
            rows.add(new Row(null,b));
        }
        return rows;
    }
}
