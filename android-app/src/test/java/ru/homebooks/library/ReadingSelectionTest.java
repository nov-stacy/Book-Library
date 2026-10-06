package ru.homebooks.library;

import org.junit.Test;
import static org.junit.Assert.*;

public class ReadingSelectionTest {
    @Test public void readStatusUsesToday(){
        assertEquals("2026-10-06",ReadingSelection.dateAfterStatusChange(ReadingStatus.READING,ReadingStatus.READ,"","2026-10-06"));
    }

    @Test public void leavingReadStatusClearsDate(){
        assertEquals("",ReadingSelection.dateAfterStatusChange(ReadingStatus.READ,ReadingStatus.WANT,"2026-09-12","2026-10-06"));
    }

    @Test public void unrelatedStatusChangeKeepsDate(){
        assertEquals("",ReadingSelection.dateAfterStatusChange(ReadingStatus.NONE,ReadingStatus.WANT,"","2026-10-06"));
    }

    @Test public void selectedDateMarksBookAsRead(){
        assertEquals(ReadingStatus.READ,ReadingSelection.statusAfterDateChange("2026-09-12",ReadingStatus.WANT));
        assertEquals(ReadingStatus.WANT,ReadingSelection.statusAfterDateChange("",ReadingStatus.WANT));
    }
}
