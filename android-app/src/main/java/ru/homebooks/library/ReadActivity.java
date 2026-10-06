package ru.homebooks.library;

import android.content.Intent;
import android.graphics.*;
import android.os.Bundle;
import android.text.*;
import android.view.*;
import android.widget.*;
import androidx.activity.ComponentActivity;
import java.util.*;

public final class ReadActivity extends ComponentActivity {
    private LibraryApp app;
    private EditText search;
    private TextView count,empty;
    private final List<Book> books=new ArrayList<>();
    private List<ReadingHistory.Row> rows=new ArrayList<>();
    private HistoryAdapter adapter;
    private int request,currentYear;
    @Override public void onCreate(Bundle state){
        super.onCreate(state);app=(LibraryApp)getApplication();currentYear=Calendar.getInstance().get(Calendar.YEAR);
        LinearLayout root=Ui.column(this);root.setBackgroundColor(Ui.PAPER);setContentView(root);Ui.insets(root);
        root.addView(Ui.toolbar(this,"Прочитано в "+currentYear,"menu",()->Navigation.show(this,false)));
        LinearLayout body=Ui.column(this);body.setPadding(Ui.dp(this,16),0,Ui.dp(this,16),0);root.addView(body,new LinearLayout.LayoutParams(-1,0,1));
        search=Ui.search(this,"Название, автор или ISBN");body.addView(search);
        count=Ui.muted(this,"",13);count.setPadding(0,Ui.dp(this,10),0,Ui.dp(this,10));body.addView(count);
        FrameLayout area=new FrameLayout(this);body.addView(area,new LinearLayout.LayoutParams(-1,0,1));
        ListView list=new ListView(this);list.setDivider(null);list.setVerticalScrollBarEnabled(false);area.addView(list,new FrameLayout.LayoutParams(-1,-1));
        empty=Ui.muted(this,"",15);empty.setGravity(Gravity.CENTER);Ui.pad(empty,24);area.addView(empty,new FrameLayout.LayoutParams(-1,-1));list.setEmptyView(empty);
        adapter=new HistoryAdapter();list.setAdapter(adapter);
        list.setOnItemClickListener((parent,view,position,id)->{Book book=rows.get(position).book;if(book!=null)startActivity(new Intent(this,AddBookActivity.class).putExtra("isbn",book.id));});
        search.addTextChangedListener(new TextWatcher(){public void beforeTextChanged(CharSequence s,int a,int c,int f){}public void afterTextChanged(Editable s){}public void onTextChanged(CharSequence s,int a,int b,int c){render();}});
        if(state!=null)search.setText(state.getString("query",""));
        app.covers.progress.observe(this,value->adapter.notifyDataSetChanged());
    }
    @Override protected void onResume(){super.onResume();int token=++request;app.io.execute(()->{try{List<Book> loaded=app.store.all();runOnUiThread(()->{if(isDestroyed()||token!=request)return;books.clear();books.addAll(loaded);render();});}catch(Exception e){runOnUiThread(()->{if(!isDestroyed())Toast.makeText(this,"Не удалось загрузить прочитанные книги",Toast.LENGTH_LONG).show();});}});}
    @Override protected void onSaveInstanceState(Bundle state){state.putString("query",search.getText().toString());super.onSaveInstanceState(state);}
    private void render(){rows=ReadingHistory.rows(books,search.getText().toString(),currentYear);int total=0;for(ReadingHistory.Row row:rows)if(row.book!=null)total++;count.setText("Книг: "+total+" · От новых к старым");empty.setText(search.length()==0?"В "+currentYear+" году пока нет прочитанных книг":"Ничего не найдено");adapter.notifyDataSetChanged();}
    private final class HistoryAdapter extends BaseAdapter {
        public int getCount(){return rows.size();}public Object getItem(int p){return rows.get(p);}public long getItemId(int p){return p;}
        public int getViewTypeCount(){return 2;}public int getItemViewType(int p){return rows.get(p).book==null?0:1;}
        public boolean areAllItemsEnabled(){return false;}public boolean isEnabled(int p){return rows.get(p).book!=null;}
        public View getView(int position,View reuse,ViewGroup parent){
            ReadingHistory.Row item=rows.get(position);
            if(item.book==null){TextView label=Ui.text(ReadActivity.this,item.heading,item.heading.endsWith(" год")?21:14);label.setTypeface(Typeface.create(item.heading.endsWith(" год")?"serif":"sans-serif-medium",Typeface.NORMAL));label.setTextColor(Ui.GREEN);label.setPadding(0,Ui.dp(ReadActivity.this,16),0,Ui.dp(ReadActivity.this,8));androidx.core.view.ViewCompat.setAccessibilityHeading(label,true);return label;}
            Book book=item.book;LinearLayout row=new LinearLayout(ReadActivity.this);row.setGravity(Gravity.CENTER_VERTICAL);row.setPadding(0,Ui.dp(ReadActivity.this,10),0,Ui.dp(ReadActivity.this,10));
            ImageView image=new ImageView(ReadActivity.this);image.setScaleType(ImageView.ScaleType.FIT_CENTER);image.setBackground(Ui.surface(ReadActivity.this,Ui.TINT,Ui.LINE,4));image.setClipToOutline(true);image.setImageDrawable(new Ui.Symbol("book",Ui.GREEN));image.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);row.addView(image,new LinearLayout.LayoutParams(Ui.dp(ReadActivity.this,42),Ui.dp(ReadActivity.this,60)));
            LinearLayout words=Ui.column(ReadActivity.this);words.setPadding(Ui.dp(ReadActivity.this,12),0,0,0);row.addView(words,new LinearLayout.LayoutParams(0,-2,1));
            TextView title=Ui.text(ReadActivity.this,book.title,15);title.setTypeface(Typeface.create("sans-serif-medium",Typeface.NORMAL));title.setMaxLines(2);title.setEllipsize(TextUtils.TruncateAt.END);words.addView(title);
            TextView author=Ui.muted(ReadActivity.this,book.author.isEmpty()?"Автор не указан":book.author,13);author.setMaxLines(1);author.setEllipsize(TextUtils.TruncateAt.END);words.addView(author);
            if(book.readOn.length()==10){TextView date=Ui.muted(ReadActivity.this,ReadingDate.label(book.readOn),13);words.addView(date);}
            app.io.execute(()->{BitmapFactory.Options options=new BitmapFactory.Options();options.inJustDecodeBounds=true;String path=app.store.cover(book.id).getAbsolutePath();BitmapFactory.decodeFile(path,options);options.inSampleSize=1;while(Math.max(options.outWidth,options.outHeight)/options.inSampleSize>Ui.dp(ReadActivity.this,120))options.inSampleSize*=2;options.inJustDecodeBounds=false;Bitmap bitmap=BitmapFactory.decodeFile(path,options);if(bitmap!=null)runOnUiThread(()->{if(isDestroyed()){bitmap.recycle();return;}image.setImageBitmap(bitmap);});});
            return row;
        }
    }
}
