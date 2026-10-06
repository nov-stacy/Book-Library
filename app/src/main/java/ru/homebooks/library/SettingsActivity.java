package ru.homebooks.library;

import android.app.AlertDialog;
import android.content.Intent;
import android.net.Uri;
import android.os.*;
import android.view.*;
import android.widget.*;
import androidx.activity.ComponentActivity;
import androidx.activity.OnBackPressedCallback;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.lifecycle.*;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.text.SimpleDateFormat;
import java.util.*;

public final class SettingsActivity extends ComponentActivity {
    private Operations operations;
    private TextView notice,autoBackupStatus;
    private Button autoBackupFolder,autoBackupDisable;
    private final android.content.SharedPreferences.OnSharedPreferenceChangeListener backupListener=(prefs,key)->runOnUiThread(this::renderAutoBackup);
    private final ActivityResultLauncher<Uri> chooseBackupFolder=registerForActivityResult(new ActivityResultContracts.OpenDocumentTree(),uri->{if(uri!=null)try{DailyBackup.choose((LibraryApp)getApplication(),uri);renderAutoBackup();}catch(Exception e){Toast.makeText(this,"Нет доступа к папке. Выберите другую папку.",Toast.LENGTH_LONG).show();}});
    private LinearLayout progressPanel;
    private ProgressBar progressBar;
    private TextView progressLabel,progressPercent;
    private LinearLayout progressRow;
    private Button backup,restore,csv,importCsv,retryCovers,cancelCovers;
    private AlertDialog confirmation;
    private final ActivityResultLauncher<String> createBackup=registerForActivityResult(new ActivityResultContracts.CreateDocument("application/zip"),uri->{if(uri!=null)operations.export(uri,true);});
    private final ActivityResultLauncher<String> createCsv=registerForActivityResult(new ActivityResultContracts.CreateDocument("text/csv"),uri->{if(uri!=null)operations.export(uri,false);});
    private final ActivityResultLauncher<String[]> openBackup=registerForActivityResult(new ActivityResultContracts.OpenDocument(),uri->{if(uri!=null)operations.inspect(uri);});
    private final ActivityResultLauncher<String[]> openCsv=registerForActivityResult(new ActivityResultContracts.OpenDocument(),uri->{if(uri!=null)operations.inspectCsv(uri);});
    @Override public void onCreate(Bundle state){
        super.onCreate(state);
        operations=new ViewModelProvider(this).get(Operations.class);operations.app=(LibraryApp)getApplication();
        LinearLayout root=Ui.column(this);root.setBackgroundColor(Ui.PAPER);setContentView(root);Ui.insets(root);
        root.addView(Ui.toolbar(this,"Настройки",this::leave));
        getOnBackPressedDispatcher().addCallback(this,new OnBackPressedCallback(true){public void handleOnBackPressed(){leave();}});
        progressPanel=Ui.column(this);progressPanel.setPadding(Ui.dp(this,16),Ui.dp(this,12),Ui.dp(this,16),Ui.dp(this,16));
        progressPanel.setBackground(Ui.round(Ui.TINT,this));
        notice=Ui.text(this,"",14);notice.setAccessibilityLiveRegion(View.ACCESSIBILITY_LIVE_REGION_POLITE);progressPanel.addView(notice);
        progressBar=new ProgressBar(this,null,android.R.attr.progressBarStyleHorizontal);
        progressBar.setMax(100);progressBar.setProgressTintList(android.content.res.ColorStateList.valueOf(Ui.GREEN));
        progressBar.setIndeterminateTintList(android.content.res.ColorStateList.valueOf(Ui.GREEN));
        progressBar.setProgressBackgroundTintList(android.content.res.ColorStateList.valueOf(Ui.LINE));
        progressPercent=percentLabel();progressRow=progressRow(progressBar,progressPercent);
        LinearLayout.LayoutParams barLayout=new LinearLayout.LayoutParams(-1,-2);barLayout.topMargin=Ui.dp(this,12);progressPanel.addView(progressRow,barLayout);
        progressLabel=Ui.muted(this,"",12);progressLabel.setPadding(0,Ui.dp(this,8),0,0);progressPanel.addView(progressLabel);
        root.addView(progressPanel,new LinearLayout.LayoutParams(-1,-2));
        ScrollView scroll=new ScrollView(this);root.addView(scroll,new LinearLayout.LayoutParams(-1,0,1));
        LinearLayout body=Ui.column(this);Ui.pad(body,16);scroll.addView(body);
        body.addView(Ui.muted(this,"Сохранение и перенос вашей библиотеки.",14));Ui.gap(body,16);
        LinearLayout copies=section(body,"Резервная копия","Все книги, коллекции, статусы и обложки в одном файле.\nПеренос на другой телефон.\nВосстановление сохранённых данных без интернета.");
        backup=Ui.action(this,"Создать копию ZIP",()->createBackup.launch(filename("zip")));copies.addView(backup,new LinearLayout.LayoutParams(-1,-2));Ui.gap(copies,10);
        restore=Ui.action(this,"Восстановить из ZIP",()->openBackup.launch(new String[]{"application/zip","application/x-zip-compressed","application/octet-stream"}));copies.addView(restore,new LinearLayout.LayoutParams(-1,-2));Ui.gap(copies,12);
        copies.addView(Ui.muted(this,"Текущие книги при восстановлении сохраняются.\nНедостающие обложки ищем по ISBN — для этого нужен интернет.",13));Ui.gap(body,16);
        LinearLayout automatic=section(body,"Ежедневная копия","ZIP со всеми книгами, коллекциями и обложками.\nПервую копию создадим после выбора папки.\nЗатем — раз в сутки; Android может отложить запуск.\nФайлы в выбранной папке останутся после удаления приложения.");
        autoBackupStatus=Ui.muted(this,"",14);autoBackupStatus.setLineSpacing(Ui.dp(this,4),1);automatic.addView(autoBackupStatus);Ui.gap(automatic,16);
        autoBackupFolder=Ui.action(this,"Выбрать папку",()->chooseBackupFolder.launch(null));automatic.addView(autoBackupFolder,new LinearLayout.LayoutParams(-1,-2));Ui.gap(automatic,8);
        autoBackupDisable=Ui.quietAction(this,"Выключить ежедневную копию",()->{DailyBackup.disable(this);renderAutoBackup();});automatic.addView(autoBackupDisable,new LinearLayout.LayoutParams(-1,-2));Ui.gap(body,16);renderAutoBackup();
        LinearLayout tables=section(body,"Перенос через CSV","Список книг в формате таблицы, без обложек.\nПосле загрузки ищем обложки по ISBN в интернете.");
        importCsv=Ui.action(this,"Загрузить из CSV",()->openCsv.launch(new String[]{"text/*","application/csv","application/vnd.ms-excel","application/octet-stream"}));tables.addView(importCsv,new LinearLayout.LayoutParams(-1,-2));Ui.gap(tables,10);
        csv=Ui.action(this,"Выгрузить в CSV",()->createCsv.launch(filename("csv")));tables.addView(csv,new LinearLayout.LayoutParams(-1,-2));Ui.gap(tables,12);
        tables.addView(Ui.muted(this,"Названия, авторы и статусы берём из файла.\nУже добавленные книги пропускаем.",13));Ui.gap(body,16);
        LinearLayout covers=section(body,"Обложки","Находим недостающие обложки по ISBN.\nСохранённые обложки не заменяем.\nПосле загрузки ZIP поиск начинается автоматически.\nДля поиска нужен интернет.");
        retryCovers=Ui.action(this,"Найти обложки",()->operations.retryCovers());covers.addView(retryCovers,new LinearLayout.LayoutParams(-1,-2));
        LinearLayout coverStatus=Ui.column(this);covers.addView(coverStatus,new LinearLayout.LayoutParams(-1,-2));Ui.gap(coverStatus,16);
        ProgressBar coverBar=new ProgressBar(this,null,android.R.attr.progressBarStyleHorizontal);coverBar.setMax(100);
        coverBar.setProgressTintList(android.content.res.ColorStateList.valueOf(Ui.GREEN));coverBar.setProgressBackgroundTintList(android.content.res.ColorStateList.valueOf(Ui.LINE));
        TextView coverPercent=percentLabel();coverStatus.addView(progressRow(coverBar,coverPercent),new LinearLayout.LayoutParams(-1,-2));Ui.gap(coverStatus,10);
        TextView coverProgress=Ui.muted(this,"",13);coverStatus.addView(coverProgress);
        Ui.gap(coverStatus,10);cancelCovers=Ui.quietAction(this,"Отменить поиск",()->operations.app.covers.cancel());coverStatus.addView(cancelCovers,new LinearLayout.LayoutParams(-1,-2));
        operations.app.covers.state.observe(this,value->{
            coverStatus.setVisibility(value.total==0?View.GONE:View.VISIBLE);coverBar.setProgress(value.percent());coverPercent.setText(value.percent()+"%");
            coverProgress.setText((value.running?"Проверено "+value.checked+" из "+value.total:value.cancelled?"Поиск отменён":"Поиск завершён")+"\nОбложек добавлено: "+value.saved+"\nНе найдено или недоступно: "+value.missing+(value.running?"\nМожно вернуться в библиотеку.":""));
            render();
        });
        operations.changes.observe(this,value->render());
    }
    private TextView percentLabel(){
        TextView label=Ui.text(this,"0%",13);label.setTextColor(Ui.GREEN);label.setTypeface(android.graphics.Typeface.create("sans-serif-medium",android.graphics.Typeface.NORMAL));
        label.setSingleLine(true);label.setGravity(Gravity.END|Gravity.CENTER_VERTICAL);label.setMinWidth(Ui.dp(this,44));return label;
    }
    private LinearLayout progressRow(ProgressBar bar,TextView percent){
        LinearLayout row=new LinearLayout(this);row.setGravity(Gravity.CENTER_VERTICAL);
        row.addView(bar,new LinearLayout.LayoutParams(0,Ui.dp(this,8),1));
        LinearLayout.LayoutParams labelLayout=new LinearLayout.LayoutParams(-2,-2);labelLayout.setMarginStart(Ui.dp(this,12));row.addView(percent,labelLayout);return row;
    }
    private LinearLayout section(LinearLayout body,String title,String description){
        LinearLayout card=Ui.column(this);Ui.pad(card,16);card.setBackground(Ui.surface(this,android.graphics.Color.WHITE,Ui.LINE,16));body.addView(card,new LinearLayout.LayoutParams(-1,-2));
        TextView heading=Ui.heading(this,title);heading.setTextSize(20);card.addView(heading);Ui.gap(card,8);TextView descriptionView=Ui.muted(this,description,14);descriptionView.setLineSpacing(Ui.dp(this,4),1);card.addView(descriptionView);Ui.gap(card,18);return card;
    }
    private void renderAutoBackup(){if(autoBackupStatus==null)return;autoBackupStatus.setText(DailyBackup.status(this));boolean enabled=DailyBackup.enabled(this);autoBackupFolder.setText(enabled?"Изменить папку":"Выбрать папку");autoBackupDisable.setVisibility(enabled?View.VISIBLE:View.GONE);}
    @Override protected void onResume(){super.onResume();DailyBackup.prefs(this).registerOnSharedPreferenceChangeListener(backupListener);renderAutoBackup();}
    @Override protected void onPause(){DailyBackup.prefs(this).unregisterOnSharedPreferenceChangeListener(backupListener);super.onPause();}
    private void leave(){if(operations.busy)Toast.makeText(this,"Дождитесь завершения операции",Toast.LENGTH_SHORT).show();else finish();}
    private String filename(String extension){return "library-"+new SimpleDateFormat("yyyy-MM-dd-HHmmss",Locale.ROOT).format(new Date())+"."+extension;}
    private void render(){
        boolean available=!operations.busy&&operations.pending==null&&operations.csvPending==null;
        backup.setEnabled(available);restore.setEnabled(available);csv.setEnabled(available);importCsv.setEnabled(available);notice.setText(operations.message);
        CoverLoader.State coverState=operations.app.covers.state.getValue();boolean searching=coverState!=null&&coverState.running;
        cancelCovers.setVisibility(searching?View.VISIBLE:View.GONE);retryCovers.setEnabled(available&&!searching);retryCovers.setText(searching?"Ищем обложки…":"Найти обложки");
        progressPanel.setVisibility(operations.message.isEmpty()?View.GONE:View.VISIBLE);
        progressRow.setVisibility(operations.busy?View.VISIBLE:View.GONE);
        progressPercent.setVisibility(operations.progressTotal>0?View.VISIBLE:View.GONE);
        progressBar.setIndeterminate(operations.progressTotal<=0);
        int percent=operations.progressTotal<=0?0:(int)(100L*operations.progressDone/operations.progressTotal);
        if(operations.progressTotal>0)progressBar.setProgress(percent);
        progressLabel.setVisibility(operations.busy&&operations.progressTotal>0?View.VISIBLE:View.GONE);
        progressLabel.setText(operations.progressStage);progressPercent.setText(percent+"%");
        if(operations.csvPending!=null&&!operations.busy&&confirmation==null){
            confirmation=new AlertDialog.Builder(this).setTitle("Загрузить книги из CSV?")
                .setMessage("Книг в файле: "+operations.csvPending.size()+"\nНовых: "+operations.newBooks+"\nСовпадений по ISBN: "+(operations.csvPending.size()-operations.newBooks)+"\n\nТекущие книги не изменятся. Недостающие обложки будем искать в интернете. Если обложка не найдётся, книга останется в библиотеке.")
                .setNegativeButton("Отмена",(d,w)->operations.cancel()).setPositiveButton("Загрузить",(d,w)->operations.importCsv()).setOnCancelListener(d->operations.cancel()).create();
            confirmation.setOnDismissListener(d->confirmation=null);confirmation.show();
        }
        if(operations.pending!=null&&!operations.busy&&confirmation==null){
            confirmation=new AlertDialog.Builder(this).setTitle("Восстановить библиотеку?")
                .setMessage("Книг в копии: "+operations.pending.books.size()+"\nНовых: "+operations.newBooks+"\nУже в библиотеке: "+(operations.pending.books.size()-operations.newBooks)+"\n\nУ существующих книг добавятся недостающие ISBN. Остальные данные сохранятся. Коллекции из копии будут добавлены или объединены по названию. Недостающие обложки будем искать по ISBN в интернете.")
                .setNegativeButton("Отмена",(d,w)->operations.cancel())
                .setPositiveButton("Восстановить",(d,w)->operations.restore())
                .setOnCancelListener(d->operations.cancel()).create();
            confirmation.setOnDismissListener(d->confirmation=null);confirmation.show();
        }
    }
    @Override protected void onDestroy(){if(confirmation!=null){confirmation.setOnCancelListener(null);confirmation.setOnDismissListener(null);confirmation.dismiss();}super.onDestroy();}

