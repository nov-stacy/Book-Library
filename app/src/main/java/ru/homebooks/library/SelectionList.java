package ru.homebooks.library;
import java.util.*;

/** Selection is keyed independently of the visible search results. */
final class SelectionList {
    static final class Item {
        final String id,title,subtitle,search;
        Item(String id,String title,String subtitle,String search){this.id=id;this.title=title;this.subtitle=subtitle;this.search=search;}
    }
    final List<Item> all,visible=new ArrayList<>();
    final Set<String> selected;
    SelectionList(List<Item> items,Set<String> selected){all=new ArrayList<>(items);this.selected=new LinkedHashSet<>(selected);filter("");}
    void filter(String query){String q=normalize(query.trim());visible.clear();for(Item item:all)if(normalize(item.title+" "+item.subtitle+" "+item.search).contains(q))visible.add(item);}
    void toggle(int index){String id=visible.get(index).id;if(!selected.add(id))selected.remove(id);}
    private static String normalize(String s){return s.toLowerCase(Locale.ROOT).replace('ё','е');}
}
