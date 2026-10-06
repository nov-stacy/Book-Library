package ru.homebooks.library;
import org.junit.Test;
import java.util.*;
import static org.junit.Assert.*;
public class SelectionListTest {
    @Test public void selectionSurvivesFilteringAndOnlyVisibleItemIsToggled(){
        SelectionList model=new SelectionList(Arrays.asList(new SelectionList.Item("a","Первая","Автор","123"),new SelectionList.Item("b","Вторая","Другой","456")),new HashSet<>(Collections.singleton("a")));
        model.filter("456");model.toggle(0);assertEquals(new HashSet<>(Arrays.asList("a","b")),model.selected);
        model.filter("нет совпадения");assertTrue(model.visible.isEmpty());assertEquals(2,model.selected.size());
        model.filter("АВТОР");model.toggle(0);assertEquals(Collections.singleton("b"),model.selected);
        model.filter("");assertEquals(2,model.visible.size());
    }
    @Test public void collectionSearchIgnoresCaseWhitespaceAndYo(){
        SelectionList model=new SelectionList(Collections.singletonList(new SelectionList.Item("1","Всё любимое","Книг: 3","")),Collections.emptySet());
        model.filter("  ВСЕ ЛЮБИМОЕ ");assertEquals(1,model.visible.size());
    }
}
