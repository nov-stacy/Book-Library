package ru.homebooks.library;

final class ReadingSelection {
    private ReadingSelection() {}

    static String dateAfterStatusChange(ReadingStatus previous,ReadingStatus next,String currentDate,String today){
        if(next==ReadingStatus.READ&&previous!=ReadingStatus.READ)return today;
        if(previous==ReadingStatus.READ&&next!=ReadingStatus.READ)return "";
        return currentDate;
    }

    static ReadingStatus statusAfterDateChange(String date,ReadingStatus current){
        return date.isEmpty()?current:ReadingStatus.READ;
    }
}
