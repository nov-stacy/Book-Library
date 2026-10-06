package ru.homebooks.library;

import java.util.*;

final class BookType {
    static final String[] ICONS={"code","sparkles","pencil","scissors","game","book-open","images","palette","library","tag","brush","camera","film","music","heart","star","globe","map","compass","cooking","leaf","flower","cat","dog","sport","puzzle","science","history","graduation","tools"};
    static final String[] ICON_LABELS={"Программирование","Идеи","Рисование","Рукоделие","Игры","Книга","Артбуки","Искусство","Энциклопедии","Метка","Кисть","Фотография","Кино","Музыка","Любимое","Звезда","Мир","Карты","Путешествия","Кулинария","Природа","Цветы","Кошки","Собаки","Спорт","Головоломки","Наука","История","Образование","Инструменты"};
    static final int[] PALETTE={0xFF648499,0xFF86926A,0xFFBD9273,0xFFA97791,0xFF75938A,0xFFA8875F,0xFF9281AA,0xFFAB796D,0xFF799098,0xFFB67C7C,0xFF6F8FAF,0xFF8A7D65,0xFF7C6D9A,0xFF5F8F88,0xFFB08A5A,0xFF956C79,0xFF6E8570,0xFF9B7653,0xFF78859F,0xFFA06F5F};
    static final String[][] DEFAULTS={{"Программирование","code"},{"Хобби","sparkles"},{"Рисование","pencil"},{"Рукоделие","scissors"},{"Игры","game"},{"Художественная","book-open"},{"Артбуки","images"},{"Искусство","palette"},{"Энциклопедии","library"}};

    final long id;
    final String name,icon;
    final int color;
    final List<String> bookIds;

    BookType(long id,String name,String icon,int color,List<String> bookIds){
        this.id=id;this.name=name;this.icon=icon;this.color=opaque(color);this.bookIds=new ArrayList<>(bookIds);
    }
    static int opaque(int color){return color|0xFF000000;}
    static String hex(int color){return String.format(Locale.ROOT,"#%06X",color&0xFFFFFF);}
    static int parseColor(String value){
        String text=value==null?"":value.trim();if(!text.matches("#[0-9a-fA-F]{6}"))throw new IllegalArgumentException("Цвет должен быть в формате #RRGGBB");
        return 0xFF000000|(int)Long.parseLong(text.substring(1),16);
    }
    static boolean validIcon(String icon){for(String candidate:ICONS)if(candidate.equals(icon))return true;return false;}
    static String iconLabel(String icon){for(int i=0;i<ICONS.length;i++)if(ICONS[i].equals(icon))return ICON_LABELS[i];return "Метка";}
    static int[] suggestions(Collection<BookType> types,int count){
        Set<Integer> used=new HashSet<>();for(BookType type:types)used.add(opaque(type.color));
        List<Integer> result=new ArrayList<>();for(int color:PALETTE)if(!used.contains(color))result.add(color);
        for(int i=0;result.size()<count&&i<720;i++){
            float hue=(types.size()*137.508f+i*29f)%360f,saturation=.32f+(i%3)*.07f,value=.72f+(i%2)*.08f;
            int candidate=hsv(hue,saturation,value);if(!used.contains(candidate)&&!result.contains(candidate))result.add(candidate);
        }
        int[] colors=new int[Math.min(count,result.size())];for(int i=0;i<colors.length;i++)colors[i]=result.get(i);return colors;
    }
    private static int hsv(float h,float s,float v){
        float c=v*s,x=c*(1-Math.abs((h/60f)%2-1)),m=v-c,r=0,g=0,b=0;
        if(h<60){r=c;g=x;}else if(h<120){r=x;g=c;}else if(h<180){g=c;b=x;}else if(h<240){g=x;b=c;}else if(h<300){r=x;b=c;}else{r=c;b=x;}
        return 0xFF000000|(Math.round((r+m)*255)<<16)|(Math.round((g+m)*255)<<8)|Math.round((b+m)*255);
    }
}
