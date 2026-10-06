package ru.homebooks.library;

import android.app.*;
import android.text.InputFilter;
import android.widget.*;
import java.util.*;

final class CollectionUi {
    static void edit(Activity activity,BookCollection collection,Runnable changed,Runnable deleted){
        LinearLayout frame=Ui.column(activity);frame.setPadding(Ui.dp(activity,24),Ui.dp(activity,16),Ui.dp(activity,24),Ui.dp(activity,16));
        TextView title=Ui.heading(activity,collection.name);title.setTextSize(21);frame.addView(title);Ui.gap(frame,6);
        frame.addView(Ui.muted(activity,"Книг в коллекции: "+collection.isbns.size(),13));Ui.gap(frame,20);
        AlertDialog dialog=new AlertDialog.Builder(activity).setTitle("Редактировать коллекцию").setView(frame).setNegativeButton("Закрыть",null).create();
        frame.addView(Ui.action(activity,"Изменить название",()->{dialog.dismiss();name(activity,collection,changed);}),new LinearLayout.LayoutParams(-1,-2));Ui.gap(frame,16);Ui.divider(frame);Ui.gap(frame,16);
        Button remove=Ui.dangerAction(activity,"Удалить коллекцию",()->{dialog.dismiss();delete(activity,collection,deleted);});
        frame.addView(remove,new LinearLayout.LayoutParams(-1,-2));dialog.show();
    }
    private static void delete(Activity activity,BookCollection collection,Runnable done){
        LibraryApp app=(LibraryApp)activity.getApplication();
        AlertDialog dialog=new AlertDialog.Builder(activity).setTitle("Удалить коллекцию?")
            .setMessage("«"+collection.name+"» будет удалена. Книги останутся в библиотеке.")
            .setNegativeButton("Отмена",null).setPositiveButton("Удалить",null).create();
        dialog.setOnShowListener(d->dialog.getButton(-1).setOnClickListener(v->{
            dialog.getButton(-1).setEnabled(false);dialog.getButton(-2).setEnabled(false);dialog.setCancelable(false);
            app.io.execute(()->{try{app.store.deleteCollection(collection.id);activity.runOnUiThread(()->{if(activity.isDestroyed())return;dialog.dismiss();done.run();});}
                catch(Exception e){activity.runOnUiThread(()->{if(activity.isDestroyed())return;dialog.getButton(-1).setEnabled(true);dialog.getButton(-2).setEnabled(true);dialog.setCancelable(true);Toast.makeText(activity,"Не удалось удалить коллекцию. Попробуйте ещё раз.",Toast.LENGTH_LONG).show();});}});
        }));dialog.show();
    }
    interface Named {void created(long id,String name);}
    static void name(Activity activity,BookCollection collection,Runnable done){name(activity,collection,(id,text)->done.run());}
    private static void name(Activity activity,BookCollection collection,Named done){
        LibraryApp app=(LibraryApp)activity.getApplication();EditText input=Ui.input(activity,"Название коллекции");input.setSingleLine(true);input.setFilters(new InputFilter[]{new InputFilter.LengthFilter(200)});
        if(collection!=null){input.setText(collection.name);input.setSelection(input.length());}
        LinearLayout frame=Ui.column(activity);frame.setPadding(Ui.dp(activity,24),Ui.dp(activity,16),Ui.dp(activity,24),Ui.dp(activity,16));frame.addView(input);
        AlertDialog dialog=new AlertDialog.Builder(activity).setTitle(collection==null?"Новая коллекция":"Название коллекции").setView(frame).setNegativeButton("Отмена",null).setPositiveButton("Сохранить",null).create();
        dialog.setOnShowListener(d->dialog.getButton(-1).setOnClickListener(v->{
            String text=input.getText().toString().trim();if(text.isEmpty()){input.setError("Введите название");return;}
            dialog.getButton(-1).setEnabled(false);dialog.getButton(-2).setEnabled(false);dialog.setCancelable(false);input.setEnabled(false);
            app.io.execute(()->{try{long id;if(collection==null)id=app.store.createCollection(text);else{app.store.renameCollection(collection.id,text);id=collection.id;}
                activity.runOnUiThread(()->{if(activity.isDestroyed())return;dialog.dismiss();done.created(id,text);});
            }catch(Exception e){activity.runOnUiThread(()->{if(activity.isDestroyed())return;dialog.getButton(-1).setEnabled(true);dialog.getButton(-2).setEnabled(true);dialog.setCancelable(true);input.setEnabled(true);input.setError(e instanceof android.database.sqlite.SQLiteConstraintException?"Коллекция с таким названием уже есть":"Не удалось сохранить коллекцию");});}});
        }));dialog.show();
    }
    static void members(Activity activity,long id,Runnable done){
        LibraryApp app=(LibraryApp)activity.getApplication();
        app.io.execute(()->{try{
            List<Book> books=app.store.all();Collections.sort(books,BookSort.TITLE_ASC.comparator());
            Map<String,String> labels=app.store.collectionLabels();
            List<SelectionList.Item> items=new ArrayList<>();for(Book book:books)items.add(new SelectionList.Item(book.id,book.title,book.author+" · "+BookCollection.label(labels,book.id),book.isbn));
            Set<String> selected=new LinkedHashSet<>(app.store.collectionIsbns(id));
            activity.runOnUiThread(()->{
                if(activity.isDestroyed()||activity.isFinishing())return;
                if(items.isEmpty()){new AlertDialog.Builder(activity).setTitle("Библиотека пока пуста").setMessage("Сначала добавьте книги в разделе «Книги».").setPositiveButton("Понятно",null).show();return;}
                pick(activity,"Книги в коллекции","Название, автор или ISBN",items,selected,values->app.store.setCollectionBooks(id,values),done);
            });
        }catch(Exception e){error(activity,"Не удалось загрузить книги");}});
    }
    static void forBook(Activity activity,String isbn,Runnable done){chooseCollections(activity,isbn,null,null,done);}
    static void forDraft(Activity activity,Set<String> selected,Save chosen,Runnable done){chooseCollections(activity,null,selected,chosen,done);}
    private static void chooseCollections(Activity activity,String isbn,Set<String> draft,Save chosen,Runnable done){
        LibraryApp app=(LibraryApp)activity.getApplication();
        app.io.execute(()->{try{
            List<SelectionList.Item> items=new ArrayList<>();items.add(new SelectionList.Item("","Без коллекции","",""));Set<String> selected=new LinkedHashSet<>();
            for(BookCollection group:app.store.collections()){String id=Long.toString(group.id);items.add(new SelectionList.Item(id,group.name,"Книг: "+group.isbns.size(),""));if(draft!=null?draft.contains(id):group.isbns.contains(isbn))selected.add(id);}
            activity.runOnUiThread(()->{
                if(activity.isDestroyed()||activity.isFinishing())return;
                if(selected.isEmpty())selected.add("");
                pick(activity,"Коллекция книги","Поиск коллекции",items,selected,values->{if(chosen!=null){Set<String> result=new LinkedHashSet<>(values);result.remove("");activity.runOnUiThread(()->chosen.apply(result));}else{List<Long> ids=new ArrayList<>();for(String value:values)if(!value.isEmpty())ids.add(Long.parseLong(value));app.store.setBookCollections(isbn,ids);}},done,true);
            });
        }catch(Exception e){error(activity,"Не удалось загрузить коллекции");}});
    }
    static void resolvePending(Activity activity,Runnable changed){
        LibraryApp app=(LibraryApp)activity.getApplication();
        app.io.execute(()->{try{
            Set<String> pending=app.store.pendingCollectionBooks();if(pending.isEmpty())return;
            String id=pending.iterator().next();Book book=app.store.find(id);if(book==null)return;
            List<BookCollection> current=new ArrayList<>();
            for(BookCollection group:app.store.collections())if(group.isbns.contains(id))current.add(group);
            if(current.size()<2)return;
            activity.runOnUiThread(()->{
                if(activity.isDestroyed()||activity.isFinishing())return;
                showCollectionConflict(activity,book,current,()->{changed.run();resolvePending(activity,changed);});
            });
        }catch(Exception e){error(activity,"Не удалось загрузить коллекции книги");}});
    }
    private static void showCollectionConflict(Activity activity,Book book,List<BookCollection> current,Runnable done){
        LibraryApp app=(LibraryApp)activity.getApplication();
        LinearLayout frame=Ui.column(activity);frame.setPadding(Ui.dp(activity,24),Ui.dp(activity,8),Ui.dp(activity,24),0);
        TextView title=Ui.heading(activity,book.title);title.setTextSize(20);frame.addView(title);
        if(!book.author.isEmpty()){Ui.gap(frame,8);frame.addView(Ui.muted(activity,book.author,15));}
        Ui.gap(frame,16);frame.addView(Ui.muted(activity,"Сейчас книга находится в этих коллекциях. Выберите, в какой её оставить:",15));Ui.gap(frame,12);
        RadioGroup choices=new RadioGroup(activity);choices.setOrientation(LinearLayout.VERTICAL);
        for(int i=0;i<current.size();i++){
            RadioButton row=new RadioButton(activity);row.setId(android.view.View.generateViewId());row.setTag(current.get(i).id);row.setText(current.get(i).name);
            row.setTextSize(16);row.setTextColor(Ui.INK);row.setButtonTintList(android.content.res.ColorStateList.valueOf(Ui.GREEN));
            row.setGravity(android.view.Gravity.CENTER_VERTICAL);row.setMinHeight(Ui.dp(activity,56));row.setPadding(Ui.dp(activity,8),Ui.dp(activity,12),Ui.dp(activity,8),Ui.dp(activity,12));
            row.setBackground(new android.graphics.drawable.RippleDrawable(android.content.res.ColorStateList.valueOf(0x14735081),Ui.round(android.graphics.Color.TRANSPARENT,activity),null));
            choices.addView(row,new RadioGroup.LayoutParams(-1,-2));
        }
        ScrollView scroll=new ScrollView(activity);scroll.addView(choices);scroll.setFillViewport(false);
        frame.addView(scroll,new LinearLayout.LayoutParams(-1,Math.min(Ui.dp(activity,Math.min(current.size(),4)*68),activity.getResources().getDisplayMetrics().heightPixels/3)));
        Ui.gap(frame,12);frame.addView(Ui.muted(activity,"После сохранения книга останется только в выбранной коллекции.",13));Ui.gap(frame,8);
        AlertDialog dialog=new AlertDialog.Builder(activity).setTitle("Выберите коллекцию").setView(frame)
            .setNegativeButton("Позже",null).setPositiveButton("Сохранить",null).create();
        dialog.setOnShowListener(d->{
            Button save=dialog.getButton(-1);save.setEnabled(false);
            choices.setOnCheckedChangeListener((group,checked)->save.setEnabled(checked!=-1));
            save.setOnClickListener(v->{
                RadioButton selected=choices.findViewById(choices.getCheckedRadioButtonId());if(selected==null)return;
                long collectionId=(Long)selected.getTag();save.setEnabled(false);dialog.getButton(-2).setEnabled(false);dialog.setCancelable(false);
                for(int i=0;i<choices.getChildCount();i++)choices.getChildAt(i).setEnabled(false);
                app.io.execute(()->{try{
                    app.store.setBookCollections(book.id,Collections.singletonList(collectionId));
                    activity.runOnUiThread(()->{if(activity.isDestroyed()||activity.isFinishing())return;dialog.dismiss();done.run();});
                }catch(Exception e){activity.runOnUiThread(()->{
                    if(activity.isDestroyed()||activity.isFinishing())return;
                    save.setEnabled(true);dialog.getButton(-2).setEnabled(true);dialog.setCancelable(true);
                    for(int i=0;i<choices.getChildCount();i++)choices.getChildAt(i).setEnabled(true);
                    Toast.makeText(activity,"Не удалось сохранить выбор. Попробуйте ещё раз.",Toast.LENGTH_LONG).show();
                });}});
            });
        });dialog.show();
    }
    interface Save {void apply(Set<String> selected);}
    private static void error(Activity activity,String text){activity.runOnUiThread(()->{if(!activity.isDestroyed())Toast.makeText(activity,text,Toast.LENGTH_LONG).show();});}
    private static void pick(Activity activity,String title,String hint,List<SelectionList.Item> items,Set<String> chosen,Save save,Runnable done){
        pick(activity,title,hint,items,chosen,save,done,false);
    }
    private static void pick(Activity activity,String title,String hint,List<SelectionList.Item> items,Set<String> chosen,Save save,Runnable done,boolean allowCreate){
        LibraryApp app=(LibraryApp)activity.getApplication();SelectionList model=new SelectionList(items,chosen);
        boolean conflict=allowCreate&&chosen.size()>1;if(conflict)model.selected.clear();
        String previous="";for(SelectionList.Item item:items)if(chosen.contains(item.id)&&!item.id.isEmpty())previous=previous.isEmpty()?item.title:previous+", "+item.title;
        final String previousName=previous;
        LinearLayout frame=Ui.column(activity);frame.setPadding(Ui.dp(activity,24),Ui.dp(activity,8),Ui.dp(activity,24),0);
        EditText search=Ui.search(activity,hint);frame.addView(search,new LinearLayout.LayoutParams(-1,-2));Ui.gap(frame,8);
        TextView count=Ui.muted(activity,"",13);frame.addView(count);Ui.gap(frame,8);
        android.widget.FrameLayout area=new android.widget.FrameLayout(activity);
        ListView list=new ListView(activity);list.setDivider(new android.graphics.drawable.ColorDrawable(Ui.LINE));list.setDividerHeight(Ui.dp(activity,1));list.setVerticalScrollBarEnabled(false);area.addView(list,new android.widget.FrameLayout.LayoutParams(-1,-1));
        TextView empty=Ui.muted(activity,items.isEmpty()&&allowCreate?"Создайте первую коллекцию":"Ничего не найдено",14);empty.setGravity(android.view.Gravity.CENTER);area.addView(empty,new android.widget.FrameLayout.LayoutParams(-1,-1));list.setEmptyView(empty);
        frame.addView(area,new LinearLayout.LayoutParams(-1,Math.min(Ui.dp(activity,320),activity.getResources().getDisplayMetrics().heightPixels/3)));
        BaseAdapter adapter=new BaseAdapter(){
            public int getCount(){return model.visible.size();}public Object getItem(int p){return model.visible.get(p);}public long getItemId(int p){return p;}
            public android.view.View getView(int p,android.view.View reuse,android.view.ViewGroup parent){
                SelectionList.Item item=model.visible.get(p);String label=item.title+(item.subtitle.isEmpty()?"":"\n"+item.subtitle);
                CheckedTextView row=Ui.choice(activity,label,model.selected.contains(item.id));
                if(allowCreate){android.util.TypedValue indicator=new android.util.TypedValue();activity.getTheme().resolveAttribute(android.R.attr.listChoiceIndicatorSingle,indicator,true);if(indicator.resourceId!=0)row.setCheckMarkDrawable(indicator.resourceId);row.setCheckMarkTintList(android.content.res.ColorStateList.valueOf(Ui.GREEN));}
                row.setTextSize(15);row.setBackground(new android.graphics.drawable.RippleDrawable(android.content.res.ColorStateList.valueOf(0x14735081),Ui.round(android.graphics.Color.TRANSPARENT,activity),null));row.setPadding(0,Ui.dp(activity,12),0,Ui.dp(activity,12));
                android.text.SpannableString text=new android.text.SpannableString(label);
                if(!item.subtitle.isEmpty()){int start=item.title.length()+1;text.setSpan(new android.text.style.RelativeSizeSpan(.85f),start,text.length(),0);text.setSpan(new android.text.style.ForegroundColorSpan(Ui.MUTED),start,text.length(),0);}row.setText(text);return row;
            }
        };
        Button[] saveButton={null};
        Runnable update=()->{
            if(allowCreate){
                String next="";for(SelectionList.Item item:model.all)if(model.selected.contains(item.id))next=item.title;
                String note=conflict&&model.selected.isEmpty()?"Ранее: "+previousName+". Выберите, где оставить книгу.":
                    !model.selected.equals(chosen)&&!next.isEmpty()?(next.equals("Без коллекции")?"Книга останется в библиотеке без коллекции.":previousName.isEmpty()?"Книга будет добавлена в «"+next+"».":"Переместить из «"+previousName+"» в «"+next+"»."):"";
                count.setText(note);count.setVisibility(note.isEmpty()?android.view.View.GONE:android.view.View.VISIBLE);
                if(saveButton[0]!=null)saveButton[0].setEnabled(model.selected.size()==1);
            }else count.setText("Выбрано: "+model.selected.size()+" · Найдено: "+model.visible.size()+"\nКниги из других коллекций будут перемещены сюда.");
            adapter.notifyDataSetChanged();
        };
        list.setAdapter(adapter);list.setOnItemClickListener((parent,view,index,itemId)->{if(allowCreate){model.selected.clear();model.selected.add(model.visible.get(index).id);}else model.toggle(index);update.run();});
        search.addTextChangedListener(new android.text.TextWatcher(){public void beforeTextChanged(CharSequence s,int start,int count,int after){}public void onTextChanged(CharSequence s,int start,int before,int count){model.filter(s.toString());update.run();}public void afterTextChanged(android.text.Editable s){}});update.run();
        Button create=Ui.action(activity,"Создать коллекцию",()->name(activity,null,(id,text)->{
            model.all.add(new SelectionList.Item(Long.toString(id),text,"Книг: 0",""));Collections.sort(model.all,(a,b)->a.id.isEmpty()?-1:b.id.isEmpty()?1:a.title.compareToIgnoreCase(b.title));model.selected.clear();model.selected.add(Long.toString(id));
            empty.setText("Ничего не найдено");search.setText("");model.filter("");update.run();
        }));if(allowCreate){Ui.gap(frame,12);frame.addView(create,new LinearLayout.LayoutParams(-1,-2));}
        AlertDialog dialog=new AlertDialog.Builder(activity).setTitle(title).setView(frame).setNegativeButton("Отмена",null).setPositiveButton("Сохранить",null).create();
        dialog.setOnShowListener(d->{saveButton[0]=dialog.getButton(-1);update.run();dialog.getButton(-1).setOnClickListener(v->{
            if(allowCreate&&model.selected.size()!=1)return;
            Set<String> snapshot=new LinkedHashSet<>(model.selected);dialog.getButton(-1).setEnabled(false);dialog.getButton(-2).setEnabled(false);dialog.setCancelable(false);list.setEnabled(false);search.setEnabled(false);create.setEnabled(false);
            app.io.execute(()->{try{save.apply(snapshot);activity.runOnUiThread(()->{if(activity.isDestroyed())return;dialog.dismiss();done.run();});}
                catch(Exception e){activity.runOnUiThread(()->{if(activity.isDestroyed())return;dialog.getButton(-1).setEnabled(true);dialog.getButton(-2).setEnabled(true);dialog.setCancelable(true);list.setEnabled(true);search.setEnabled(true);create.setEnabled(true);Toast.makeText(activity,"Не удалось сохранить выбор. Попробуйте ещё раз.",Toast.LENGTH_LONG).show();});}});
        });});dialog.getWindow().setSoftInputMode(android.view.WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE|android.view.WindowManager.LayoutParams.SOFT_INPUT_STATE_ALWAYS_HIDDEN);dialog.show();
    }
}
