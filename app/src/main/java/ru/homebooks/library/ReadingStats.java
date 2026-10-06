package ru.homebooks.library;

import java.util.*;

final class ReadingStats {
    static final String[] MONTHS={"Январь","Февраль","Март","Апрель","Май","Июнь","Июль","Август","Сентябрь","Октябрь","Ноябрь","Декабрь"};
    static final String[] SHORT_MONTHS={"Янв","Фев","Мар","Апр","Май","Июн","Июл","Авг","Сен","Окт","Ноя","Дек"};

    static final class TypeCount {
        final String name,icon;
        final int color,count;
        TypeCount(String name,String icon,int color,int count){this.name=name;this.icon=icon;this.color=color;this.count=count;}
    }
    static final class Year {
        final int year,total;
        final int[] months;
        final List<TypeCount> types;
        Year(int year,int total,int[] months,List<TypeCount> types){this.year=year;this.total=total;this.months=months;this.types=types;}
        int bestCount(){int best=0;for(int value:months)best=Math.max(best,value);return best;}
        String bestMonths(){
            int best=bestCount();if(best==0)return "—";
            List<String> names=new ArrayList<>();for(int i=0;i<months.length;i++)if(months[i]==best)names.add(MONTHS[i].toLowerCase(new Locale("ru")));
            String joined=names.get(0);for(int i=1;i<names.size()-1;i++)joined+=", "+names.get(i);if(names.size()>1)joined+=" и "+names.get(names.size()-1);
            return Character.toUpperCase(joined.charAt(0))+joined.substring(1);
        }
    }

    static Year calculate(int year,List<Book> books,Map<String,BookType> labels){
        int total=0;int[] months=new int[12];Map<String,TypeCount> grouped=new LinkedHashMap<>();
        String prefix=String.format(Locale.ROOT,"%04d",year);
        for(Book book:books){
            if(book.status!=ReadingStatus.READ||book.readOn.length()<4||!book.readOn.substring(0,4).equals(prefix))continue;
            total++;
            if(book.readOn.length()>=7){try{int month=Integer.parseInt(book.readOn.substring(5,7));if(month>=1&&month<=12)months[month-1]++;}catch(NumberFormatException ignored){}}
            BookType type=labels.get(book.id);String key=type==null?"":Long.toString(type.id);
            TypeCount current=grouped.get(key);String name=type==null?"Без типа":type.name,icon=type==null?"tag":type.icon;int color=type==null?0xFF7C7380:type.color;
            grouped.put(key,new TypeCount(name,icon,color,current==null?1:current.count+1));
        }
        List<TypeCount> types=new ArrayList<>(grouped.values());
        Collections.sort(types,(a,b)->{int count=Integer.compare(b.count,a.count);return count!=0?count:a.name.compareToIgnoreCase(b.name);});
        return new Year(year,total,months,types);
    }

    static int monthlyPlan(int goal,int read,int selectedYear,Calendar now){
        int currentYear=now.get(Calendar.YEAR);if(selectedYear!=currentYear)return divideUp(goal,12);if(goal<=read)return 0;
        return divideUp(goal-read,Math.max(1,12-now.get(Calendar.MONTH)));
    }
    static double pace(int read,int selectedYear,Calendar now){
        int current=now.get(Calendar.YEAR);if(selectedYear>current)return 0;
        return read/(selectedYear==current?(now.get(Calendar.MONTH)+1d):12d);
    }
    static int plannedByNow(int goal,int selectedYear,Calendar now){
        int current=now.get(Calendar.YEAR);if(selectedYear<current)return goal;if(selectedYear>current)return 0;
        return divideUp(goal*now.get(Calendar.DAY_OF_YEAR),now.getActualMaximum(Calendar.DAY_OF_YEAR));
    }
    private static int divideUp(int value,int divisor){return (value+divisor-1)/divisor;}
}
