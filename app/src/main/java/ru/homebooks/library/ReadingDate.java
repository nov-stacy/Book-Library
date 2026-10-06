package ru.homebooks.library;

import java.text.*;
import java.util.*;

final class ReadingDate {
    static boolean valid(String value){
        if(value==null)return false;
        if(value.isEmpty())return true;
        if(value.matches("[0-9]{4}"))return !value.equals("0000");
        if(value.matches("[0-9]{4}-[0-9]{2}"))return valid(value+"-01");
        if(!value.matches("[0-9]{4}-[0-9]{2}-[0-9]{2}"))return false;
        SimpleDateFormat f=new SimpleDateFormat("yyyy-MM-dd",Locale.ROOT);f.setLenient(false);
        ParsePosition pos=new ParsePosition(0);return f.parse(value,pos)!=null&&pos.getIndex()==value.length();
    }
    static String label(String value){if(value.isEmpty())return "Не указана";
        if(value.length()==4)return value+" г.";
        if(value.length()==7){String[] months={"Январь","Февраль","Март","Апрель","Май","Июнь","Июль","Август","Сентябрь","Октябрь","Ноябрь","Декабрь"};return months[Integer.parseInt(value.substring(5))-1]+" "+value.substring(0,4);}
        return value.substring(8)+"."+value.substring(5,7)+"."+value.substring(0,4);}
}