    /** Keeps an in-flight operation and its inspected archive across rotation. */
    public static final class Operations extends ViewModel {
        LibraryApp app;
        final MutableLiveData<Integer> changes=new MutableLiveData<>(0);
        final Handler main=new Handler(Looper.getMainLooper());
        boolean busy,cleared;
        String message="";
        LibraryBackup.Archive pending;
        List<Book> csvPending;
        int newBooks,progressDone,progressTotal;
        String progressStage="";
        long lastProgress;
        void progress(int done,int total,String stage){
            long now=SystemClock.elapsedRealtime();
            if(done!=0&&done!=total&&!stage.equals("Завершаем сохранение…")&&now-lastProgress<100)return;
            lastProgress=now;
            main.post(()->{progressDone=done;progressTotal=total;progressStage=stage;update();});
        }
        void update(){changes.setValue(changes.getValue()+1);}
        void start(String text){busy=true;message=text;progressDone=0;progressTotal=0;progressStage="";lastProgress=0;update();}
        void complete(String text){main.post(()->{busy=false;message=text;update();});}
        void export(Uri uri,boolean full){
            if(busy)return;start(full?"Создаём резервную копию…":"Выгружаем CSV…");
            app.io.execute(()->{
                try{
                    List<Book> books=app.store.all();
                    try(OutputStream out=app.getContentResolver().openOutputStream(uri,"wt")){
                        if(out==null)throw new IOException();
                        if(full)LibraryBackup.write(out,books,app.store::cover,app.store.collections());
                        else{Writer writer=new OutputStreamWriter(out,StandardCharsets.UTF_8);Csv.write(writer,books);}
                    }
                    complete((full?"Резервная копия сохранена. Книг: ":"CSV сохранён. Книг: ")+books.size());
                }catch(Exception e){complete("Не удалось сохранить файл. Он может быть неполным. Проверьте свободное место и повторите выгрузку.");}
            });
        }
        void inspectCsv(Uri uri){
            if(busy)return;start("Проверяем CSV…");
            app.io.execute(()->{
                try{
                    List<Book> books;
                    try(InputStream in=app.getContentResolver().openInputStream(uri)){
                        if(in==null)throw new IOException("Не удалось открыть файл");
                        java.nio.charset.CharsetDecoder decoder=StandardCharsets.UTF_8.newDecoder().onMalformedInput(java.nio.charset.CodingErrorAction.REPORT);
                        books=Csv.read(new InputStreamReader(in,decoder));
                    }
                    Set<String> existing=new HashSet<>();for(Book b:app.store.all())existing.add(b.id);int missing=0;
                    for(Book b:books)if(existing.add(b.id))missing++;
                    int count=missing;main.post(()->{busy=false;if(cleared)return;csvPending=books;newBooks=count;message="CSV проверен";update();});
                }catch(IOException e){complete("CSV не загружен. "+(e instanceof java.nio.charset.CharacterCodingException?"Сохраните файл в кодировке UTF-8.":e.getMessage())+" Библиотека не изменена.");}
                catch(Exception e){complete("Не удалось проверить CSV. Библиотека не изменена.");}
            });
        }
        void importCsv(){
            if(busy||csvPending==null)return;List<Book> books=csvPending;csvPending=null;start("Добавляем книги…");
            app.io.execute(()->{
                try{
                    int added=app.store.importMissing(books);
                    queueCovers(books);
                    complete("Добавлено книг: "+added+". Совпадения пропущены: "+(books.size()-added)+". Можно вернуться в библиотеку.");
                }catch(Exception e){complete("Не удалось завершить импорт. Проверьте свободное место и повторите загрузку.");}
            });
        }
        private int queueCovers(List<Book> books){
            Set<String> ids=new LinkedHashSet<>();
            for(Book imported:books){
                Book stored=app.store.find(imported.id);
                if(stored!=null&&!stored.isbn.isEmpty()&&!app.store.cover(stored.id).isFile())ids.add(stored.id);
            }
            if(!ids.isEmpty())app.covers.enqueue(ids);
            return ids.size();
        }
        void retryCovers(){
            if(busy)return;start("Проверяем книги без обложек…");
            app.io.execute(()->{try{
                int count=queueCovers(app.store.all());
                complete(count==0?"Нет книг с ISBN, которым нужна обложка.":"");
            }catch(Exception e){complete("Не удалось начать поиск обложек. Попробуйте ещё раз.");}});
        }
        void inspect(Uri uri){
            if(busy)return;start("Проверяем резервную копию…");
            app.io.execute(()->{
                LibraryBackup.Archive archive=null;
                try{
                    try(InputStream in=app.getContentResolver().openInputStream(uri)){
                        if(in==null)throw new IOException();archive=LibraryBackup.read(in,app.getCacheDir());
                    }
                    Set<String> existing=new HashSet<>();for(Book b:app.store.all())existing.add(b.id);
                    int missing=0;for(Book b:archive.books)if(!existing.contains(b.id))missing++;
                    LibraryBackup.Archive ready=archive;int count=missing;
                    main.post(()->{busy=false;if(cleared){ready.close();return;}pending=ready;newBooks=count;message="Копия проверена";update();});
                }catch(Exception e){if(archive!=null)archive.close();complete("Не удалось прочитать копию. Выберите ZIP, созданный приложением. Библиотека не изменена.");}
            });
        }
        void cancel(){csvPending=null;LibraryBackup.Archive archive=pending;pending=null;if(archive!=null)app.io.execute(archive::close);message="Восстановление отменено";update();}
        void restore(){
            if(busy||pending==null)return;LibraryBackup.Archive archive=pending;pending=null;start("Восстанавливаем книги…");
            app.io.execute(()->{
                try{int count=app.store.restoreMissing(archive,this::progress);queueCovers(archive.books);complete("Восстановлено новых книг: "+count+". Недостающие ISBN дополнены. Поиск обложек продолжится в фоне.");}
                catch(Exception e){complete("Не удалось восстановить копию. Проверьте свободное место и повторите. Существующие книги не изменены.");}
                finally{archive.close();}
            });
        }
        @Override protected void onCleared(){cleared=true;if(pending!=null){LibraryBackup.Archive archive=pending;pending=null;app.io.execute(archive::close);}}
    }
}
