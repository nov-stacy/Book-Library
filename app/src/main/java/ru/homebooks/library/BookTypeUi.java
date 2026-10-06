package ru.homebooks.library;

import android.app.*;
import android.text.*;
import android.view.*;
import android.widget.*;
import java.util.*;

final class BookTypeUi {
    interface Chosen {void apply(Long typeId);}

    static void choose(Activity activity,Long selected,Chosen chosen,Runnable create){
        LibraryApp app=(LibraryApp)activity.getApplication();
        app.io.execute(()->{try{List<BookType> types=app.store.bookTypes();activity.runOnUiThread(()->{if(!activity.isDestroyed()&&!activity.isFinishing())show(activity,types,selected,chosen,create);});}
            catch(Exception e){activity.runOnUiThread(()->Toast.makeText(activity,"Не удалось загрузить типы книг",Toast.LENGTH_LONG).show());}});
    }

    private static void show(Activity activity,List<BookType> types,Long selected,Chosen chosen,Runnable create){
        LinearLayout frame=Ui.column(activity);frame.setPadding(Ui.dp(activity,24),Ui.dp(activity,8),Ui.dp(activity,24),0);
        EditText search=Ui.search(activity,"Найти тип");frame.addView(search);Ui.gap(frame,8);
        List<BookType> all=new ArrayList<>(),visible=new ArrayList<>();all.add(new BookType(-1,"Без типа","tag",Ui.MUTED,Collections.emptyList()));all.addAll(types);visible.addAll(all);
        final Long[] pending={selected};
        ListView list=new ListView(activity);list.setDivider(new android.graphics.drawable.ColorDrawable(Ui.LINE));list.setDividerHeight(Ui.dp(activity,1));list.setVerticalScrollBarEnabled(false);
        BaseAdapter adapter=new BaseAdapter(){
            public int getCount(){return visible.size();}public Object getItem(int p){return visible.get(p);}public long getItemId(int p){return visible.get(p).id;}
            public View getView(int p,View reuse,ViewGroup parent){
                BookType type=visible.get(p);LinearLayout row=new LinearLayout(activity);row.setGravity(Gravity.CENTER_VERTICAL);row.setMinimumHeight(Ui.dp(activity,58));row.setPadding(0,Ui.dp(activity,8),0,Ui.dp(activity,8));
                ImageView icon=new ImageView(activity);icon.setImageDrawable(new Ui.Symbol(type.icon,type.id<0?Ui.MUTED:type.color));icon.setContentDescription(null);row.addView(icon,new LinearLayout.LayoutParams(Ui.dp(activity,34),Ui.dp(activity,34)));
                LinearLayout words=Ui.column(activity);words.setPadding(Ui.dp(activity,12),0,0,0);words.addView(Ui.text(activity,type.name,15));if(type.id>=0)words.addView(Ui.muted(activity,"Книг: "+type.bookIds.size(),12));row.addView(words,new LinearLayout.LayoutParams(0,-2,1));
                RadioButton radio=new RadioButton(activity);radio.setClickable(false);radio.setChecked(type.id<0?pending[0]==null:pending[0]!=null&&pending[0]==type.id);radio.setButtonTintList(android.content.res.ColorStateList.valueOf(Ui.GREEN));row.addView(radio);return row;
            }
        };
        list.setAdapter(adapter);frame.addView(list,new LinearLayout.LayoutParams(-1,Math.min(Ui.dp(activity,330),activity.getResources().getDisplayMetrics().heightPixels/3)));
        Button add=Ui.quietAction(activity,"Создать тип книги","tag",create);Ui.gap(frame,12);frame.addView(add,new LinearLayout.LayoutParams(-1,-2));
        AlertDialog dialog=new AlertDialog.Builder(activity).setTitle("Тип книги").setView(frame).setNegativeButton("Отмена",null).setPositiveButton("Сохранить",null).create();
        list.setOnItemClickListener((parent,view,index,id)->{BookType type=visible.get(index);pending[0]=type.id<0?null:type.id;adapter.notifyDataSetChanged();});
        search.addTextChangedListener(new TextWatcher(){public void beforeTextChanged(CharSequence s,int a,int b,int c){}public void afterTextChanged(Editable e){}public void onTextChanged(CharSequence s,int a,int b,int c){String q=s.toString().trim().toLowerCase(Locale.ROOT);visible.clear();for(BookType type:all)if(type.name.toLowerCase(Locale.ROOT).contains(q))visible.add(type);adapter.notifyDataSetChanged();}});
        add.setOnClickListener(v->{dialog.dismiss();create.run();});
        dialog.setOnShowListener(d->dialog.getButton(-1).setOnClickListener(v->{chosen.apply(pending[0]);dialog.dismiss();}));dialog.show();
    }
}
