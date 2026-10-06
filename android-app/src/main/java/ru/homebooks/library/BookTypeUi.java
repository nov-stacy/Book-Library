package ru.homebooks.library;

import android.app.*;
import android.text.*;
import android.view.*;
import android.widget.*;
import java.util.*;

final class BookTypeUi {
    interface Chosen {void apply(Long typeId);}

    static void members(Activity activity,long typeId,Runnable done){
        LibraryApp app=(LibraryApp)activity.getApplication();
        app.io.execute(()->{try{
            BookType type=app.store.bookType(typeId);List<Book> books=app.store.all();Map<String,BookType> labels=app.store.bookTypeLabels();
            activity.runOnUiThread(()->{
                if(activity.isDestroyed()||activity.isFinishing())return;
                if(type==null){Toast.makeText(activity,"Тип книг удалён",Toast.LENGTH_LONG).show();done.run();return;}
                showMembers(activity,type,books,labels,done);
            });
        }catch(Exception e){activity.runOnUiThread(()->{if(!activity.isDestroyed())Toast.makeText(activity,"Не удалось загрузить книги",Toast.LENGTH_LONG).show();});}});
    }

    private static void showMembers(Activity activity,BookType type,List<Book> books,Map<String,BookType> labels,Runnable done){
        LibraryApp app=(LibraryApp)activity.getApplication();Set<String> existing=new HashSet<>(type.bookIds);Collections.sort(books,BookSort.TITLE_ASC.comparator());List<SelectionList.Item> items=new ArrayList<>();
        for(Book book:books)if(!existing.contains(book.id)){BookType assigned=labels.get(book.id);String author=book.author.isEmpty()?"Автор не указан":book.author;String status=assigned==null?"без типа":"тип «"+assigned.name+"»";items.add(new SelectionList.Item(book.id,book.title,author+" · "+status,book.isbn));}
        SelectionList model=new SelectionList(items,Collections.emptySet());
        LinearLayout frame=Ui.column(activity);frame.setPadding(Ui.dp(activity,24),Ui.dp(activity,8),Ui.dp(activity,24),0);
        EditText search=Ui.search(activity,"Название, автор или ISBN");frame.addView(search);Ui.gap(frame,8);
        TextView count=Ui.muted(activity,"",13);frame.addView(count);Ui.gap(frame,8);
        FrameLayout area=new FrameLayout(activity);ListView list=new ListView(activity);list.setDivider(new android.graphics.drawable.ColorDrawable(Ui.LINE));list.setDividerHeight(Ui.dp(activity,1));list.setVerticalScrollBarEnabled(false);area.addView(list,new FrameLayout.LayoutParams(-1,-1));
        TextView empty=Ui.muted(activity,"Нет книг для добавления",14);empty.setGravity(Gravity.CENTER);area.addView(empty,new FrameLayout.LayoutParams(-1,-1));list.setEmptyView(empty);frame.addView(area,new LinearLayout.LayoutParams(-1,Math.min(Ui.dp(activity,330),activity.getResources().getDisplayMetrics().heightPixels/3)));
        BaseAdapter adapter=new BaseAdapter(){
            public int getCount(){return model.visible.size();}public Object getItem(int p){return model.visible.get(p);}public long getItemId(int p){return p;}
            public View getView(int p,View reuse,ViewGroup parent){SelectionList.Item item=model.visible.get(p);String label=item.title+"\n"+item.subtitle;CheckedTextView row=Ui.choice(activity,label,model.selected.contains(item.id));row.setTextSize(15);row.setBackground(new android.graphics.drawable.RippleDrawable(android.content.res.ColorStateList.valueOf(0x14735081),Ui.round(android.graphics.Color.TRANSPARENT,activity),null));row.setPadding(0,Ui.dp(activity,12),0,Ui.dp(activity,12));SpannableString text=new SpannableString(label);int start=item.title.length()+1;text.setSpan(new android.text.style.RelativeSizeSpan(.85f),start,text.length(),0);text.setSpan(new android.text.style.ForegroundColorSpan(Ui.MUTED),start,text.length(),0);row.setText(text);return row;}
        };
        list.setAdapter(adapter);Button[] add={null};Runnable update=()->{count.setText("Выбрано: "+model.selected.size());if(add[0]!=null){add[0].setText("Добавить · "+model.selected.size());add[0].setEnabled(!model.selected.isEmpty());}adapter.notifyDataSetChanged();};
        list.setOnItemClickListener((parent,view,index,id)->{model.toggle(index);update.run();});
        search.addTextChangedListener(new TextWatcher(){public void beforeTextChanged(CharSequence s,int a,int b,int c){}public void afterTextChanged(Editable e){}public void onTextChanged(CharSequence s,int a,int b,int c){model.filter(s.toString());update.run();}});
        AlertDialog dialog=new AlertDialog.Builder(activity).setTitle("Добавить книги в «"+type.name+"»").setView(frame).setNegativeButton("Отмена",null).setPositiveButton("Добавить",null).create();
        dialog.setOnShowListener(d->{add[0]=dialog.getButton(-1);update.run();add[0].setOnClickListener(v->{Set<String> snapshot=new LinkedHashSet<>(model.selected);if(snapshot.isEmpty())return;add[0].setEnabled(false);dialog.getButton(-2).setEnabled(false);dialog.setCancelable(false);list.setEnabled(false);search.setEnabled(false);app.io.execute(()->{try{Set<String> combined=new LinkedHashSet<>(app.store.bookTypeBookIds(type.id));combined.addAll(snapshot);app.store.setBookTypeBooks(type.id,combined);activity.runOnUiThread(()->{if(activity.isDestroyed())return;dialog.dismiss();done.run();});}catch(Exception e){activity.runOnUiThread(()->{if(activity.isDestroyed())return;add[0].setEnabled(true);dialog.getButton(-2).setEnabled(true);dialog.setCancelable(true);list.setEnabled(true);search.setEnabled(true);Toast.makeText(activity,"Не удалось добавить книги",Toast.LENGTH_LONG).show();});}});});});
        update.run();dialog.show();dialog.getWindow().setSoftInputMode(android.view.WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE|android.view.WindowManager.LayoutParams.SOFT_INPUT_STATE_ALWAYS_HIDDEN);
    }

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
                RadioButton radio=new RadioButton(activity);radio.setClickable(false);radio.setFocusable(false);radio.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);radio.setChecked(type.id<0?pending[0]==null:pending[0]!=null&&pending[0]==type.id);radio.setButtonTintList(android.content.res.ColorStateList.valueOf(Ui.GREEN));row.setDescendantFocusability(ViewGroup.FOCUS_BLOCK_DESCENDANTS);row.addView(radio);return row;
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
