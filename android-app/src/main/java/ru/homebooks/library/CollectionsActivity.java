package ru.homebooks.library;

import android.app.AlertDialog;
import android.content.Intent;
import android.os.Bundle;
import android.view.*;
import android.widget.*;
import androidx.activity.ComponentActivity;
import java.util.*;

public final class CollectionsActivity extends ComponentActivity {
    private LibraryApp app;
    private LinearLayout content;
    private int request,loadRequest;
    private EditText search;
    private Button sortButton;
    private int sort;
    private List<BookCollection> all=new ArrayList<>();
    private static final String[] SORTS={"По названию А–Я","По названию Я–А","Сначала больше книг","Сначала меньше книг","Сначала новые","Сначала старые"};
    @Override public void onCreate(Bundle state){
        super.onCreate(state);app=(LibraryApp)getApplication();
        LinearLayout root=Ui.column(this);root.setBackgroundColor(Ui.PAPER);setContentView(root);Ui.insets(root);
        root.addView(Ui.toolbar(this,"Коллекции","menu",()->Navigation.show(this,true)));
        sort=getSharedPreferences("library_preferences",MODE_PRIVATE).getInt("collection_sort",0);if(sort<0||sort>=SORTS.length)sort=0;
        LinearLayout controls=Ui.column(this);controls.setPadding(Ui.dp(this,16),0,Ui.dp(this,16),0);root.addView(controls);
        search=Ui.search(this,"Найти коллекцию");controls.addView(search);
        sortButton=Ui.textButton(this,SORTS[sort]+"  ▾",this::chooseSort);sortButton.setTextSize(14);sortButton.setGravity(Gravity.START|Gravity.CENTER_VERTICAL);sortButton.setTextColor(Ui.GREEN);controls.addView(sortButton);
        ScrollView scroll=new ScrollView(this);scroll.setFillViewport(true);root.addView(scroll,new LinearLayout.LayoutParams(-1,0,1));content=Ui.column(this);content.setPadding(Ui.dp(this,16),0,Ui.dp(this,16),Ui.dp(this,8));scroll.addView(content);
        LinearLayout footer=Ui.column(this);footer.setPadding(Ui.dp(this,16),Ui.dp(this,8),Ui.dp(this,16),Ui.dp(this,8));footer.addView(Ui.primary(this,"＋ Новая коллекция",()->CollectionUi.name(this,null,this::refresh)),new LinearLayout.LayoutParams(-1,-2));root.addView(footer);
        if(state!=null)search.setText(state.getString("query",""));
        search.addTextChangedListener(new android.text.TextWatcher(){public void beforeTextChanged(CharSequence s,int a,int c,int f){}public void afterTextChanged(android.text.Editable s){}public void onTextChanged(CharSequence s,int a,int b,int c){render();}});
    }
    @Override protected void onSaveInstanceState(Bundle state){state.putString("query",search.getText().toString());super.onSaveInstanceState(state);}
    private void chooseSort(){new AlertDialog.Builder(this).setTitle("Сортировка коллекций").setSingleChoiceItems(Ui.choices(this,SORTS,sort),sort,(d,index)->{sort=index;getSharedPreferences("library_preferences",MODE_PRIVATE).edit().putInt("collection_sort",sort).apply();sortButton.setText(SORTS[sort]+"  ▾");d.dismiss();render();}).setNegativeButton("Отмена",null).show();}
    @Override protected void onResume(){super.onResume();refresh();}
    private void refresh(){int token=++loadRequest;app.io.execute(()->{try{List<BookCollection> groups=app.store.collections();runOnUiThread(()->{if(isDestroyed()||token!=loadRequest)return;all=groups;render();});}catch(Exception e){runOnUiThread(()->Toast.makeText(this,"Не удалось загрузить коллекции",Toast.LENGTH_LONG).show());}});}
    private void render(){int token=++request;content.removeAllViews();
        List<BookCollection> groups=new ArrayList<>();String query=search.getText().toString().trim().toLowerCase(Locale.ROOT);
        for(BookCollection group:all)if(group.name.toLowerCase(Locale.ROOT).contains(query))groups.add(group);
        java.text.Collator collator=java.text.Collator.getInstance(new Locale("ru"));
        Collections.sort(groups,(a,b)->{
            int comparison;
            if(sort==4)return Long.compare(b.id,a.id);
            if(sort==5)return Long.compare(a.id,b.id);
            if(sort==2||sort==3){comparison=Integer.compare(a.isbns.size(),b.isbns.size());if(comparison!=0)return sort==2?-comparison:comparison;}
            comparison=collator.compare(a.name,b.name);return sort==1?-comparison:comparison;
        });
        if(groups.isEmpty()&&!query.isEmpty()){content.addView(Ui.muted(this,"Коллекции не найдены",14));return;}
        if(groups.isEmpty()){
            TextView empty=Ui.muted(this,"Коллекций пока нет",14);empty.setGravity(Gravity.CENTER);content.addView(empty,new LinearLayout.LayoutParams(-1,Ui.dp(this,180)));return;
        }
        for(BookCollection group:groups){
            LinearLayout card=Ui.column(this);Ui.pad(card,16);card.setBackground(Ui.surface(this,android.graphics.Color.WHITE,Ui.LINE,16));
            LinearLayout previewStrip=null;
            LinearLayout row=new LinearLayout(this);row.setGravity(Gravity.CENTER_VERTICAL);row.setBackground(new android.graphics.drawable.RippleDrawable(android.content.res.ColorStateList.valueOf(0x14735081),Ui.round(android.graphics.Color.TRANSPARENT,this),null));
            LinearLayout words=Ui.column(this);words.setPadding(0,Ui.dp(this,8),0,Ui.dp(this,8));TextView name=Ui.text(this,group.name,16);name.setTypeface(android.graphics.Typeface.create("sans-serif-medium",android.graphics.Typeface.NORMAL));name.setMaxLines(2);name.setEllipsize(android.text.TextUtils.TruncateAt.END);words.addView(name);Ui.gap(words,4);words.addView(Ui.muted(this,"Книг: "+group.isbns.size(),13));
            if(!group.isbns.isEmpty()){
                LinearLayout previews=new LinearLayout(this);previews.setGravity(Gravity.CENTER_VERTICAL);
                previews.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO_HIDE_DESCENDANTS);
                int shown=Math.min(4,group.isbns.size());
                for(int i=0;i<shown;i++){
                    ImageView cover=new ImageView(this);cover.setScaleType(ImageView.ScaleType.FIT_CENTER);
                    cover.setBackground(Ui.surface(this,Ui.TINT,Ui.LINE,4));cover.setClipToOutline(true);Ui.pad(cover,5);
                    cover.setImageDrawable(new Ui.Symbol("book",Ui.GREEN));
                    LinearLayout.LayoutParams cp=new LinearLayout.LayoutParams(Ui.dp(this,32),Ui.dp(this,46));cp.setMarginEnd(Ui.dp(this,6));previews.addView(cover,cp);
                    loadPreview(cover,group.isbns.get(i),token);
                }
                if(group.isbns.size()>shown){TextView extra=Ui.muted(this,"+"+(group.isbns.size()-shown),12);extra.setGravity(Gravity.CENTER);previews.addView(extra,new LinearLayout.LayoutParams(-2,Ui.dp(this,46)));}
                previewStrip=previews;
            }
            row.addView(words,new LinearLayout.LayoutParams(0,-2,1));row.setOnClickListener(v->startActivity(new Intent(this,MainActivity.class).putExtra("collectionId",group.id)));
            Button edit=Ui.editButton(this,"Изменить коллекцию «"+group.name+"»",()->actions(group));LinearLayout.LayoutParams editLayout=new LinearLayout.LayoutParams(-2,-2);editLayout.setMarginStart(Ui.dp(this,12));row.addView(edit,editLayout);card.addView(row,new LinearLayout.LayoutParams(-1,-2));if(previewStrip!=null){Ui.gap(card,8);card.addView(previewStrip);previewStrip.setOnClickListener(v->row.performClick());}content.addView(card,new LinearLayout.LayoutParams(-1,-2));Ui.gap(content,12);
        }
    }
    private void loadPreview(ImageView view,String isbn,int token){
        app.io.execute(()->{
            String path=app.store.cover(isbn).getAbsolutePath();
            android.graphics.BitmapFactory.Options options=new android.graphics.BitmapFactory.Options();options.inJustDecodeBounds=true;
            android.graphics.BitmapFactory.decodeFile(path,options);if(options.outWidth<=0||options.outHeight<=0)return;
            options.inSampleSize=1;int target=Ui.dp(this,64);
            while(Math.max(options.outWidth,options.outHeight)/options.inSampleSize>target*2)options.inSampleSize*=2;
            options.inJustDecodeBounds=false;android.graphics.Bitmap bitmap=android.graphics.BitmapFactory.decodeFile(path,options);
            if(bitmap!=null)runOnUiThread(()->{if(isDestroyed()||token!=request){bitmap.recycle();return;}view.setPadding(0,0,0,0);view.setImageBitmap(bitmap);});
        });
    }
    private void actions(BookCollection group){CollectionUi.edit(this,group,this::refresh,this::refresh);}
}
