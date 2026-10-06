package ru.homebooks.library;

import android.content.*;
import android.graphics.*;
import android.net.Uri;
import android.os.Bundle;
import android.view.*;
import android.widget.*;
import androidx.activity.ComponentActivity;
import androidx.activity.OnBackPressedCallback;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.IntentSenderRequest;
import com.google.mlkit.vision.documentscanner.GmsDocumentScannerOptions;
import com.google.mlkit.vision.documentscanner.GmsDocumentScanning;
import com.google.mlkit.vision.documentscanner.GmsDocumentScanningResult;
import androidx.activity.result.contract.ActivityResultContracts;

public final class AddBookActivity extends ComponentActivity {
    private LibraryApp app;
    private final java.util.Set<String> draftCollections=new java.util.LinkedHashSet<>();
    private final java.util.Set<String> originalCollections=new java.util.LinkedHashSet<>();
    private LinearLayout body;
    private EditText title,author,isbnInput;
    private String isbn, draftTitle="",draftAuthor="",notice="",lastIsbn="",lastTitle="";
    private long addedAt;
    private String scanCoverBook, pendingScanUri;
    private boolean pendingCoverPhoto;
    private String readOn="",originalReadOn="",knownIsbn="";
    private boolean busy, continuous, lookupEntry, editing, lastDuplicate;
    private int request;
    private boolean manualEntry;
    private String manualIsbn="";
    private boolean editMode, confirming, confirmationFlow, offerCover, duplicateCoverOffer;
    private String draftCoverPath;
    private BookLocation location=BookLocation.HOME,originalLocation=BookLocation.HOME;
    private ReadingStatus readingStatus=ReadingStatus.NONE;
    private String originalTitle="", originalAuthor="";
    private final ActivityResultLauncher<String> pickCover=registerForActivityResult(new ActivityResultContracts.GetContent(), uri->{if(uri!=null)previewCover(uri,true);});
    private final ActivityResultLauncher<Intent> editCover=registerForActivityResult(new ActivityResultContracts.StartActivityForResult(),result->{
        if(result.getResultCode()!=RESULT_OK||result.getData()==null)return;
        String path=result.getData().getStringExtra("coverPath"),target=result.getData().getStringExtra("bookId");
        if(path==null)return;
        java.io.File file=new java.io.File(path);
        try{if(!file.getCanonicalPath().startsWith(getCacheDir().getCanonicalPath()+java.io.File.separator))return;}catch(java.io.IOException e){return;}
        if(target==null||!target.equals(isbn)||busy){deleteDraftFile(path);return;}
        useDraftCover(path);notice="";editor();
    });
    private final ActivityResultLauncher<IntentSenderRequest> scanCover=registerForActivityResult(
        new ActivityResultContracts.StartIntentSenderForResult(),result->{
            String target=scanCoverBook;scanCoverBook=null;
            if(result.getResultCode()!=RESULT_OK)return;
            if(target==null||!target.equals(isbn)||busy)return;
            GmsDocumentScanningResult scanned=GmsDocumentScanningResult.fromActivityResultIntent(result.getData());
            if(scanned==null||scanned.getPages()==null||scanned.getPages().size()!=1){toast("Не удалось получить снимок. Попробуйте ещё раз.");return;}
            previewScannedCover(scanned.getPages().get(0).getImageUri());
        });
    private final ActivityResultLauncher<Intent> scan=registerForActivityResult(new ActivityResultContracts.StartActivityForResult(),result->{
        if(result.getResultCode()==RESULT_OK && result.getData()!=null){continuous=true;lookupEntry=true;openIsbn(result.getData().getStringExtra("isbn"));}
        else{continuous=false;finish();}
    });
    @Override public void onCreate(Bundle state){
        super.onCreate(state);app=(LibraryApp)getApplication();
        getOnBackPressedDispatcher().addCallback(this,new OnBackPressedCallback(true){public void handleOnBackPressed(){leave();}});
        if(state!=null){duplicateCoverOffer=state.getBoolean("duplicateCoverOffer");offerCover=state.getBoolean("offerCover");confirming=state.getBoolean("confirming");confirmationFlow=state.getBoolean("confirmationFlow");continuous=state.getBoolean("continuous");lookupEntry=state.getBoolean("lookupEntry");lastIsbn=state.getString("lastIsbn","");lastTitle=state.getString("lastTitle","");lastDuplicate=state.getBoolean("lastDuplicate");draftCoverPath=state.getString("draftCoverPath");scanCoverBook=state.getString("scanCoverBook");pendingScanUri=state.getString("pendingScanUri");pendingCoverPhoto=state.getBoolean("pendingCoverPhoto");java.util.ArrayList<String> groups=state.getStringArrayList("draftCollections");if(groups!=null)draftCollections.addAll(groups);java.util.ArrayList<String> originalGroups=state.getStringArrayList("originalCollections");if(originalGroups!=null)originalCollections.addAll(originalGroups);knownIsbn=state.getString("knownIsbn","");readOn=state.getString("readOn","");originalReadOn=state.getString("originalReadOn",readOn);location=BookLocation.fromId(state.getInt("location",0));originalLocation=BookLocation.fromId(state.getInt("originalLocation",location.id));isbn=state.getString("isbn");if(knownIsbn.isEmpty()&&isbn!=null&&!Book.isLocalId(isbn))knownIsbn=isbn;draftTitle=state.getString("title","");draftAuthor=state.getString("author","");addedAt=state.getLong("date");notice=state.getString("notice","");readingStatus=ReadingStatus.fromId(state.getInt("readingStatus",0));editing=state.getBoolean("editing");editMode=state.getBoolean("editMode");originalTitle=state.getString("originalTitle",draftTitle);originalAuthor=state.getString("originalAuthor",draftAuthor);
            confirming=false;confirmationFlow=false;
            if(state.getBoolean("busy"))notice="Поиск прерван. Нажмите «Повторить поиск» или заполните карточку.";
            if(isbn==null){manualIsbn=state.getString("manualIsbn","");if(state.getBoolean("manualEntry"))manualIsbnScreen();else selection();}else{editor();if(pendingScanUri!=null)previewCover(Uri.parse(pendingScanUri),pendingCoverPhoto);}return;}
        String existing=getIntent().getStringExtra("isbn");
        if(existing!=null){lookupEntry=false;openIsbn(existing);}else selection();
        if(Intent.ACTION_SEND.equals(getIntent().getAction()))sharePage(getIntent().getStringExtra(Intent.EXTRA_TEXT));
    }
    @Override protected void onSaveInstanceState(Bundle state){capture();state.putBoolean("manualEntry",manualEntry);state.putString("manualIsbn",manualEntry&&isbnInput!=null?isbnInput.getText().toString():manualIsbn);state.putBoolean("offerCover",offerCover);state.putBoolean("confirming",confirming);state.putBoolean("confirmationFlow",confirmationFlow);state.putBoolean("continuous",continuous);state.putBoolean("lookupEntry",lookupEntry);state.putString("lastIsbn",lastIsbn);state.putString("lastTitle",lastTitle);state.putBoolean("lastDuplicate",lastDuplicate);state.putString("draftCoverPath",draftCoverPath);state.putString("scanCoverBook",scanCoverBook);state.putString("pendingScanUri",pendingScanUri);state.putBoolean("pendingCoverPhoto",pendingCoverPhoto);state.putStringArrayList("draftCollections",new java.util.ArrayList<>(draftCollections));state.putStringArrayList("originalCollections",new java.util.ArrayList<>(originalCollections));state.putString("knownIsbn",knownIsbn);state.putString("readOn",readOn);state.putString("originalReadOn",originalReadOn);state.putInt("location",location.id);state.putInt("originalLocation",originalLocation.id);state.putString("isbn",isbn);state.putString("title",draftTitle);state.putString("author",draftAuthor);state.putLong("date",addedAt);state.putString("notice",notice);state.putBoolean("busy",busy);state.putInt("readingStatus",readingStatus.id);state.putBoolean("editing",editing);state.putBoolean("editMode",editMode);state.putString("originalTitle",originalTitle);state.putString("originalAuthor",originalAuthor);state.putBoolean("duplicateCoverOffer",duplicateCoverOffer);super.onSaveInstanceState(state);}
    private void capture(){if(title!=null){draftTitle=title.getText().toString();draftAuthor=author.getText().toString();}}
    private void shell(String eyebrow,String heading){
        title=null;author=null;
        LinearLayout root=Ui.column(this);root.setBackgroundColor(Ui.PAPER);setContentView(root);Ui.insets(root);
        root.addView(Ui.toolbar(this,heading,this::leave));
        ScrollView scroll=new ScrollView(this);scroll.setFillViewport(true);root.addView(scroll,new LinearLayout.LayoutParams(-1,0,1));body=Ui.column(this);body.setPadding(Ui.dp(this,16),Ui.dp(this,16),Ui.dp(this,16),Ui.dp(this,24));scroll.addView(body);
    }
    private void selection(){
        manualEntry=false;isbnInput=null;
        busy=false;editing=false;editMode=false;confirming=false;confirmationFlow=false;isbn=null;shell("ПОПОЛНИТЬ КОЛЛЕКЦИЮ","Новая книга");
        body.addView(Ui.muted(this,"Один штрихкод — и книга на вашей полке.",14));Ui.gap(body,20);
        LinearLayout card=Ui.column(this);Ui.pad(card,16);card.setGravity(Gravity.CENTER);card.setBackground(Ui.surface(this,Color.WHITE,Ui.LINE,16));
        ImageView icon=new ImageView(this);icon.setImageDrawable(new Ui.Symbol("scan",Ui.GREEN));card.addView(icon,new LinearLayout.LayoutParams(Ui.dp(this,56),Ui.dp(this,56)));Ui.gap(card,24);
        TextView title=Ui.text(this,"Сканируйте всю полку",22);title.setTypeface(Typeface.create("serif",Typeface.NORMAL));title.setGravity(Gravity.CENTER);card.addView(title);Ui.gap(card,10);
        TextView note=Ui.muted(this,"Найдём книгу по ISBN.\nВы проверите её перед добавлением.",14);note.setGravity(Gravity.CENTER);card.addView(note);Ui.gap(card,24);
        card.addView(Ui.primary(this,"Начать сканирование",this::startScan),new LinearLayout.LayoutParams(-1,-2));body.addView(card);Ui.gap(body,24);
        body.addView(Ui.action(this,"Ввести ISBN вручную",this::manualIsbnScreen),new LinearLayout.LayoutParams(-1,-2));
        Ui.gap(body,12);body.addView(Ui.action(this,"Добавить без ISBN",()->{
            draftCollections.clear();knownIsbn="";readOn="";originalReadOn="";isbn=Book.newLocalId();draftTitle="";draftAuthor="";addedAt=System.currentTimeMillis();readingStatus=ReadingStatus.NONE;location=BookLocation.HOME;
            busy=false;editing=false;editMode=true;continuous=false;lookupEntry=false;notice="Введите название и автора. Обложку можно выбрать с телефона.";editor();
        }),new LinearLayout.LayoutParams(-1,-2));
        Ui.gap(body,24);body.addView(Ui.muted(this,"Данные ищутся в каталогах и интернете. Ваша библиотека хранится только на телефоне.",13));
    }
    private void manualIsbnScreen(){
        manualEntry=true;shell("ДОБАВЛЕНИЕ КНИГИ","Ввести ISBN");
        body.addView(Ui.muted(this,"Введите номер с обложки книги.\nМожно вставить ISBN с пробелами и дефисами.",14));Ui.gap(body,24);
        body.addView(Ui.eyebrow(this,"ISBN"));Ui.gap(body,8);
        isbnInput=Ui.input(this,"ISBN-13 или ISBN-10");isbnInput.setSingleLine(true);
        isbnInput.setInputType(android.text.InputType.TYPE_CLASS_TEXT|android.text.InputType.TYPE_TEXT_FLAG_CAP_CHARACTERS|android.text.InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS);
        isbnInput.setImeOptions(android.view.inputmethod.EditorInfo.IME_ACTION_SEARCH);
        isbnInput.setText(manualIsbn);isbnInput.setSelection(isbnInput.length());body.addView(isbnInput);Ui.gap(body,20);
        body.addView(Ui.primary(this,"Найти книгу",this::submitManualIsbn),new LinearLayout.LayoutParams(-1,-2));
        isbnInput.setOnEditorActionListener((v,action,event)->{if(action==android.view.inputmethod.EditorInfo.IME_ACTION_SEARCH){submitManualIsbn();return true;}return false;});
        isbnInput.requestFocus();
    }
    private void hideManualKeyboard(){
        if(isbnInput!=null){android.view.inputmethod.InputMethodManager keyboard=(android.view.inputmethod.InputMethodManager)getSystemService(INPUT_METHOD_SERVICE);if(keyboard!=null)keyboard.hideSoftInputFromWindow(isbnInput.getWindowToken(),0);}
    }
    private void submitManualIsbn(){
        if(busy||!manualEntry)return;
        manualIsbn=isbnInput.getText().toString();String code=Isbn.normalize(manualIsbn);
        if(code==null){isbnInput.setError("Проверьте цифры ISBN");return;}
        hideManualKeyboard();manualEntry=false;continuous=false;lookupEntry=true;openIsbn(code);
    }
    private void startScan(){scan.launch(new Intent(this,ScanActivity.class).putExtra("skipIsbn",lastIsbn).putExtra("savedTitle",lastTitle).putExtra("alreadySaved",lastDuplicate));}
    private void openIsbn(String raw){
        clearDraftCover();duplicateCoverOffer=false;confirming=false;confirmationFlow=false;draftCollections.clear();
        String code=Book.isLocalId(raw)?raw:Isbn.normalize(raw);if(code==null){toast("Некорректный ISBN");return;}
        knownIsbn=Book.isLocalId(code)?"":code;readOn="";originalReadOn="";isbn=code;location=BookLocation.HOME;readingStatus=ReadingStatus.NONE;draftTitle="";draftAuthor="";addedAt=System.currentTimeMillis();busy=true;editing=false;editMode=false;notice="Проверяем вашу библиотеку…";editor();int token=++request;
        app.io.execute(()->{try{Book found=app.store.find(code);if(found==null)found=app.store.findByIsbn(code);Book saved=found;boolean missingSavedCover=saved!=null&&!hasReadableCover(app.store.cover(saved.id));java.util.Set<String> savedGroups=new java.util.LinkedHashSet<>();if(saved!=null)for(BookCollection group:app.store.collections())if(group.isbns.contains(saved.id))savedGroups.add(Long.toString(group.id));runOnUiThread(()->{if(!active(token))return;
            if(saved!=null){isbn=saved.id;knownIsbn=saved.isbn;if(lookupEntry){lastIsbn=code;lastTitle=saved.title;lastDuplicate=true;busy=false;if(!missingSavedCover){continueAfterDuplicate();return;}duplicateCoverOffer=true;}
                draftCollections.clear();draftCollections.addAll(savedGroups);originalCollections.clear();originalCollections.addAll(savedGroups);busy=false;editing=true;editMode=false;readOn=saved.readOn;originalReadOn=readOn;readingStatus=saved.status;location=saved.location;originalLocation=location;draftTitle=saved.title;draftAuthor=saved.author;originalTitle=saved.title;originalAuthor=saved.author;addedAt=saved.addedAt;notice="Сохранено на телефоне";editor();
            }else if(Book.isLocalId(code)){busy=false;notice="Книга не найдена";toast(notice);finish();}else lookup(null);
        });}catch(Exception e){runOnUiThread(()->{if(active(token)){busy=false;notice="Не удалось прочитать библиотеку. Попробуйте ещё раз.";editor();}});}});
    }
    private static boolean hasReadableCover(java.io.File file){
        BitmapFactory.Options bounds=new BitmapFactory.Options();bounds.inJustDecodeBounds=true;
        BitmapFactory.decodeFile(file.getAbsolutePath(),bounds);return bounds.outWidth>0&&bounds.outHeight>0;
    }
    private void continueAfterDuplicate(){
        duplicateCoverOffer=false;
        if(continuous)startScan();else{setResult(RESULT_OK,new Intent().putExtra("savedIsbn",isbn).putExtra("savedTitle",lastTitle).putExtra("alreadySaved",true));finish();}
    }
    private void lookup(String page){
        capture();confirming=false;busy=true;notice=page==null?"Ищем название и автора…":"Читаем данные на странице…";editor();int token=++request;String code=isbn;boolean existing=false;
        app.network.execute(()->{
            BookLookup.Result result=page==null?new BookLookup().findMetadata(code):new BookLookup().fromPage(page,code);
            String coverPath=null;
            if(result.cover!=null)try{coverPath=writeCover(code,result.cover,existing);}catch(Exception ignored){}
            String readyCover=coverPath;
            runOnUiThread(()->{if(!active(token)){if(!existing)deleteDraftFile(readyCover);return;}if(!existing&&readyCover!=null)useDraftCover(readyCover);showLookupResult(result);});
        });
    }
    void showLookupResult(BookLookup.Result result){
        busy=false;notice=result.notice;
        if(!result.title.isEmpty()){draftTitle=result.title;draftAuthor=result.author;}
        confirming=false;confirmationFlow=false;offerCover=false;
        if(!result.title.isEmpty())notice="Данные найдены";else notice="Книга не найдена. Введите название и автора.";
        editor();
    }
    private void titleSearchButton(){
        if(editing)return;
        Button find=Ui.quietAction(this,"Найти по названию","search",this::lookupTitle);
        find.setEnabled(!title.getText().toString().trim().isEmpty());
        title.addTextChangedListener(new android.text.TextWatcher(){
            public void beforeTextChanged(CharSequence s,int start,int count,int after){}
            public void onTextChanged(CharSequence s,int start,int before,int count){find.setEnabled(!s.toString().trim().isEmpty());}
            public void afterTextChanged(android.text.Editable s){}
        });
        body.addView(find,new LinearLayout.LayoutParams(-1,-2));Ui.gap(body,20);
    }
    private void lookupTitle(){
        capture();if(busy||draftTitle.trim().isEmpty())return;
        String name=draftTitle.trim(),writer=draftAuthor.trim(),code=knownIsbn;
        busy=true;notice="Ищем по названию…";editor();int token=++request;
        app.network.execute(()->{
            TitleBookSearch.Result result=new BookLookup().findByTitle(name,writer,code);
            runOnUiThread(()->{if(!active(token))return;busy=false;
                notice=result.candidates.isEmpty()?(result.incomplete?"Не удалось проверить все источники. Попробуйте ещё раз или заполните карточку вручную.":"По этому названию ничего не найдено. Уточните название или добавьте обложку самостоятельно."):"Выберите книгу и проверьте обложку.";
                editor();if(!result.candidates.isEmpty())showTitleCandidates(result);
            });
        });
    }
    private void lookupMissingCover(){
        if(busy||(editing&&!duplicateCoverOffer))return;
        capture();String name=draftTitle.trim(),writer=draftAuthor.trim(),code=knownIsbn;
        if(name.isEmpty())return;
        busy=true;notice="Ищем обложку по названию и сверяем ISBN…";editor();int token=++request;
        app.network.execute(()->{
            try{
                TitleBookSearch.Result found=new BookLookup().findCoverCandidates(name,writer,code);
                java.util.List<TitleBookSearch.Candidate> covers=new java.util.ArrayList<>();
                for(TitleBookSearch.Candidate candidate:found.candidates)if(!candidate.book.cover.isEmpty())covers.add(candidate);
                TitleBookSearch.Result result=new TitleBookSearch.Result(covers,found.incomplete);
                runOnUiThread(()->{if(!active(token))return;busy=false;editor();if(covers.isEmpty())missingCoverResult(found.incomplete);else showTitleCandidates(result,true);});
            }catch(Exception e){runOnUiThread(()->{if(active(token)){busy=false;editor();missingCoverResult(true);}});}
        });
    }
    private void missingCoverResult(boolean incomplete){
        LinearLayout content=Ui.column(this);Ui.pad(content,24);
        content.setBackground(Ui.surface(this,Ui.PAPER,Ui.LINE,22));
        TextView heading=Ui.text(this,incomplete?"Обложку получить не удалось":"Обложка не найдена",21);
        heading.setGravity(Gravity.CENTER);content.addView(heading);Ui.gap(content,12);
        TextView description=Ui.muted(this,incomplete?"Часть источников не ответила. Попробуйте поиск позже.":"По ISBN и названию не удалось найти обложку.",15);
        description.setGravity(Gravity.CENTER);description.setLineSpacing(Ui.dp(this,3),1);content.addView(description);Ui.gap(content,24);
        android.app.AlertDialog dialog=new android.app.AlertDialog.Builder(this).create();
        content.addView(Ui.primary(this,"Вернуться",dialog::dismiss),new LinearLayout.LayoutParams(-1,-2));
        ScrollView scroll=new ScrollView(this);scroll.addView(content);dialog.setView(scroll,0,0,0,0);dialog.show();
        if(dialog.getWindow()!=null)dialog.getWindow().setBackgroundDrawable(new android.graphics.drawable.ColorDrawable(Color.TRANSPARENT));
    }
    private void showTitleCandidates(TitleBookSearch.Result result){showTitleCandidates(result,false);}
    private void showTitleCandidates(TitleBookSearch.Result result,boolean coverOnly){
        if(result.candidates.size()==1){previewTitleCandidate(result.candidates.get(0),result,coverOnly);return;}
        LinearLayout list=Ui.column(this);Ui.pad(list,16);
        if(result.incomplete){list.addView(Ui.muted(this,"Часть источников не ответила. Показываем найденное.",14));Ui.gap(list,12);}
        android.app.AlertDialog dialog=new android.app.AlertDialog.Builder(this).setTitle("Результаты поиска").setNegativeButton("Вернуться к карточке",null).create();
        for(TitleBookSearch.Candidate candidate:result.candidates){
            Button row=Ui.action(this,candidate.book.title+"\n"+(candidate.book.author.isEmpty()?"":candidate.book.author+" · ")+candidate.book.source+"\n"+candidate.editionNote(knownIsbn),()->{dialog.dismiss();previewTitleCandidate(candidate,result,coverOnly);});
            row.setSingleLine(false);row.setGravity(Gravity.START|Gravity.CENTER_VERTICAL);list.addView(row,new LinearLayout.LayoutParams(-1,-2));Ui.gap(list,8);
        }
        ScrollView scroll=new ScrollView(this);scroll.addView(list);dialog.setView(scroll);dialog.show();
    }
    private void previewTitleCandidate(TitleBookSearch.Candidate candidate,TitleBookSearch.Result candidates,boolean coverOnly){
        busy=true;notice="Загружаем обложку для проверки…";editor();int token=++request;
        app.network.execute(()->{
            BookLookup.Result result=new BookLookup().previewTitle(candidate);
            Bitmap bitmap=result.cover==null?null:BitmapFactory.decodeByteArray(result.cover,0,result.cover.length);
            runOnUiThread(()->{if(!active(token)){if(bitmap!=null)bitmap.recycle();return;}busy=false;notice=coverOnly?"Проверьте найденную обложку.":"Проверьте найденную книгу.";editor();
                if(coverOnly&&bitmap==null){missingCoverResult(true);return;}
                LinearLayout content=Ui.column(this);Ui.pad(content,20);
                ImageView image=new ImageView(this);image.setContentDescription("Обложка найденной книги");image.setScaleType(ImageView.ScaleType.FIT_CENTER);
                if(bitmap!=null){image.setImageBitmap(bitmap);content.addView(image,new LinearLayout.LayoutParams(-1,Ui.dp(this,180)));Ui.gap(content,16);}
                content.addView(Ui.text(this,result.title,18));Ui.gap(content,8);
                if(!result.author.isEmpty()){content.addView(Ui.muted(this,result.author,15));Ui.gap(content,8);}
                content.addView(Ui.muted(this,candidate.book.source+"\n"+candidate.editionNote(knownIsbn),14));Ui.gap(content,12);
                content.addView(Ui.muted(this,(knownIsbn.isEmpty()?"ISBN карточки останется без изменений.":"Введённый ISBN сохраним.")+(result.cover==null?"\nОбложка недоступна — её можно отсканировать.":draftCoverPath!=null?"\nВаша выбранная обложка будет сохранена.":""),14));
                ScrollView scroll=new ScrollView(this);scroll.addView(content);
                android.app.AlertDialog dialog=new android.app.AlertDialog.Builder(this).setTitle(coverOnly?"Найденная обложка":"Это ваша книга?").setView(scroll)
                    .setPositiveButton(coverOnly?"Использовать обложку":"Да, использовать",(d,w)->{if(coverOnly)saveScannedCover(isbn,result.cover);else applyTitleCandidate(result);})
                    .setNegativeButton("Назад",(d,w)->{if(candidates.candidates.size()>1)showTitleCandidates(candidates,coverOnly);}).create();
                dialog.setOnDismissListener(d->{image.setImageDrawable(null);if(bitmap!=null)bitmap.recycle();});dialog.show();
            });
        });
    }
    private void applyTitleCandidate(BookLookup.Result result){
        if(busy)return;
        String target=isbn;boolean needsCover=draftCoverPath==null&&result.cover!=null;
        busy=true;notice="Подставляем данные…";editor();int token=++request;
        app.io.execute(()->{
            String path=null;if(needsCover)try{path=writeCover(target,result.cover,false);}catch(java.io.IOException ignored){}
            String ready=path;
            runOnUiThread(()->{if(!active(token)){deleteDraftFile(ready);return;}
                if(ready!=null)useDraftCover(ready);draftTitle=result.title;if(!result.author.isEmpty())draftAuthor=result.author;
                busy=false;confirming=false;notice="Данные выбраны. Проверьте карточку перед сохранением.";editor();
            });
        });
    }
    private void skipFoundBook(boolean rescan){
        if(busy)return;String skipped=knownIsbn;clearDraftCover();++request;confirming=false;confirmationFlow=false;
        isbn=null;draftTitle="";draftAuthor="";draftCollections.clear();lastIsbn=rescan?"":skipped;lastTitle="";lastDuplicate=false;
        if(continuous||rescan){continuous=true;startScan();}else selection();
    }
    private String writeCover(String code,byte[] image,boolean existing)throws java.io.IOException{
        if(existing){app.store.saveCover(code,image);return null;}
        java.io.File file=java.io.File.createTempFile("book-cover-draft-",".jpg",getCacheDir());
        try(java.io.FileOutputStream out=new java.io.FileOutputStream(file)){out.write(image);}catch(java.io.IOException e){file.delete();throw e;}
        return file.getAbsolutePath();
    }
    private void useDraftCover(String path){clearDraftCover();draftCoverPath=path;}
    private void clearDraftCover(){String path=draftCoverPath;draftCoverPath=null;deleteDraftFile(path);}
    private void deleteDraftFile(String path){if(path!=null)app.io.execute(()->new java.io.File(path).delete());}
    private ImageButton copyTitleButton(){
        return Ui.iconButton(this,"copy","Копировать название",()->{
            String name=title==null?draftTitle:title.getText().toString();
            android.content.ClipboardManager clipboard=(android.content.ClipboardManager)getSystemService(CLIPBOARD_SERVICE);
            if(clipboard!=null){clipboard.setPrimaryClip(ClipData.newPlainText("Название книги",name));toast("Название скопировано");}
        });
    }
    private void addTitleInput(){
        LinearLayout row=new LinearLayout(this);row.setGravity(Gravity.CENTER_VERTICAL);
        row.setBackground(Ui.surface(this,Color.WHITE,Ui.LINE,12));title.setBackgroundColor(Color.TRANSPARENT);
        title.setOnFocusChangeListener((v,focused)->row.setBackground(Ui.surface(this,Color.WHITE,focused?Ui.GREEN:Ui.LINE,12)));
        row.addView(title,new LinearLayout.LayoutParams(0,-2,1));
        row.addView(copyTitleButton(),new LinearLayout.LayoutParams(Ui.dp(this,48),Ui.dp(this,48)));
        body.addView(row,new LinearLayout.LayoutParams(-1,-2));
    }
    private void addTitleHeading(){
        LinearLayout row=new LinearLayout(this);row.setGravity(Gravity.TOP);
        TextView name=Ui.heading(this,draftTitle);name.setTextSize(20);
        row.addView(name,new LinearLayout.LayoutParams(0,-2,1));
        row.addView(copyTitleButton(),new LinearLayout.LayoutParams(Ui.dp(this,48),Ui.dp(this,48)));
        body.addView(row,new LinearLayout.LayoutParams(-1,-2));
    }
    private void editor(){
        shell(editing?"ВАША КОЛЛЕКЦИЯ":"ДОБАВЛЕНИЕ КНИГИ",busy?"Подождите немного":!editing?"Добавить книгу":duplicateCoverOffer?"Уже в библиотеке":editMode?"Редактирование":editing?"О книге":"Карточка книги");
        TextView code=Ui.muted(this,knownIsbn.isEmpty()?"ISBN не указан":"ISBN "+knownIsbn,13);code.setGravity(Gravity.CENTER);if(editing&&!duplicateCoverOffer){body.addView(code);Ui.gap(body,16);}
        if(busy){
            LinearLayout progress=Ui.column(this);progress.setGravity(Gravity.CENTER);Ui.pad(progress,28);progress.setBackground(Ui.round(Ui.TINT,this));
            progress.addView(new Ui.ShelfArt(this),new LinearLayout.LayoutParams(-1,Ui.dp(this,150)));Ui.gap(progress,20);progress.addView(new ProgressBar(this),new LinearLayout.LayoutParams(Ui.dp(this,32),Ui.dp(this,32)));Ui.gap(progress,20);
            TextView note=Ui.text(this,notice,16);note.setGravity(Gravity.CENTER);progress.addView(note);body.addView(progress);return;
        }
        if(!editing||duplicateCoverOffer){simpleEditor();return;}
        ImageView cover=new ImageView(this);cover.setScaleType(ImageView.ScaleType.FIT_CENTER);cover.setContentDescription("Обложка книги");cover.setImageDrawable(new Ui.Symbol("book",Ui.GREEN));
        cover.setBackground(Ui.surface(this,Ui.TINT,Ui.LINE,4));cover.setClipToOutline(true);

        LinearLayout.LayoutParams coverLayout=new LinearLayout.LayoutParams(Ui.dp(this,112),Ui.dp(this,156));coverLayout.gravity=Gravity.CENTER_HORIZONTAL;body.addView(cover,coverLayout);String codeValue=isbn;String coverPath=draftCoverPath!=null?draftCoverPath:editing?app.store.cover(codeValue).getAbsolutePath():null;app.io.execute(()->{Bitmap bitmap=coverPath==null?null:BitmapFactory.decodeFile(coverPath);if(bitmap!=null)runOnUiThread(()->{if(!isDestroyed())cover.setImageBitmap(bitmap);});});Ui.gap(body,20);
        if(editing && !editMode){
            addTitleHeading();Ui.gap(body,10);
            TextView byline=Ui.muted(this,draftAuthor.isEmpty()?"Автор не указан":draftAuthor,15);byline.setGravity(Gravity.START);body.addView(byline);Ui.gap(body,24);
            Button status=Ui.button(this,readingStatus.label+"  ▾",this::chooseStatus);status.setTextSize(15);status.setTextColor(readingStatus.foreground);status.setBackground(Ui.round(readingStatus.background,this));body.addView(status,new LinearLayout.LayoutParams(-1,-2));Ui.gap(body,12);
            if(!readOn.isEmpty()){Ui.gap(body,10);TextView readDate=Ui.muted(this,"Прочитана "+ReadingDate.label(readOn),14);readDate.setGravity(Gravity.CENTER);body.addView(readDate);}
            Ui.gap(body,20);
            TextView collection=Ui.muted(this,"Коллекция",13);body.addView(collection);Ui.gap(body,4);
            TextView collectionName=Ui.text(this,"Без коллекции",15);body.addView(collectionName);Ui.gap(body,20);
            app.io.execute(()->{String label=BookCollection.label(app.store.collectionLabels(),codeValue);runOnUiThread(()->{if(!isDestroyed())collectionName.setText(label);});});
            body.addView(Ui.action(this,"Редактировать",()->{originalLocation=location;originalReadOn=readOn;originalTitle=draftTitle;originalAuthor=draftAuthor;originalCollections.clear();originalCollections.addAll(draftCollections);editMode=true;editor();}));Ui.gap(body,12);
            Button delete=Ui.button(this,"Удалить книгу",this::confirmDelete);delete.setTextColor(android.graphics.Color.rgb(157,55,65));delete.setBackgroundColor(Color.TRANSPARENT);body.addView(delete);
            return;
        }
        body.addView(Ui.quietAction(this,"Обработать обложку","crop",this::formatCurrentCover));Ui.gap(body,12);
        body.addView(Ui.action(this,"Сканировать обложку","document-scan",this::startCoverScan));Ui.gap(body,12);
        body.addView(Ui.action(this,"Загрузить обложку","image",()->{capture();pickCover.launch("image/*");}));Ui.gap(body,20);
        TextView note=Ui.muted(this,notice,14);body.addView(note);Ui.gap(body,20);
        body.addView(Ui.eyebrow(this,"НАЗВАНИЕ"));Ui.gap(body,8);title=Ui.input(this,"Название книги");title.setText(draftTitle);addTitleInput();Ui.gap(body,12);titleSearchButton();
        body.addView(Ui.eyebrow(this,"АВТОР"));Ui.gap(body,8);author=Ui.input(this,"Имя автора");author.setText(draftAuthor);body.addView(author);Ui.gap(body,24);
        LinearLayout properties=Ui.column(this);properties.setBackground(Ui.surface(this,Color.WHITE,Ui.LINE,12));
        properties.addView(Ui.property(this,"Местонахождение",location.label,Ui.INK,this::chooseLocation));Ui.divider(properties);
        properties.addView(Ui.property(this,"Дата прочтения",ReadingDate.label(readOn),Ui.INK,this::chooseReadDate));Ui.divider(properties);
        Ui.PropertyRow collectionRow=Ui.property(this,"Коллекция",draftCollections.isEmpty()?"Выбрать коллекцию":"Выбранная коллекция",Ui.INK,()->{
            capture();CollectionUi.forDraft(this,new java.util.LinkedHashSet<>(draftCollections),values->{draftCollections.clear();draftCollections.addAll(values);},this::editor);
        });properties.addView(collectionRow);body.addView(properties);Ui.gap(body,20);
        java.util.Set<String> selected=new java.util.LinkedHashSet<>(draftCollections);
        app.io.execute(()->{java.util.List<String> labels=new java.util.ArrayList<>();for(BookCollection group:app.store.collections())if(selected.contains(Long.toString(group.id)))labels.add(group.name);
            runOnUiThread(()->{if(!isDestroyed())collectionRow.setValue(labels.isEmpty()?"Выбрать коллекцию":String.join(", ",labels));});});
        body.addView(Ui.primary(this,"Сохранить книгу",this::save));Ui.gap(body,12);
        if(editing){Button cancel=Ui.textButton(this,"Отмена",this::cancelEdit);cancel.setGravity(Gravity.CENTER);body.addView(cancel);return;}
        if(Book.isLocalId(isbn))return;
        body.addView(Ui.quietAction(this,"Повторить поиск","refresh",()->{lookupEntry=false;lookup(null);}));Ui.gap(body,12);
        body.addView(Ui.quietAction(this,"Найти в интернете","globe",()->{
            try{startActivity(new Intent(Intent.ACTION_VIEW,Uri.parse("https://www.google.com/search?q="+Uri.encode(isbn+" книга"))));}
            catch(ActivityNotFoundException e){toast("На телефоне не найден браузер");}
        }));Ui.gap(body,12);
        body.addView(Ui.quietAction(this,"Заполнить по ссылке на страницу","link",this::pageDialog),new LinearLayout.LayoutParams(-1,-2));
    }
    private void simpleEditor(){
        TextView code=Ui.muted(this,knownIsbn.isEmpty()?"ISBN не указан":"ISBN "+knownIsbn,13);body.addView(code);Ui.gap(body,20);
        if(notice.contains("не найден")||notice.startsWith("Не удалось")){body.addView(Ui.muted(this,notice,14));Ui.gap(body,16);}
        body.addView(Ui.eyebrow(this,"НАЗВАНИЕ"));Ui.gap(body,8);title=Ui.input(this,"Название книги");title.setText(draftTitle);addTitleInput();Ui.gap(body,16);
        if(draftTitle.trim().isEmpty())titleSearchButton();
        body.addView(Ui.eyebrow(this,"АВТОР"));Ui.gap(body,8);author=Ui.input(this,"Имя автора");author.setText(draftAuthor);body.addView(author);Ui.gap(body,20);
        LinearLayout row=new LinearLayout(this);row.setGravity(Gravity.CENTER_VERTICAL);
        ImageView cover=new ImageView(this);cover.setContentDescription("Обложка книги");cover.setImageDrawable(new Ui.Symbol("book",Ui.GREEN));cover.setScaleType(ImageView.ScaleType.FIT_CENTER);cover.setBackground(Ui.surface(this,Ui.TINT,Ui.LINE,6));
        row.addView(cover,new LinearLayout.LayoutParams(Ui.dp(this,66),Ui.dp(this,91)));
        LinearLayout words=Ui.column(this);words.setPadding(Ui.dp(this,16),0,0,0);words.addView(Ui.text(this,"Обложка",16));Ui.gap(words,7);words.addView(Ui.muted(this,draftCoverPath==null?"Выберите способ добавления":"Обложка готова к сохранению",13));row.addView(words,new LinearLayout.LayoutParams(0,-2,1));body.addView(row);Ui.gap(body,18);
        if(draftCoverPath!=null){String path=draftCoverPath;app.io.execute(()->{Bitmap bitmap=BitmapFactory.decodeFile(path);if(bitmap!=null)runOnUiThread(()->{if(!isDestroyed())cover.setImageBitmap(bitmap);});});
            body.addView(Ui.quietAction(this,"Изменить обложку","edit",this::coverActions));
        }else coverButtons();
        Ui.gap(body,18);LinearLayout properties=Ui.column(this);properties.setBackground(Ui.surface(this,Color.WHITE,Ui.LINE,12));
        Ui.PropertyRow collection=Ui.property(this,"Коллекция",draftCollections.isEmpty()?"Без коллекции":"Выбрана",Ui.INK,()->{capture();CollectionUi.forDraft(this,new java.util.LinkedHashSet<>(draftCollections),values->{draftCollections.clear();draftCollections.addAll(values);},this::editor);});properties.addView(collection);Ui.divider(properties);
        java.util.Set<String> selected=new java.util.LinkedHashSet<>(draftCollections);app.io.execute(()->{java.util.List<String> labels=new java.util.ArrayList<>();for(BookCollection group:app.store.collections())if(selected.contains(Long.toString(group.id)))labels.add(group.name);runOnUiThread(()->{if(!isDestroyed())collection.setValue(labels.isEmpty()?"Без коллекции":String.join(", ",labels));});});
        properties.addView(Ui.property(this,"Местонахождение",location.label,Ui.INK,this::chooseLocation));Ui.divider(properties);
        properties.addView(Ui.property(this,"Статус",readingStatus==ReadingStatus.NONE?"Выбрать":readingStatus.label,Ui.INK,this::chooseStatus));
        if(readingStatus==ReadingStatus.READ||!readOn.isEmpty()){Ui.divider(properties);properties.addView(Ui.property(this,"Дата прочтения",ReadingDate.label(readOn),Ui.INK,this::chooseReadDate));}
        body.addView(properties);Ui.gap(body,20);body.addView(Ui.primary(this,continuous?"Сохранить и сканировать дальше":"Сохранить книгу",this::save));Ui.gap(body,8);
        if(duplicateCoverOffer)body.addView(Ui.textButton(this,continuous?"Продолжить без изменений":"Вернуться",()->{clearDraftCover();continueAfterDuplicate();}));
        else if(continuous)body.addView(Ui.textButton(this,"Пропустить книгу",()->skipFoundBook(false)));
    }
    private void coverButtons(){
        Button find=Ui.quietAction(this,"Найти в интернете","search",this::lookupMissingCover);find.setEnabled(!draftTitle.trim().isEmpty());body.addView(find);Ui.gap(body,10);
        title.addTextChangedListener(new android.text.TextWatcher(){public void beforeTextChanged(CharSequence s,int a,int c,int f){}public void onTextChanged(CharSequence s,int a,int b,int c){find.setEnabled(!s.toString().trim().isEmpty());}public void afterTextChanged(android.text.Editable e){}});
        body.addView(Ui.quietAction(this,"Сканировать обложку","document-scan",this::startCoverScan));Ui.gap(body,10);
        body.addView(Ui.quietAction(this,"Выбрать фото","image",()->{capture();pickCover.launch("image/*");}));
    }
    private void coverActions(){
        capture();new android.app.AlertDialog.Builder(this).setTitle("Обложка").setItems(new String[]{"Обработать фото","Сканировать заново","Выбрать другое фото","Найти в интернете"},(d,index)->{if(index==0)formatCurrentCover();else if(index==1)startCoverScan();else if(index==2)pickCover.launch("image/*");else lookupMissingCover();}).setNegativeButton("Отмена",null).show();
    }
    private void formatCurrentCover(){String path=draftCoverPath!=null?draftCoverPath:editing?app.store.cover(isbn).getAbsolutePath():null;if(path!=null&&new java.io.File(path).isFile())previewCover(Uri.fromFile(new java.io.File(path)),true);else toast("Сначала выберите фотографию");}
    private void pageDialog(){EditText input=Ui.input(this,"https://…");input.setInputType(android.text.InputType.TYPE_CLASS_TEXT|android.text.InputType.TYPE_TEXT_VARIATION_URI);
        new android.app.AlertDialog.Builder(this).setTitle("Ссылка на книгу").setView(Ui.dialogField(this,input)).setNegativeButton("Отмена",null).setPositiveButton("Заполнить",(d,w)->{lookupEntry=false;lookup(input.getText().toString().trim());}).show();}
    private void sharePage(String text){
        if(text==null)return;java.util.regex.Matcher m=java.util.regex.Pattern.compile("https://[^\\s]+").matcher(text);if(!m.find())return;String page=m.group();
        EditText code=Ui.input(this,"ISBN книги");
        android.app.AlertDialog dialog=new android.app.AlertDialog.Builder(this).setTitle("ISBN для проверки страницы").setView(Ui.dialogField(this,code)).setNegativeButton("Отмена",null).setPositiveButton("Добавить",null).create();
        dialog.setOnShowListener(d->dialog.getButton(-1).setOnClickListener(v->{String normalized=Isbn.normalize(code.getText().toString());if(normalized==null){code.setError("Проверьте ISBN");return;}dialog.dismiss();isbn=normalized;knownIsbn=normalized;addedAt=System.currentTimeMillis();lookupEntry=true;continuous=false;lookup(page);}));dialog.show();
    }
    private void save(){
        if(busy)return;capture();if(draftTitle.trim().isEmpty()){if(title!=null)title.setError("Укажите название");return;}
        String pendingCover=draftCoverPath;
        Book book=new Book(isbn,knownIsbn,draftTitle,draftAuthor,addedAt,readingStatus,location,readOn);busy=true;notice="Сохраняем на вашу полку…";editor();int token=++request;
        java.util.List<Long> groups=new java.util.ArrayList<>();for(String id:draftCollections)groups.add(Long.parseLong(id));boolean isNew=!editing;
        app.io.execute(()->{try{
            Book duplicate=isNew?app.store.find(book.id):null;
            if(isNew&&duplicate==null&&!book.isbn.isEmpty())duplicate=app.store.findByIsbn(book.isbn);
            if(duplicate!=null){Book saved=duplicate;runOnUiThread(()->{if(!active(token))return;busy=false;clearDraftCover();confirming=false;confirmationFlow=false;lastIsbn=book.isbn;lastTitle=saved.title;lastDuplicate=true;
                if(continuous){isbn=null;startScan();}else{setResult(RESULT_OK,new Intent().putExtra("savedIsbn",saved.id).putExtra("savedTitle",saved.title).putExtra("alreadySaved",true));finish();}});return;}
            android.database.sqlite.SQLiteDatabase db=app.store.getWritableDatabase();db.beginTransaction();
            try{
                if(isNew)app.store.saveWithCollections(book,groups);else{app.store.saveEdited(book);if(duplicateCoverOffer)app.store.setStatus(book.id,book.status);if(!draftCollections.equals(originalCollections))app.store.setBookCollections(book.id,groups);}
                if(pendingCover!=null){try(java.io.FileInputStream in=new java.io.FileInputStream(pendingCover);java.io.ByteArrayOutputStream out=new java.io.ByteArrayOutputStream()){byte[] buffer=new byte[8192];int n;while((n=in.read(buffer))!=-1)out.write(buffer,0,n);app.store.saveCover(book.id,out.toByteArray());}}
                db.setTransactionSuccessful();
            }finally{db.endTransaction();}
            runOnUiThread(()->{if(!active(token))return;busy=false;setResult(RESULT_OK,new Intent().putExtra("savedIsbn",book.id).putExtra("savedTitle",book.title));lastIsbn=book.isbn.isEmpty()?book.id:book.isbn;lastTitle=book.title;lastDuplicate=editing;clearDraftCover();confirming=false;confirmationFlow=false;
            if(duplicateCoverOffer){clearDraftCover();continueAfterDuplicate();}
            else if(editing){originalCollections.clear();originalCollections.addAll(draftCollections);editMode=false;originalLocation=location;originalReadOn=readOn;originalTitle=draftTitle;originalAuthor=draftAuthor;notice="Изменения сохранены";editor();}else if(continuous){isbn=null;startScan();}else{finish();}
        });}catch(Exception e){runOnUiThread(()->{if(active(token)){busy=false;lookupEntry=false;notice="Не удалось сохранить книгу. Проверьте свободное место.";editor();}});}});
    }
    private void leave(){
        if(manualEntry){hideManualKeyboard();finish();return;}
        if(!busy&&confirming){clearDraftCover();++request;finish();return;}
        if(busy){new android.app.AlertDialog.Builder(this).setMessage("Вернуться в библиотеку? Текущая операция может ещё выполняться.").setNegativeButton("Остаться",null).setPositiveButton("Вернуться",(d,w)->{++request;finish();}).show();return;}
        capture();
        if(editing && (editMode||duplicateCoverOffer)){
            if(!draftTitle.equals(originalTitle)||!draftAuthor.equals(originalAuthor)||!readOn.equals(originalReadOn)||location!=originalLocation||!draftCollections.equals(originalCollections)||draftCoverPath!=null)new android.app.AlertDialog.Builder(this).setMessage("Отменить изменения карточки?").setNegativeButton("Продолжить",null).setPositiveButton("Отменить",(d,w)->cancelEdit()).show();
            else cancelEdit();
            return;
        }
        if(isbn!=null&&!draftTitle.isEmpty()&&!editing){new android.app.AlertDialog.Builder(this).setMessage("Выйти без сохранения карточки?").setNegativeButton("Остаться",null).setPositiveButton("Выйти",(d,w)->finish()).show();}else finish();
    }
    private void chooseReadDate(){
        capture();java.util.Calendar c=java.util.Calendar.getInstance();
        if(!readOn.isEmpty())c.set(Integer.parseInt(readOn.substring(0,4)),readOn.length()>=7?Integer.parseInt(readOn.substring(5,7))-1:0,readOn.length()==10?Integer.parseInt(readOn.substring(8)):1);
        android.app.DatePickerDialog dialog=new android.app.DatePickerDialog(this,R.style.LibraryDialog,(picker,y,m,d)->{
            readOn=String.format(java.util.Locale.ROOT,"%04d-%02d-%02d",y,m+1,d);editor();
        },c.get(java.util.Calendar.YEAR),c.get(java.util.Calendar.MONTH),c.get(java.util.Calendar.DAY_OF_MONTH));
        dialog.setTitle("Дата прочтения");dialog.getDatePicker().setMaxDate(System.currentTimeMillis());
        dialog.setButton(android.content.DialogInterface.BUTTON_NEUTRAL,"Убрать дату",(d,w)->{readOn="";editor();});dialog.show();dialog.getButton(-2).setText("Отмена");
    }
    private void chooseLocation(){
        capture();BookLocation[] choices={BookLocation.HOME,BookLocation.PARENTS};String[] labels={"У меня","У родителей"};int selected=location==BookLocation.PARENTS?1:0;
        new android.app.AlertDialog.Builder(this).setTitle("Где находится книга?").setSingleChoiceItems(Ui.choices(this,labels,selected),selected,(dialog,index)->{
            dialog.dismiss();location=choices[index];editor();
        }).setNegativeButton("Отмена",null).show();
    }
    private void chooseStatus(){
        capture();
        ReadingStatus[] choices=ReadingStatus.values();String[] labels=new String[choices.length];
        for(int i=0;i<choices.length;i++)labels[i]=choices[i].label;
        new android.app.AlertDialog.Builder(this).setTitle("Статус чтения").setSingleChoiceItems(Ui.choices(this,labels,readingStatus.ordinal()),readingStatus.ordinal(),(dialog,index)->{
            dialog.dismiss();if(busy)return;ReadingStatus chosen=choices[index];if(chosen==readingStatus)return;
            if(!editing||duplicateCoverOffer||editMode){readingStatus=chosen;editor();return;}
            busy=true;notice="Сохраняем статус…";editor();String code=isbn;int token=++request;
            app.io.execute(()->{try{app.store.setStatus(code,chosen);runOnUiThread(()->{if(active(token)){readingStatus=chosen;busy=false;setResult(RESULT_OK);editor();}});}
            catch(Exception e){runOnUiThread(()->{if(active(token)){busy=false;editor();toast("Не удалось сохранить статус");}});}});
        }).setNegativeButton("Отмена",null).show();
    }
    private void confirmDelete(){
        new android.app.AlertDialog.Builder(this).setTitle("Удалить книгу?")
            .setMessage("«"+draftTitle+"» будет удалена из библиотеки вместе с обложкой. Это действие нельзя отменить.")
            .setNegativeButton("Отмена",null).setPositiveButton("Удалить",(dialog,which)->{
                if(busy)return;
                busy=true;notice="Удаляем книгу…";editor();int token=++request;String code=isbn;
                app.io.execute(()->{try{app.store.delete(java.util.Collections.singleton(code));runOnUiThread(()->{if(active(token)){setResult(RESULT_OK,new Intent().putExtra("deleted",true));finish();}});}
                catch(Exception e){runOnUiThread(()->{if(active(token)){busy=false;editor();toast("Не удалось удалить книгу. Попробуйте ещё раз.");}});}});
            }).show();
    }
    private void cancelEdit(){clearDraftCover();if(duplicateCoverOffer){finish();return;}draftCollections.clear();draftCollections.addAll(originalCollections);location=originalLocation;readOn=originalReadOn;draftTitle=originalTitle;draftAuthor=originalAuthor;editMode=false;editor();}
    private void startCoverScan(){
        if(isbn==null||busy)return;
        capture();scanCoverBook=isbn;pendingScanUri=null;
        busy=true;notice="Готовим сканер обложки…";editor();int token=++request;
        // BASE offers cropping and rotation without document-cleaning filters changing cover artwork.
        GmsDocumentScannerOptions options=new GmsDocumentScannerOptions.Builder()
            .setGalleryImportAllowed(false).setPageLimit(1)
            .setResultFormats(GmsDocumentScannerOptions.RESULT_FORMAT_JPEG)
            .setScannerMode(GmsDocumentScannerOptions.SCANNER_MODE_BASE).build();
        GmsDocumentScanning.getClient(options).getStartScanIntent(this)
            .addOnSuccessListener(this,sender->{
                if(!active(token))return;
                busy=false;notice="Наведите камеру на обложку и проверьте её границы.";editor();
                try{scanCover.launch(new IntentSenderRequest.Builder(sender).build());}
                catch(Exception e){coverScanUnavailable();}
            }).addOnFailureListener(this,error->{if(active(token))coverScanUnavailable();});
    }
    private void coverScanUnavailable(){
        scanCoverBook=null;busy=false;notice="Не удалось открыть сканер. Можно загрузить готовое фото.";editor();
        new android.app.AlertDialog.Builder(this).setTitle("Сканер пока недоступен")
            .setMessage("Для первого запуска подключитесь к интернету и проверьте обновления сервисов Google Play. Можно повторить попытку или выбрать готовое фото.")
            .setNegativeButton("Закрыть",null).setPositiveButton("Выбрать фото",(d,w)->pickCover.launch("image/*")).show();
    }
    private void previewScannedCover(Uri uri){previewCover(uri,false);}
    private void previewCover(Uri uri,boolean photo){
        if(isbn==null||busy||uri==null)return;
        capture();String target=isbn;busy=true;notice="Готовим фотографию…";editor();int token=++request;
        app.io.execute(()->{
            String path=null;
            try{path=writeCover(target,CoverImages.read(getContentResolver(),uri),false);String input=path;
                runOnUiThread(()->{if(!active(token)){deleteDraftFile(input);return;}busy=false;notice="";editor();
                    editCover.launch(new Intent(this,CoverEditActivity.class).putExtra("imagePath",input).putExtra("bookId",target));});
            }catch(Exception e){deleteDraftFile(path);runOnUiThread(()->{if(active(token)){busy=false;notice="Не удалось открыть фотографию. Выберите другое фото.";editor();}});}
        });
    }
    private void saveScannedCover(String target,byte[] image){
        if(busy||!target.equals(isbn))return;
        capture();busy=true;notice="Готовим обложку…";editor();int token=++request;
        app.io.execute(()->{try{String path=writeCover(target,image,false);runOnUiThread(()->{if(!active(token)){deleteDraftFile(path);return;}useDraftCover(path);busy=false;notice="";editor();});}
            catch(Exception e){runOnUiThread(()->{if(active(token)){busy=false;notice="Не удалось подготовить обложку.";editor();}});}});
    }
    private void importCover(Uri uri){previewCover(uri,true);}
    @Override protected void onDestroy(){if(isFinishing())clearDraftCover();super.onDestroy();}
    private boolean active(int token){return !isDestroyed()&&!isFinishing()&&token==request;}
    private void toast(String text){if(!isDestroyed())Toast.makeText(this,text,Toast.LENGTH_LONG).show();}
}
