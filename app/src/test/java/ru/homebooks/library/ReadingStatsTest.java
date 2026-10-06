package ru.homebooks.library;

import org.junit.Test;
import java.util.*;
import static org.junit.Assert.*;

public class ReadingStatsTest {
    @Test public void countsOnlyReadBooksInYearAndGroupsTypes(){
        Book first=new Book(Book.newLocalId(),"","Первая","",1,ReadingStatus.READ,BookLocation.HOME,"2026-03-02");
        Book second=new Book(Book.newLocalId(),"","Вторая","",2,ReadingStatus.READ,BookLocation.HOME,"2026-03");
        Book partial=new Book(Book.newLocalId(),"","Без месяца","",3,ReadingStatus.READ,BookLocation.HOME,"2026");
        Book wrongStatus=new Book(Book.newLocalId(),"","Не считается","",4,ReadingStatus.READING,BookLocation.HOME,"2026-03-04");
        Book otherYear=new Book(Book.newLocalId(),"","Другой год","",5,ReadingStatus.READ,BookLocation.HOME,"2025-03-04");
        BookType art=new BookType(4,"Искусство","palette",0xFF123456,Collections.emptyList());Map<String,BookType> labels=new HashMap<>();labels.put(first.id,art);labels.put(second.id,art);
        ReadingStats.Year stats=ReadingStats.calculate(2026,Arrays.asList(first,second,partial,wrongStatus,otherYear),labels);
        assertEquals(3,stats.total);assertEquals(2,stats.months[2]);assertEquals(2,stats.types.size());assertEquals("Искусство",stats.types.get(0).name);assertEquals(2,stats.types.get(0).count);assertEquals("Март",stats.bestMonths());
    }
    @Test public void bestMonthShowsTiesAndPacingUsesRemainingMonths(){
        int[] months=new int[12];months[2]=3;months[6]=3;months[9]=3;ReadingStats.Year stats=new ReadingStats.Year(2026,9,months,Collections.emptyList());assertEquals("Март, июль и октябрь",stats.bestMonths());
        Calendar now=new GregorianCalendar(2026,Calendar.OCTOBER,6);assertEquals(3,ReadingStats.monthlyPlan(30,22,2026,now));assertEquals(2.2,ReadingStats.pace(22,2026,now),.001);assertEquals(23,ReadingStats.plannedByNow(30,2026,now));
        assertEquals(2,ReadingStats.monthlyPlan(24,0,2027,now));assertEquals(3,ReadingStats.monthlyPlan(36,25,2024,now));assertEquals(2,ReadingStats.monthlyPlan(24,29,2025,now));assertEquals(0,ReadingStats.pace(0,2027,now),.001);
    }
}
