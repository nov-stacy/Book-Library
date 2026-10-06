package ru.homebooks.library;

import java.util.*;

final class BookSeries {
    static final int BOOKS=0, PERIODICAL=1;
    final long id;
    final String name;
    final int kind;
    final Integer totalParts;
    final List<Member> members;

    BookSeries(long id,String name,int kind,Integer totalParts,List<Member> members){
        if(kind!=BOOKS&&kind!=PERIODICAL)throw new IllegalArgumentException("Invalid series kind");
        this.id=id;this.name=name;this.kind=kind;this.totalParts=totalParts;this.members=new ArrayList<>(members);
    }
    String kindLabel(){return kind==PERIODICAL?"Журнал":"Книжная серия";}
    String icon(){return kind==PERIODICAL?"document":"library";}
    static final class Member {
        final String bookId,position,issueNumber,issueDate;
        Member(String bookId,String position,String issueNumber,String issueDate){this.bookId=bookId;this.position=position;this.issueNumber=issueNumber;this.issueDate=issueDate;}
    }
    static String memberLabel(BookSeries series,Member member){
        if(series==null||member==null)return "";
        if(series.kind==PERIODICAL){String number=member.issueNumber.isEmpty()?"":member.issueNumber.startsWith("№")?member.issueNumber:"№ "+member.issueNumber;String date=member.issueDate.isEmpty()?"":ReadingDate.label(member.issueDate);return number+(number.isEmpty()||date.isEmpty()?"":" · ")+date;}
        return member.position.isEmpty()?series.name:series.name+" · книга "+member.position;
    }
}
