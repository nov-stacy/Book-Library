package ru.homebooks.library;

import android.content.Intent;
import android.graphics.*;
import android.os.Bundle;
import android.text.*;
import android.view.*;
import android.widget.*;
import androidx.activity.ComponentActivity;
import java.util.*;

public final class SeriesActivity extends ComponentActivity {
    private LibraryApp app;private LinearLayout content;private EditText search;private Button booksTab,periodicalsTab,add;private int kind=BookSeries.BOOKS,request;private List<BookSeries> all=Collections.emptyList();
    @Override public void onCreate(Bundle state){super.onCreate(state);app=(LibraryApp)getApplication();if(state!=null)kind=state.getInt("kind",kind);LinearLayout root=Ui.column(this);root.setBackgroundColor(Ui.PAPER);setContentView(root);Ui.insets(root);root.addView(Ui.toolbar(this,"Серии","menu",()->Navigation.show(this,false)));
        LinearLayout controls=Ui.column(this);controls.setPadding(Ui.dp(this,16),0,Ui.dp(this,16),0);root.addView(controls);LinearLayout tabs=new LinearLayout(this);booksTab=tab("Книжные серии",BookSeries.BOOKS);periodicalsTab=tab("Журналы",BookSeries.PERIODICAL);tabs.addView(booksTab,new LinearLayout.LayoutParams(0,Ui.dp(this,48),1));LinearLayout.LayoutParams pp=new LinearLayout.LayoutParams(0,Ui.dp(this,48),1);pp.setMarginStart(Ui.dp(this,8));tabs.addView(periodicalsTab,pp);controls.addView(tabs);Ui.gap(controls,10);search=Ui.search(this,"Найти по названию");controls.addView(search);Ui.gap(controls,10);
        ScrollView scroll=new ScrollView(this);scroll.setFillViewport(true);root.addView(scroll,new LinearLayout.LayoutParams(-1,0,1));content=Ui.column(this);content.setPadding(Ui.dp(this,16),0,Ui.dp(this,16),Ui.dp(this,12));scroll.addView(content);
        LinearLayout footer=Ui.column(this);footer.setPadding(Ui.dp(this,16),Ui.dp(this,8),Ui.dp(this,16),Ui.dp(this,8));add=Ui.primary(this,"",this::create);footer.addView(add,new LinearLayout.LayoutParams(-1,-2));root.addView(footer);search.addTextChangedListener(new TextWatcher(){public void beforeTextChanged(CharSequence s,int a,int b,int c){}public void afterTextChanged(Editable e){}public void onTextChanged(CharSequence s,int a,int b,int c){render();}});updateTabs();}
    private Button tab(String label,int value){Button button=Ui.button(this,label,()->{kind=value;updateTabs();render();});button.setTextSize(14);return button;}
    @Override protected void onResume(){super.onResume();refresh();}
    @Override protected void onSaveInstanceState(Bundle state){state.putInt("kind",kind);super.onSaveInstanceState(state);}
    private void refresh(){int token=++request;app.io.execute(()->{try{List<BookSeries> data=app.store.bookSeries();runOnUiThread(()->{if(!isDestroyed()&&token==request){all=data;render();}});}catch(Exception e){runOnUiThread(()->Toast.makeText(this,"Не удалось загрузить серии",Toast.LENGTH_LONG).show());}});}
    private void updateTabs(){styleTab(booksTab,kind==BookSeries.BOOKS);styleTab(periodicalsTab,kind==BookSeries.PERIODICAL);add.setText(kind==BookSeries.BOOKS?"＋ Новая серия":"＋ Новый журнал");}
    private void styleTab(Button button,boolean selected){button.setTextColor(selected?Color.WHITE:Ui.GREEN);button.setBackground(new android.graphics.drawable.RippleDrawable(android.content.res.ColorStateList.valueOf(0x22735081),Ui.surface(this,selected?Ui.GREEN:Color.WHITE,selected?Ui.GREEN:Ui.LINE,12),null));}
    private void render(){if(content==null)return;content.removeAllViews();String q=search.getText().toString().trim().toLowerCase(Locale.ROOT);int shown=0;for(BookSeries series:all){if(series.kind!=kind||!series.name.toLowerCase(Locale.ROOT).contains(q))continue;shown++;LinearLayout row=new LinearLayout(this);row.setGravity(Gravity.CENTER_VERTICAL);row.setPadding(Ui.dp(this,14),Ui.dp(this,10),Ui.dp(this,8),Ui.dp(this,10));row.setMinimumHeight(Ui.dp(this,72));row.setBackground(new android.graphics.drawable.RippleDrawable(android.content.res.ColorStateList.valueOf(0x14735081),Ui.surface(this,Color.WHITE,Ui.LINE,14),null));
            ImageView icon=new ImageView(this);icon.setImageDrawable(new Ui.Symbol(series.icon(),Ui.GREEN));icon.setBackground(Ui.round(Ui.TINT,this));Ui.pad(icon,10);row.addView(icon,new LinearLayout.LayoutParams(Ui.dp(this,42),Ui.dp(this,42)));LinearLayout words=Ui.column(this);words.setPadding(Ui.dp(this,12),0,0,0);TextView name=Ui.text(this,series.name,16);name.setTypeface(Typeface.create("sans-serif-medium",Typeface.NORMAL));words.addView(name);String summary=series.kind==BookSeries.PERIODICAL?series.members.size()+" "+issuesWord(series.members.size()):series.totalParts==null?series.members.size()+" "+booksWord(series.members.size()):series.members.size()+" из "+series.totalParts+" книг";words.addView(Ui.muted(this,summary,13));row.addView(words,new LinearLayout.LayoutParams(0,-2,1));Button edit=Ui.editButton(this,"Изменить «"+series.name+"»",()->startActivity(new Intent(this,SeriesEditorActivity.class).putExtra("seriesId",series.id)));row.addView(edit);row.setOnClickListener(v->startActivity(new Intent(this,SeriesDetailActivity.class).putExtra("seriesId",series.id)));content.addView(row);Ui.gap(content,10);}
        if(shown==0){TextView empty=Ui.muted(this,q.isEmpty()?(kind==BookSeries.BOOKS?"Создайте первую книжную серию":"Создайте первый журнал"):"Ничего не найдено",15);empty.setGravity(Gravity.CENTER);content.addView(empty,new LinearLayout.LayoutParams(-1,Ui.dp(this,180)));}}
    private void create(){startActivity(new Intent(this,SeriesEditorActivity.class).putExtra("kind",kind));}
    private static String booksWord(int n){int x=n%100,y=x%10;return x>10&&x<20?"книг":y==1?"книга":y>=2&&y<=4?"книги":"книг";}
    private static String issuesWord(int n){int x=n%100,y=x%10;return x>10&&x<20?"выпусков":y==1?"выпуск":y>=2&&y<=4?"выпуска":"выпусков";}
}
