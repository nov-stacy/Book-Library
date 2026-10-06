package ru.homebooks.library;

import android.app.*;
import android.content.Intent;
import android.graphics.Color;
import android.os.Bundle;
import android.text.InputFilter;
import android.view.*;
import android.widget.*;
import androidx.activity.ComponentActivity;
import java.util.*;

public final class SeriesEditorActivity extends ComponentActivity {
    private LibraryApp app;private long id=-1;private int kind;private EditText name,total;private Button books,periodical,save;private BookSeries editing;
    @Override public void onCreate(Bundle state){super.onCreate(state);app=(LibraryApp)getApplication();id=getIntent().getLongExtra("seriesId",-1);kind=getIntent().getIntExtra("kind",BookSeries.BOOKS);if(id<0)render();else app.io.execute(()->{try{BookSeries item=app.store.bookSeries(id);runOnUiThread(()->{if(item==null){Toast.makeText(this,"Серия уже удалена",Toast.LENGTH_LONG).show();finish();}else{editing=item;kind=item.kind;render();}});}catch(Exception e){runOnUiThread(()->finish());}});}
    private void render(){LinearLayout root=Ui.column(this);root.setBackgroundColor(Ui.PAPER);setContentView(root);Ui.insets(root);root.addView(Ui.toolbar(this,editing==null?(kind==BookSeries.BOOKS?"Новая серия":"Новый журнал"):kind==BookSeries.BOOKS?"Редактировать серию":"Редактировать журнал",this::finish));ScrollView scroll=new ScrollView(this);scroll.setFillViewport(true);root.addView(scroll,new LinearLayout.LayoutParams(-1,0,1));LinearLayout body=Ui.column(this);body.setPadding(Ui.dp(this,16),Ui.dp(this,8),Ui.dp(this,16),Ui.dp(this,24));scroll.addView(body);
        body.addView(Ui.eyebrow(this,"ВИД"));Ui.gap(body,8);LinearLayout tabs=new LinearLayout(this);books=kindButton("Книжная серия",BookSeries.BOOKS);periodical=kindButton("Журнал",BookSeries.PERIODICAL);tabs.addView(books,new LinearLayout.LayoutParams(0,Ui.dp(this,52),1));LinearLayout.LayoutParams pp=new LinearLayout.LayoutParams(0,Ui.dp(this,52),1);pp.setMarginStart(Ui.dp(this,8));tabs.addView(periodical,pp);body.addView(tabs);if(editing!=null){books.setEnabled(false);periodical.setEnabled(false);}styleKinds();Ui.gap(body,22);
        body.addView(Ui.eyebrow(this,"НАЗВАНИЕ"));Ui.gap(body,8);name=Ui.input(this,"");name.setSingleLine(true);name.setFilters(new InputFilter[]{new InputFilter.LengthFilter(200)});if(editing!=null)name.setText(editing.name);body.addView(name);Ui.gap(body,22);
        if(kind==BookSeries.BOOKS){body.addView(Ui.eyebrow(this,"ВСЕГО ЧАСТЕЙ"));Ui.gap(body,8);total=Ui.input(this,"");total.setSingleLine(true);total.setInputType(android.text.InputType.TYPE_CLASS_NUMBER);total.setFilters(new InputFilter[]{new InputFilter.LengthFilter(3)});if(editing!=null&&editing.totalParts!=null)total.setText(Integer.toString(editing.totalParts));body.addView(total);}
        LinearLayout footer=Ui.column(this);footer.setPadding(Ui.dp(this,16),Ui.dp(this,8),Ui.dp(this,16),Ui.dp(this,8));save=Ui.primary(this,"Сохранить",this::save);footer.addView(save);Ui.gap(footer,8);footer.addView(Ui.quietAction(this,"Отмена",this::finish));if(editing!=null){Ui.gap(footer,8);footer.addView(Ui.dangerAction(this,kind==BookSeries.BOOKS?"Удалить серию":"Удалить журнал",this::confirmDelete));}root.addView(footer);}
    private Button kindButton(String title,int value){return Ui.button(this,title,()->{kind=value;render();});}
    private void styleKinds(){styleKind(books,kind==BookSeries.BOOKS);styleKind(periodical,kind==BookSeries.PERIODICAL);}
    private void styleKind(Button button,boolean selected){button.setTextColor(selected?Color.WHITE:Ui.GREEN);button.setBackground(new android.graphics.drawable.RippleDrawable(android.content.res.ColorStateList.valueOf(0x22735081),Ui.surface(this,selected?Ui.GREEN:Color.WHITE,selected?Ui.GREEN:Ui.LINE,12),null));}
    private Integer total(){if(kind==BookSeries.PERIODICAL||total==null||total.getText().toString().trim().isEmpty())return null;try{int value=Integer.parseInt(total.getText().toString());if(value<1||value>999)throw new NumberFormatException();return value;}catch(NumberFormatException e){total.setError("Введите число от 1 до 999");return -1;}}
    private void save(){String value=name.getText().toString().trim();if(value.isEmpty()){name.setError("Введите название");return;}Integer count=total();if(count!=null&&count<0)return;save.setEnabled(false);app.io.execute(()->{try{if(editing==null)id=app.store.createBookSeries(value,kind,count);else app.store.updateBookSeries(id,value,count);long ready=id;runOnUiThread(()->{setResult(RESULT_OK,new Intent().putExtra("seriesId",ready).putExtra("seriesKind",kind));finish();});}catch(Exception e){runOnUiThread(()->{save.setEnabled(true);name.setError(e instanceof android.database.sqlite.SQLiteConstraintException?"Такое название уже используется":"Не удалось сохранить");});}});}
    private void confirmDelete(){new AlertDialog.Builder(this).setTitle(kind==BookSeries.BOOKS?"Удалить серию?":"Удалить журнал?").setMessage("Книги останутся в библиотеке без этой связи.").setNegativeButton("Отмена",null).setPositiveButton("Удалить",(d,w)->app.io.execute(()->{try{app.store.deleteBookSeries(id);runOnUiThread(()->{setResult(RESULT_OK);finish();});}catch(Exception e){runOnUiThread(()->Toast.makeText(this,"Не удалось удалить",Toast.LENGTH_LONG).show());}})).show();}
}
