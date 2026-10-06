package ru.homebooks.library;

import android.content.Intent;
import android.graphics.*;
import android.os.Bundle;
import android.text.*;
import android.view.*;
import android.widget.*;
import androidx.activity.ComponentActivity;
import java.util.*;

public final class BookTypesActivity extends ComponentActivity {
    private LibraryApp app;
    private LinearLayout content;
    private EditText search;
    private List<BookType> all=new ArrayList<>();
    private int request;

    @Override public void onCreate(Bundle state){
        super.onCreate(state);app=(LibraryApp)getApplication();
        LinearLayout root=Ui.column(this);root.setBackgroundColor(Ui.PAPER);setContentView(root);Ui.insets(root);
        root.addView(Ui.toolbar(this,"Типы книг","menu",()->Navigation.show(this,false)));
        LinearLayout controls=Ui.column(this);controls.setPadding(Ui.dp(this,16),0,Ui.dp(this,16),0);root.addView(controls);search=Ui.search(this,"Найти тип");controls.addView(search);Ui.gap(controls,8);
        ScrollView scroll=new ScrollView(this);scroll.setFillViewport(true);root.addView(scroll,new LinearLayout.LayoutParams(-1,0,1));content=Ui.column(this);content.setPadding(Ui.dp(this,16),0,Ui.dp(this,16),Ui.dp(this,8));scroll.addView(content);
        LinearLayout footer=Ui.column(this);footer.setPadding(Ui.dp(this,16),Ui.dp(this,8),Ui.dp(this,16),Ui.dp(this,8));footer.addView(Ui.primary(this,"＋ Тип книги",()->startActivity(new Intent(this,BookTypeEditorActivity.class))),new LinearLayout.LayoutParams(-1,-2));root.addView(footer);
        search.addTextChangedListener(new TextWatcher(){public void beforeTextChanged(CharSequence s,int a,int b,int c){}public void afterTextChanged(Editable e){}public void onTextChanged(CharSequence s,int a,int b,int c){render();}});
    }
    @Override protected void onResume(){super.onResume();refresh();}
    private void refresh(){int token=++request;app.io.execute(()->{try{List<BookType> types=app.store.bookTypes();runOnUiThread(()->{if(!isDestroyed()&&token==request){all=types;render();}});}catch(Exception e){runOnUiThread(()->Toast.makeText(this,"Не удалось загрузить типы книг",Toast.LENGTH_LONG).show());}});}
    private void render(){content.removeAllViews();String q=search.getText().toString().trim().toLowerCase(Locale.ROOT);List<BookType> shown=new ArrayList<>();for(BookType type:all)if(type.name.toLowerCase(Locale.ROOT).contains(q))shown.add(type);
        if(shown.isEmpty()){TextView empty=Ui.muted(this,q.isEmpty()?"Создайте первый тип книги":"Типы не найдены",14);empty.setGravity(Gravity.CENTER);content.addView(empty,new LinearLayout.LayoutParams(-1,Ui.dp(this,160)));return;}
        for(BookType type:shown){
            LinearLayout row=new LinearLayout(this);row.setGravity(Gravity.CENTER_VERTICAL);row.setMinimumHeight(Ui.dp(this,66));row.setPadding(Ui.dp(this,12),Ui.dp(this,8),0,Ui.dp(this,8));row.setBackground(new android.graphics.drawable.RippleDrawable(android.content.res.ColorStateList.valueOf(0x14735081),Ui.round(Color.TRANSPARENT,this),null));
            ImageView icon=new ImageView(this);icon.setImageDrawable(new Ui.Symbol(type.icon,type.color));icon.setBackground(Ui.round(0xFFF1ECF3,this));Ui.pad(icon,9);row.addView(icon,new LinearLayout.LayoutParams(Ui.dp(this,42),Ui.dp(this,42)));
            LinearLayout words=Ui.column(this);words.setPadding(Ui.dp(this,12),0,0,0);LinearLayout nameLine=new LinearLayout(this);nameLine.setGravity(Gravity.CENTER_VERTICAL);View dot=new View(this);dot.setBackground(Ui.round(type.color,this));nameLine.addView(dot,new LinearLayout.LayoutParams(Ui.dp(this,9),Ui.dp(this,9)));TextView name=Ui.text(this,type.name,16);name.setPadding(Ui.dp(this,8),0,0,0);nameLine.addView(name);words.addView(nameLine);Ui.gap(words,3);words.addView(Ui.muted(this,"Книг: "+type.bookIds.size(),13));row.addView(words,new LinearLayout.LayoutParams(0,-2,1));
            ImageButton edit=Ui.iconButton(this,"edit","Изменить тип «"+type.name+"»",()->startActivity(new Intent(this,BookTypeEditorActivity.class).putExtra("typeId",type.id)));row.addView(edit,new LinearLayout.LayoutParams(Ui.dp(this,48),Ui.dp(this,48)));
            row.setOnClickListener(v->startActivity(new Intent(this,MainActivity.class).putExtra("bookTypeId",type.id)));content.addView(row);Ui.divider(content);
        }
    }
}
