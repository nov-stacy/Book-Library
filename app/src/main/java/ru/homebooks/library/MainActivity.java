package ru.homebooks.library;

import android.content.Intent;
import android.graphics.*;
import android.net.Uri;
import android.os.Bundle;
import android.text.*;
import android.view.*;
import android.widget.*;
import androidx.activity.ComponentActivity;
import androidx.activity.OnBackPressedCallback;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.*;

public final class MainActivity extends ComponentActivity {
    private LibraryApp app;
    private long collectionId=-1;
    private BookCollection currentCollection;
    private TextView screenHeading;
    private TextView count, sortLabel;
    private EditText search;
    private BookAdapter adapter;
    private LinearLayout empty;
    private TextView emptyTitle, emptyNote;
    private int request;
    private BookSort sortOrder;
    private ReadingStatus statusFilter;
    private Long typeFilterId;
    private boolean withoutCollection, withoutCover,withoutType;
    private Set<String> booksWithCovers=new HashSet<>();
    private final Set<String> loadingCovers=Collections.synchronizedSet(new HashSet<>());
    private final android.util.LruCache<String,Bitmap> coverCache=new android.util.LruCache<String,Bitmap>(8*1024*1024){@Override protected int sizeOf(String key,Bitmap value){return value.getAllocationByteCount();}};
    private Set<String> collectedBooks=new HashSet<>();
    private Map<String,String> collectionLabels=new HashMap<>();
    private Map<String,BookType> bookTypeLabels=new HashMap<>();
    private List<BookType> bookTypes=new ArrayList<>();
    private boolean offeredCollectionResolution;
    private Button filterButton,typeFilterButton;
    private LinearLayout receipt;
    private final Set<String> selected = new LinkedHashSet<>();
    private boolean selecting, deleting;
    private Button selectButton, primaryAction;
    private OnBackPressedCallback selectionBack;
    private final ActivityResultLauncher<Intent> add = registerForActivityResult(new ActivityResultContracts.StartActivityForResult(), result -> {
        receipt.removeAllViews();
        Intent data = result.getData();
        if (result.getResultCode() == RESULT_OK && data != null && data.hasExtra("savedTitle")) {
            receipt.removeAllViews();
            receipt.addView(new BookNotice(this, data.getStringExtra("savedIsbn"), data.getStringExtra("savedTitle"), data.getBooleanExtra("alreadySaved", false), false));
            View shown=receipt.getChildAt(0);
            int duration=5000;
            if(android.os.Build.VERSION.SDK_INT>=29){
                android.view.accessibility.AccessibilityManager accessibility=(android.view.accessibility.AccessibilityManager)getSystemService(ACCESSIBILITY_SERVICE);
                duration=accessibility.getRecommendedTimeoutMillis(duration,android.view.accessibility.AccessibilityManager.FLAG_CONTENT_TEXT);
            }
            receipt.postDelayed(()->{if(!isDestroyed()&&receipt.getChildCount()>0&&receipt.getChildAt(0)==shown)receipt.removeAllViews();},duration);
        }
    });
    @Override public void onCreate(Bundle state) {
        super.onCreate(state);withoutCover=state!=null&&state.getBoolean("withoutCover");withoutCollection=state!=null&&state.getBoolean("withoutCollection");withoutType=state!=null&&state.getBoolean("withoutType");app=(LibraryApp)getApplication();collectionId=getIntent().getLongExtra("collectionId",-1);if(state!=null&&state.containsKey("typeFilterId"))typeFilterId=state.getLong("typeFilterId");else if(getIntent().hasExtra("bookTypeId"))typeFilterId=getIntent().getLongExtra("bookTypeId",-1);
        sortOrder=BookSort.restore(getSharedPreferences("library_preferences",MODE_PRIVATE).getString("sort_order",null));
        LinearLayout root=Ui.column(this);root.setBackgroundColor(Ui.PAPER);setContentView(root);Ui.insets(root);
        LinearLayout top=Ui.toolbar(this,collectionId<0?"Библиотека":"Коллекция","menu",()->Navigation.show(this,collectionId>=0));
        screenHeading=(TextView)top.getChildAt(1);root.addView(top);
        if(collectionId>=0){
            Button edit=Ui.editButton(this,"Изменить коллекцию",()->{if(currentCollection!=null)CollectionUi.edit(this,currentCollection,this::refresh,this::finish);});
            LinearLayout.LayoutParams editLayout=new LinearLayout.LayoutParams(-2,-2);editLayout.setMarginStart(Ui.dp(this,12));top.addView(edit,editLayout);
        }
        LinearLayout body=Ui.column(this);body.setPadding(Ui.dp(this,16),0,Ui.dp(this,16),0);root.addView(body,new LinearLayout.LayoutParams(-1,0,1));
        search=Ui.search(this,"Название, автор или ISBN");body.addView(search);
        if(state!=null && state.containsKey("statusFilter")) statusFilter=ReadingStatus.fromId(state.getInt("statusFilter"));
        LinearLayout filters=new LinearLayout(this);filters.setGravity(Gravity.CENTER_VERTICAL);
        filterButton=Ui.button(this,"",this::chooseFilter);filterButton.setTextSize(14);
        filterButton.setSingleLine(true);filterButton.setEllipsize(TextUtils.TruncateAt.END);
        filterButton.setBackground(new android.graphics.drawable.InsetDrawable(filterButton.getBackground(),0,Ui.dp(this,8),0,Ui.dp(this,8)));
        filterButton.setPadding(Ui.dp(this,12),0,Ui.dp(this,12),0);
        filterButton.setGravity(Gravity.START|Gravity.CENTER_VERTICAL);
        Ui.Symbol dropdown=new Ui.Symbol("down",Color.WHITE);dropdown.setBounds(0,0,Ui.dp(this,14),Ui.dp(this,14));
        filterButton.setCompoundDrawablesRelative(null,null,dropdown,null);filterButton.setCompoundDrawablePadding(Ui.dp(this,8));
        filters.addView(filterButton,new LinearLayout.LayoutParams(0,Ui.dp(this,48),1));
        typeFilterButton=Ui.button(this,"",this::chooseTypeFilter);typeFilterButton.setTextSize(14);typeFilterButton.setSingleLine(true);typeFilterButton.setEllipsize(TextUtils.TruncateAt.END);typeFilterButton.setBackground(new android.graphics.drawable.InsetDrawable(typeFilterButton.getBackground(),0,Ui.dp(this,8),0,Ui.dp(this,8)));typeFilterButton.setPadding(Ui.dp(this,12),0,Ui.dp(this,12),0);typeFilterButton.setGravity(Gravity.START|Gravity.CENTER_VERTICAL);Ui.Symbol typeDropdown=new Ui.Symbol("down",Color.WHITE);typeDropdown.setBounds(0,0,Ui.dp(this,14),Ui.dp(this,14));typeFilterButton.setCompoundDrawablesRelative(null,null,typeDropdown,null);typeFilterButton.setCompoundDrawablePadding(Ui.dp(this,8));LinearLayout.LayoutParams typeFilterLayout=new LinearLayout.LayoutParams(0,Ui.dp(this,48),1);typeFilterLayout.setMarginStart(Ui.dp(this,8));filters.addView(typeFilterButton,typeFilterLayout);body.addView(filters);updateFilters();updateTypeFilter();
        LinearLayout label=new LinearLayout(this);label.setGravity(Gravity.CENTER_VERTICAL);
        count=Ui.muted(this,"Загрузка…",13);count.setSingleLine(true);count.setEllipsize(TextUtils.TruncateAt.END);
        label.addView(count,new LinearLayout.LayoutParams(0,-2,1));
        selectButton=Ui.textButton(this,"Выбрать",()->{if(!deleting){selecting=!selecting;selected.clear();updateSelection();}});
        selectButton.setTextSize(14);selectButton.setMinHeight(Ui.dp(this,48));selectButton.setMinimumHeight(Ui.dp(this,48));
        selectButton.setMinWidth(0);selectButton.setMinimumWidth(0);selectButton.setPadding(Ui.dp(this,8),Ui.dp(this,6),Ui.dp(this,8),Ui.dp(this,6));selectButton.setTextColor(Ui.GREEN);
        LinearLayout.LayoutParams selectLayout=new LinearLayout.LayoutParams(-2,-2);selectLayout.setMarginStart(Ui.dp(this,4));
        label.addView(selectButton,selectLayout);body.addView(label);
        FrameLayout area=new FrameLayout(this);ListView list=new ListView(this);list.setDivider(new android.graphics.drawable.ColorDrawable(Ui.LINE));list.setDividerHeight(Ui.dp(this,1));list.setVerticalScrollBarEnabled(false);area.addView(list,new FrameLayout.LayoutParams(-1,-1));
        ScrollView emptyScroll=new ScrollView(this);emptyScroll.setFillViewport(true);empty=Ui.column(this);empty.setGravity(Gravity.CENTER);emptyScroll.addView(empty);
        empty.addView(new Ui.ShelfArt(this),new LinearLayout.LayoutParams(-1,Ui.dp(this,170)));Ui.gap(empty,18);
        emptyTitle=Ui.text(this,"Здесь начинается ваша полка",21);emptyTitle.setTypeface(Typeface.create("serif",Typeface.NORMAL));emptyTitle.setGravity(Gravity.CENTER);empty.addView(emptyTitle);Ui.gap(empty,10);
        emptyNote=Ui.muted(this,"Добавьте первую книгу —\nостальные истории подтянутся следом.",15);emptyNote.setGravity(Gravity.CENTER);empty.addView(emptyNote);Ui.gap(empty,24);
        area.addView(emptyScroll,new FrameLayout.LayoutParams(-1,-1));list.setEmptyView(emptyScroll);body.addView(area,new LinearLayout.LayoutParams(-1,0,1));
        receipt=Ui.column(this);
        FrameLayout.LayoutParams receiptLayout=new FrameLayout.LayoutParams(-1,-2,Gravity.BOTTOM);receiptLayout.setMargins(Ui.dp(this,2),0,Ui.dp(this,2),Ui.dp(this,8));area.addView(receipt,receiptLayout);
        LinearLayout footer=new LinearLayout(this);footer.setGravity(Gravity.CENTER_VERTICAL);footer.setPadding(Ui.dp(this,16),Ui.dp(this,6),Ui.dp(this,16),Ui.dp(this,8));
        Button sortButton=Ui.textButton(this,"",this::chooseSort);
        sortButton.setTextSize(14);sortButton.setMinHeight(Ui.dp(this,48));sortButton.setMinimumHeight(Ui.dp(this,48));
        sortButton.setMinimumWidth(0);sortButton.setMinWidth(0);sortButton.setGravity(Gravity.START|Gravity.CENTER_VERTICAL);
        sortButton.setPadding(0,Ui.dp(this,8),Ui.dp(this,12),Ui.dp(this,8));sortButton.setTextColor(Ui.GREEN);
        sortLabel=sortButton;updateSortLabel();footer.addView(sortLabel,new LinearLayout.LayoutParams(0,-2,1));
        primaryAction=Ui.primary(this,"＋ Книга",()->{if(selecting)confirmDelete();else if(collectionId>=0)CollectionUi.members(this,collectionId,this::refresh);else add.launch(new Intent(this,AddBookActivity.class));});
        primaryAction.setTextSize(14);primaryAction.setMinHeight(Ui.dp(this,48));primaryAction.setMinimumHeight(Ui.dp(this,48));primaryAction.setPadding(Ui.dp(this,16),Ui.dp(this,8),Ui.dp(this,16),Ui.dp(this,8));
        footer.addView(primaryAction,new LinearLayout.LayoutParams(-2,-2));root.addView(footer);
        adapter=new BookAdapter();list.setAdapter(adapter);list.setOnItemClickListener((p,v,pos,id)->{String code=adapter.items.get(pos).id;if(selecting)toggle(code);else add.launch(new Intent(this,AddBookActivity.class).putExtra("isbn",code));});
        list.setOnItemLongClickListener((p,v,pos,id)->{if(deleting)return true;selecting=true;toggle(adapter.items.get(pos).id);return true;});
        selectionBack=new OnBackPressedCallback(false){public void handleOnBackPressed(){if(!deleting){selecting=false;selected.clear();updateSelection();}}};
        getOnBackPressedDispatcher().addCallback(this,selectionBack);
        search.addTextChangedListener(new TextWatcher(){public void beforeTextChanged(CharSequence s,int a,int c,int f){}public void afterTextChanged(Editable s){}public void onTextChanged(CharSequence s,int a,int b,int c){adapter.filter(s.toString());}});
        if(state!=null){search.setText(state.getString("query",""));selecting=state.getBoolean("selecting");ArrayList<String> saved=state.getStringArrayList("selected");if(saved!=null)selected.addAll(saved);}updateSelection();
        app.covers.changedCover.observe(this,id->{if(id==null||adapter==null)return;booksWithCovers.add(id);if(withoutCover)adapter.filter(search.getText().toString());else adapter.notifyDataSetChanged();});
    }
    @Override protected void onResume(){super.onResume();if(adapter!=null)refresh();}
    @Override protected void onSaveInstanceState(Bundle state){state.putBoolean("withoutCover",withoutCover);state.putBoolean("withoutCollection",withoutCollection);state.putBoolean("withoutType",withoutType);if(typeFilterId!=null)state.putLong("typeFilterId",typeFilterId);if(statusFilter!=null)state.putInt("statusFilter",statusFilter.id);state.putString("query",search.getText().toString());state.putBoolean("selecting",selecting);state.putStringArrayList("selected",new ArrayList<>(selected));super.onSaveInstanceState(state);}
    private void refresh(){int token=++request;app.io.execute(()->{
        try {Map<String,String> labels=app.store.collectionLabels();Map<String,BookType> typeLabels=app.store.bookTypeLabels();List<BookType> loadedTypes=app.store.bookTypes();Set<String> collected=labels.keySet();List<Book> books=app.store.all();BookCollection group=collectionId<0?null:app.store.collection(collectionId);
            if(collectionId>=0){Set<String> members=new HashSet<>(group==null?Collections.emptyList():group.isbns);for(Iterator<Book> it=books.iterator();it.hasNext();)if(!members.contains(it.next().id))it.remove();}
            Set<String> initialCovered=null;if(withoutCover){initialCovered=new HashSet<>();for(Book book:books){java.io.File image=app.store.cover(book.id);if(image.isFile()&&image.length()>0)initialCovered.add(book.id);}}Set<String> readyCovered=initialCovered;
            runOnUiThread(()->{if(isDestroyed()||token!=request)return;if(readyCovered!=null)booksWithCovers=readyCovered;if(collectionId>=0&&group==null){toast("Коллекция удалена");finish();return;}currentCollection=group;screenHeading.setText(group==null?"Библиотека":group.name);bookTypeLabels=typeLabels;bookTypes=loadedTypes;if(typeFilterId!=null){BookType current=null;for(BookType type:loadedTypes)if(type.id==typeFilterId)current=type;if(current==null){typeFilterId=null;withoutType=false;}else if(collectionId<0)screenHeading.setText(current.name);}collectionLabels=labels;collectedBooks=collected;updateTypeFilter();adapter.all=books;Set<String> present=new HashSet<>();for(Book b:books)present.add(b.id);selected.retainAll(present);updateSelection();adapter.filter(search.getText().toString());if(!offeredCollectionResolution&&collectionId<0){offeredCollectionResolution=true;CollectionUi.resolvePending(this,this::refresh);}});
            if(readyCovered==null){Set<String> covered=new HashSet<>();for(Book book:books){java.io.File image=app.store.cover(book.id);if(image.isFile()&&image.length()>0)covered.add(book.id);}runOnUiThread(()->{if(!isDestroyed()&&token==request){booksWithCovers=covered;if(withoutCover)adapter.filter(search.getText().toString());}});}}
        catch(Exception e){runOnUiThread(()->toast("Не удалось прочитать библиотеку"));}
    });}
    private void updateFilters(){
        String label=withoutCover?"Без обложки":withoutCollection?"Без коллекции":statusFilter==null?"Все книги":statusFilter.label;
        filterButton.setText(label);filterButton.setContentDescription("Фильтр: "+label+". Выбрать фильтр");
    }
    private void updateTypeFilter(){
        String label=withoutType?"Без типа":"Все типы";if(typeFilterId!=null)for(BookType type:bookTypes)if(type.id==typeFilterId){label=type.name;break;}
        typeFilterButton.setText(label);typeFilterButton.setContentDescription("Тип книги: "+label+". Выбрать фильтр");
    }
    private void chooseFilter(){
        ReadingStatus[] choices={null,ReadingStatus.READING,ReadingStatus.WANT,ReadingStatus.READ};
        String[] labels=collectionId<0?new String[]{"Все книги","Читаю","Хочу прочитать","Прочитано","Без обложки","Без коллекции"}:new String[]{"Все книги","Читаю","Хочу прочитать","Прочитано","Без обложки"};int selectedIndex=0;
        for(int i=0;i<choices.length;i++)if(choices[i]==statusFilter)selectedIndex=i;
        if(withoutCollection)selectedIndex=5;
        if(withoutCover)selectedIndex=4;
        new android.app.AlertDialog.Builder(this).setTitle("Показать книги")
            .setSingleChoiceItems(Ui.choices(this,labels,selectedIndex),selectedIndex,(dialog,index)->{withoutCover=index==4;withoutCollection=collectionId<0&&index==5;statusFilter=index<choices.length?choices[index]:null;updateFilters();adapter.filter(search.getText().toString());dialog.dismiss();})
            .setNegativeButton("Закрыть",null).show();
    }
    private void chooseTypeFilter(){
        List<BookType> choices=new ArrayList<>(bookTypes);String[] labels=new String[choices.size()+2];labels[0]="Все типы";labels[1]="Без типа";int selectedIndex=withoutType?1:0;
        for(int i=0;i<choices.size();i++){labels[i+2]=choices.get(i).name;if(typeFilterId!=null&&typeFilterId==choices.get(i).id)selectedIndex=i+2;}
        new android.app.AlertDialog.Builder(this).setTitle("Тип книги").setSingleChoiceItems(Ui.choices(this,labels,selectedIndex),selectedIndex,(dialog,index)->{withoutType=index==1;typeFilterId=index>=2?choices.get(index-2).id:null;updateTypeFilter();adapter.filter(search.getText().toString());dialog.dismiss();}).setNegativeButton("Закрыть",null).show();
    }
    private void chooseSort(){
        BookSort[] choices=BookSort.values();String[] labels=new String[choices.length];
        for(int i=0;i<choices.length;i++)labels[i]=choices[i].option;
        new android.app.AlertDialog.Builder(this).setTitle("Сортировка книг")
            .setSingleChoiceItems(Ui.choices(this,labels,sortOrder.ordinal()),sortOrder.ordinal(),(dialog,index)->{
                sortOrder=choices[index];
                getSharedPreferences("library_preferences",MODE_PRIVATE).edit().putString("sort_order",sortOrder.name()).apply();
                updateSortLabel();adapter.filter(search.getText().toString());dialog.dismiss();
            }).setNegativeButton("Закрыть",null).show();
    }
    private void updateSortLabel(){
        sortLabel.setText("↕  "+sortOrder.label);
        sortLabel.setContentDescription("Сортировка: "+sortOrder.option+". Изменить порядок книг");
    }
    private void toggle(String isbn){if(deleting)return;if(!selected.add(isbn))selected.remove(isbn);updateSelection();}
    private void updateSelection(){
        selectButton.setText(selecting?"Отмена":"Выбрать");selectButton.setEnabled(!deleting);
        primaryAction.setText(deleting?"Удаляем…":selecting?(collectionId>=0?"Убрать из коллекции · ":"Удалить выбранные · ")+selected.size():collectionId>=0?"Выбрать книги":"＋ Книга");
        sortLabel.setVisibility(selecting?View.GONE:View.VISIBLE);
        primaryAction.setLayoutParams(new LinearLayout.LayoutParams(selecting?-1:-2,-2));
        primaryAction.setEnabled(!deleting&&(!selecting||!selected.isEmpty()));
        selectionBack.setEnabled(selecting);
        receipt.setVisibility(selecting?View.GONE:View.VISIBLE);
        adapter.notifyDataSetChanged();
    }
    private void confirmDelete(){
        if(deleting||selected.isEmpty())return;
        ArrayList<String> targets=new ArrayList<>(selected);
        new android.app.AlertDialog.Builder(this).setTitle(collectionId>=0?"Убрать книги из коллекции?":"Удалить выбранные книги?")
            .setMessage(collectionId>=0?"Выбрано книг: "+targets.size()+". Они останутся в библиотеке.":"Будет удалено книг: "+targets.size()+". Их обложки тоже будут удалены. Это действие нельзя отменить.")
            .setNegativeButton("Отмена",null).setPositiveButton(collectionId>=0?"Убрать":"Удалить",(dialog,which)->{
                if(deleting)return;deleting=true;updateSelection();
                app.io.execute(()->{try{int removed;if(collectionId>=0){app.store.removeFromCollection(collectionId,targets);removed=targets.size();}else removed=app.store.delete(targets);runOnUiThread(()->{if(isDestroyed())return;deleting=false;selecting=false;selected.clear();receipt.removeAllViews();updateSelection();refresh();toast((collectionId>=0?"Убрано из коллекции: ":"Удалено книг: ")+removed);});}
                catch(Exception e){runOnUiThread(()->{if(isDestroyed())return;deleting=false;updateSelection();toast("Не удалось удалить книги. Попробуйте ещё раз.");});}});
            }).show();
    }
    private void toast(String s){if(!isDestroyed())Toast.makeText(this,s,Toast.LENGTH_LONG).show();}
    private final class BookAdapter extends BaseAdapter{
        List<Book> all=new ArrayList<>();final List<Book> items=new ArrayList<>();
        void filter(String query){String q=query.trim().toLowerCase(Locale.ROOT);items.clear();for(Book b:all){BookType type=bookTypeLabels.get(b.id);if((!withoutCover||!booksWithCovers.contains(b.id))&&(!withoutCollection||!collectedBooks.contains(b.id))&&(!withoutType||type==null)&&(typeFilterId==null||type!=null&&type.id==typeFilterId)&&(statusFilter==null||b.status==statusFilter)&&(b.title+" "+b.author+" "+b.isbn).toLowerCase(Locale.ROOT).contains(q))items.add(b);}
            Collections.sort(items,sortOrder.comparator());
            count.setText(!withoutCover&&!withoutCollection&&!withoutType&&typeFilterId==null&&statusFilter==null&&q.isEmpty()?Integer.toString(all.size()):items.size()+"/"+all.size());
            count.setContentDescription("Показано книг: "+items.size()+" из "+all.size());
            emptyTitle.setText(collectionId>=0&&q.isEmpty()&&statusFilter==null?"В коллекции пока нет книг":statusFilter!=null?"На этой полке пока пусто":q.isEmpty()?"Здесь начинается ваша полка":"Пока ничего не найдено");emptyNote.setText(collectionId>=0&&q.isEmpty()&&statusFilter==null?"Нажмите «Выбрать книги», чтобы собрать коллекцию.":statusFilter!=null?"Выберите другой статус или измените поиск.":q.isEmpty()?"Добавьте первую книгу —\nостальные истории подтянутся следом.":"Попробуйте другое название,\nимя автора или ISBN.");if(withoutCollection){emptyTitle.setText(q.isEmpty()?"Все книги распределены":"Пока ничего не найдено");emptyNote.setText(q.isEmpty()?"У каждой книги уже есть коллекция.":"Измените поиск или выберите другой фильтр.");}if(withoutCover){emptyTitle.setText(q.isEmpty()&&!all.isEmpty()?"У всех книг есть обложки":"Пока ничего не найдено");emptyNote.setText(q.isEmpty()&&!all.isEmpty()?"Можно выбрать другой фильтр.":"Измените поиск или выберите другой фильтр.");}notifyDataSetChanged();}
        public int getCount(){return items.size();}public Object getItem(int p){return items.get(p);}public long getItemId(int p){return p;}
        public View getView(int pos,View reuse,ViewGroup parent){LinearLayout row;
            if(reuse instanceof LinearLayout)row=(LinearLayout)reuse;else{
                row=new LinearLayout(MainActivity.this);row.setPadding(0,Ui.dp(MainActivity.this,10),0,Ui.dp(MainActivity.this,10));row.setMinimumHeight(Ui.dp(MainActivity.this,80));row.setGravity(Gravity.CENTER_VERTICAL);row.setBackground(Ui.round(Color.WHITE,MainActivity.this));
                View stripe=new View(MainActivity.this);LinearLayout.LayoutParams stripeLayout=new LinearLayout.LayoutParams(Ui.dp(MainActivity.this,3),Ui.dp(MainActivity.this,56));stripeLayout.setMarginEnd(Ui.dp(MainActivity.this,9));row.addView(stripe,stripeLayout);
                ImageView image=new ImageView(MainActivity.this);image.setScaleType(ImageView.ScaleType.FIT_CENTER);image.setBackground(Ui.surface(MainActivity.this,Ui.TINT,Ui.LINE,4));image.setClipToOutline(true);image.setContentDescription("Обложка книги");row.addView(image,new LinearLayout.LayoutParams(Ui.dp(MainActivity.this,42),Ui.dp(MainActivity.this,60)));
                LinearLayout words=Ui.column(MainActivity.this);words.setPadding(Ui.dp(MainActivity.this,12),0,0,0);row.addView(words,new LinearLayout.LayoutParams(0,-2,1));
                LinearLayout titleLine=new LinearLayout(MainActivity.this);titleLine.setGravity(Gravity.TOP);TextView title=Ui.text(MainActivity.this,"",15);title.setTypeface(Typeface.create("sans-serif-medium",Typeface.NORMAL));title.setMaxLines(2);title.setEllipsize(TextUtils.TruncateAt.END);titleLine.addView(title,new LinearLayout.LayoutParams(0,-2,1));TextView badge=Ui.text(MainActivity.this,"",11);badge.setSingleLine(true);badge.setPadding(Ui.dp(MainActivity.this,7),Ui.dp(MainActivity.this,3),Ui.dp(MainActivity.this,7),Ui.dp(MainActivity.this,3));LinearLayout.LayoutParams badgeLayout=new LinearLayout.LayoutParams(-2,-2);badgeLayout.setMarginStart(Ui.dp(MainActivity.this,8));titleLine.addView(badge,badgeLayout);words.addView(titleLine);Ui.gap(words,4);
                TextView author=Ui.muted(MainActivity.this,"",13);author.setMaxLines(1);author.setEllipsize(TextUtils.TruncateAt.END);
                words.addView(author);Ui.gap(words,4);
                TextView type=Ui.muted(MainActivity.this,"",12);type.setMaxLines(1);type.setEllipsize(TextUtils.TruncateAt.END);type.setCompoundDrawablePadding(Ui.dp(MainActivity.this,5));words.addView(type);Ui.gap(words,4);
                TextView collection=Ui.text(MainActivity.this,"",13);collection.setMaxLines(1);collection.setEllipsize(TextUtils.TruncateAt.END);
                Ui.Symbol folder=new Ui.Symbol("folder",Ui.GREEN);folder.setBounds(0,0,Ui.dp(MainActivity.this,14),Ui.dp(MainActivity.this,14));collection.setCompoundDrawablesRelative(folder,null,null,null);collection.setCompoundDrawablePadding(Ui.dp(MainActivity.this,5));
                words.addView(collection);
                CheckBox check=new CheckBox(MainActivity.this);check.setClickable(false);check.setFocusable(false);check.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);row.addView(check);
            }
            Book b=items.get(pos);
            BookType bookType=bookTypeLabels.get(b.id);View stripe=row.getChildAt(0);stripe.setVisibility(bookType==null?View.INVISIBLE:View.VISIBLE);if(bookType!=null)stripe.setBackground(Ui.round(bookType.color,MainActivity.this));
            CheckBox check=(CheckBox)row.getChildAt(3);check.setVisibility(selecting?View.VISIBLE:View.GONE);check.setChecked(selected.contains(b.id));
            row.setBackground(Ui.round(selecting&&selected.contains(b.id)?Ui.TINT:Ui.PAPER,MainActivity.this));
            row.setContentDescription(b.title+(bookType==null?"":", тип "+bookType.name)+", "+BookCollection.label(collectionLabels,b.id)+(b.status==ReadingStatus.NONE?"":", "+b.status.label)+(selecting?(selected.contains(b.id)?", выбрана":", не выбрана"):""));LinearLayout words=(LinearLayout)row.getChildAt(2);LinearLayout titleLine=(LinearLayout)words.getChildAt(0);((TextView)titleLine.getChildAt(0)).setText(b.title);TextView badge=(TextView)titleLine.getChildAt(1);badge.setVisibility(b.status==ReadingStatus.NONE?View.GONE:View.VISIBLE);badge.setText(b.status.label);badge.setTextColor(b.status.foreground);badge.setBackground(Ui.round(b.status.background,MainActivity.this));((TextView)words.getChildAt(2)).setText(b.author.isEmpty()?"Автор не указан":b.author);
            TextView type=(TextView)words.getChildAt(4);type.setVisibility(bookType==null?View.GONE:View.VISIBLE);type.setText(bookType==null?"":bookType.name);if(bookType!=null){Ui.Symbol typeIcon=new Ui.Symbol(bookType.icon,bookType.color);typeIcon.setBounds(0,0,Ui.dp(MainActivity.this,14),Ui.dp(MainActivity.this,14));type.setCompoundDrawablesRelative(typeIcon,null,null,null);}
            TextView collection=(TextView)words.getChildAt(6);collection.setText(BookCollection.label(collectionLabels,b.id));collection.setTextColor(collectionLabels.containsKey(b.id)?Ui.GREEN:Ui.MUTED);
            ImageView image=(ImageView)row.getChildAt(1);java.io.File cover=app.store.cover(b.id);String coverKey=b.id+":"+cover.lastModified()+":"+cover.length();image.setTag(coverKey);image.setImageDrawable(new Ui.Symbol("book",Ui.GREEN));Bitmap cached=coverCache.get(coverKey);if(cached!=null)image.setImageBitmap(cached);else if(cover.isFile()&&loadingCovers.add(coverKey))app.io.execute(()->{Bitmap bitmap=CoverImages.thumbnail(cover,Ui.dp(MainActivity.this,120));if(bitmap!=null)coverCache.put(coverKey,bitmap);loadingCovers.remove(coverKey);runOnUiThread(()->{if(!isDestroyed()&&coverKey.equals(image.getTag())&&bitmap!=null)image.setImageBitmap(bitmap);});});return row;
        }
    }
}
