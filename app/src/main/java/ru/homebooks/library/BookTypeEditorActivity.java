package ru.homebooks.library;

import android.app.*;
import android.content.Intent;
import android.graphics.*;
import android.graphics.drawable.*;
import android.os.Bundle;
import android.text.*;
import android.view.*;
import android.widget.*;
import androidx.activity.ComponentActivity;
import java.util.*;

public final class BookTypeEditorActivity extends ComponentActivity {
    private LibraryApp app;
    private long typeId=-1;
    private List<BookType> types=new ArrayList<>();
    private EditText name,hex;
    private Button iconButton,save;
    private String selectedIcon="tag";
    private int selectedColor=BookType.PALETTE[0];
    private boolean syncing;

    @Override public void onCreate(Bundle state){
        super.onCreate(state);app=(LibraryApp)getApplication();typeId=getIntent().getLongExtra("typeId",-1);
        boolean editingRequested=typeId>=0;
        app.io.execute(()->{try{List<BookType> loaded=app.store.bookTypes();BookType editing=null;for(BookType type:loaded)if(type.id==typeId)editing=type;BookType ready=editing;runOnUiThread(()->{if(isDestroyed())return;if(editingRequested&&ready==null){Toast.makeText(this,"Тип книги уже удалён",Toast.LENGTH_LONG).show();finish();return;}types=loaded;if(ready!=null){selectedIcon=ready.icon;selectedColor=ready.color;}else{int[] suggestions=BookType.suggestions(types,1);if(suggestions.length>0)selectedColor=suggestions[0];}render(ready);});}
            catch(Exception e){runOnUiThread(()->{Toast.makeText(this,"Не удалось открыть тип книги",Toast.LENGTH_LONG).show();finish();});}});
    }

    private void render(BookType editing){
        LinearLayout root=Ui.column(this);root.setBackgroundColor(Ui.PAPER);setContentView(root);Ui.insets(root);root.addView(Ui.toolbar(this,editing==null?"Новый тип":"Редактировать тип",this::finish));
        ScrollView scroll=new ScrollView(this);scroll.setFillViewport(true);root.addView(scroll,new LinearLayout.LayoutParams(-1,0,1));LinearLayout body=Ui.column(this);body.setPadding(Ui.dp(this,16),Ui.dp(this,4),Ui.dp(this,16),Ui.dp(this,24));scroll.addView(body);
        body.addView(Ui.eyebrow(this,"НАЗВАНИЕ"));Ui.gap(body,8);name=Ui.input(this,"Например, Фотография");name.setSingleLine(true);name.setFilters(new InputFilter[]{new InputFilter.LengthFilter(80)});if(editing!=null)name.setText(editing.name);body.addView(name);Ui.gap(body,22);
        body.addView(Ui.eyebrow(this,"ИКОНКА"));Ui.gap(body,8);iconButton=Ui.collectionButton(this,BookType.iconLabel(selectedIcon),this::chooseIcon);updateIconButton();body.addView(iconButton);Ui.gap(body,22);
        body.addView(Ui.eyebrow(this,"ЦВЕТ"));Ui.gap(body,8);GridLayout colors=new GridLayout(this);colors.setColumnCount(6);int[] suggestions=BookType.suggestions(types,12);for(int color:suggestions)colors.addView(colorButton(color),new GridLayout.LayoutParams(GridLayout.spec(GridLayout.UNDEFINED,1,1f),GridLayout.spec(GridLayout.UNDEFINED,1,1f)));body.addView(colors,new LinearLayout.LayoutParams(-1,-2));Ui.gap(body,10);
        LinearLayout custom=new LinearLayout(this);custom.setGravity(Gravity.CENTER_VERTICAL);View sample=new View(this);sample.setTag("sample");custom.addView(sample,new LinearLayout.LayoutParams(Ui.dp(this,48),Ui.dp(this,48)));LinearLayout.LayoutParams sampleParams=(LinearLayout.LayoutParams)sample.getLayoutParams();sampleParams.setMarginEnd(Ui.dp(this,10));sample.setLayoutParams(sampleParams);
        hex=Ui.input(this,"#RRGGBB");hex.setSingleLine(true);hex.setInputType(android.text.InputType.TYPE_CLASS_TEXT|android.text.InputType.TYPE_TEXT_FLAG_CAP_CHARACTERS|android.text.InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS);hex.setFilters(new InputFilter[]{new InputFilter.LengthFilter(7)});custom.addView(hex,new LinearLayout.LayoutParams(0,-2,1));body.addView(custom);Ui.gap(body,10);
        body.addView(Ui.quietAction(this,"Выбрать другой цвет","palette",this::chooseCustomColor),new LinearLayout.LayoutParams(-1,-2));Ui.gap(body,22);
        LinearLayout preview=Ui.column(this);Ui.pad(preview,14);preview.setBackground(Ui.surface(this,Color.WHITE,Ui.LINE,12));TextView previewLabel=Ui.muted(this,"В списке типов",13);preview.addView(previewLabel);Ui.gap(preview,8);LinearLayout previewRow=new LinearLayout(this);previewRow.setGravity(Gravity.CENTER_VERTICAL);ImageView previewIcon=new ImageView(this);previewIcon.setTag("preview-icon");previewRow.addView(previewIcon,new LinearLayout.LayoutParams(Ui.dp(this,36),Ui.dp(this,36)));TextView previewName=Ui.text(this,editing==null?"Название типа":editing.name,16);previewName.setTag("preview-name");previewName.setPadding(Ui.dp(this,12),0,0,0);previewRow.addView(previewName);preview.addView(previewRow);body.addView(preview);
        LinearLayout footer=Ui.column(this);footer.setPadding(Ui.dp(this,16),Ui.dp(this,8),Ui.dp(this,16),Ui.dp(this,8));save=Ui.primary(this,"Сохранить",this::save);footer.addView(save,new LinearLayout.LayoutParams(-1,-2));Ui.gap(footer,10);footer.addView(Ui.quietAction(this,"Отмена",this::finish),new LinearLayout.LayoutParams(-1,-2));
        if(editing!=null){Ui.gap(footer,10);Button delete=Ui.button(this,"Удалить тип",this::confirmDelete);delete.setTextColor(0xFF984C52);delete.setBackground(new android.graphics.drawable.RippleDrawable(android.content.res.ColorStateList.valueOf(0x18984C52),Ui.round(0xFFF6EAEB,this),null));footer.addView(delete,new LinearLayout.LayoutParams(-1,-2));}root.addView(footer);
        hex.addTextChangedListener(new TextWatcher(){public void beforeTextChanged(CharSequence s,int a,int b,int c){}public void afterTextChanged(Editable e){}public void onTextChanged(CharSequence s,int a,int b,int c){if(syncing)return;try{selectedColor=BookType.parseColor(s.toString());hex.setError(null);syncColor();}catch(IllegalArgumentException ignored){save.setEnabled(false);}}});
        name.addTextChangedListener(new TextWatcher(){public void beforeTextChanged(CharSequence s,int a,int b,int c){}public void afterTextChanged(Editable e){}public void onTextChanged(CharSequence s,int a,int b,int c){updatePreview();}});
        syncColor();updatePreview();
    }

    private ImageButton colorButton(int color){ImageButton button=new ImageButton(this);button.setContentDescription("Выбрать цвет "+BookType.hex(color));button.setPadding(Ui.dp(this,7),Ui.dp(this,7),Ui.dp(this,7),Ui.dp(this,7));button.setMinimumHeight(Ui.dp(this,48));button.setImageDrawable(selectedColor==color?new Ui.Symbol("check",Color.WHITE):null);button.setBackground(colorBackground(color,selectedColor==color));button.setOnClickListener(v->{selectedColor=color;syncColor();});button.setTag(color);return button;}
    private Drawable colorBackground(int color,boolean selected){GradientDrawable swatch=Ui.round(color,this);if(selected)swatch.setStroke(Ui.dp(this,3),Ui.GREEN);return new InsetDrawable(swatch,Ui.dp(this,5));}
    private void syncColor(){
        syncing=true;hex.setText(BookType.hex(selectedColor));hex.setSelection(hex.length());syncing=false;
        View sample=((ViewGroup)hex.getParent()).findViewWithTag("sample");if(sample!=null)sample.setBackground(colorBackground(selectedColor,true));
        BookType owner=null;for(BookType type:types)if(type.id!=typeId&&type.color==selectedColor){owner=type;break;}
        if(owner!=null){hex.setError("Этот цвет уже использует тип «"+owner.name+"»");save.setEnabled(false);}else{hex.setError(null);save.setEnabled(true);}
        ViewGroup root=(ViewGroup)hex.getRootView();updateColorButtons(root);updatePreview();
    }
    private void updateColorButtons(ViewGroup parent){for(int i=0;i<parent.getChildCount();i++){View child=parent.getChildAt(i);if(child instanceof ImageButton&&child.getTag() instanceof Integer){int color=(Integer)child.getTag();((ImageButton)child).setImageDrawable(color==selectedColor?new Ui.Symbol("check",Color.WHITE):null);child.setBackground(colorBackground(color,color==selectedColor));}if(child instanceof ViewGroup)updateColorButtons((ViewGroup)child);}}
    private void updateIconButton(){if(iconButton==null)return;iconButton.setText(BookType.iconLabel(selectedIcon));Ui.Symbol icon=new Ui.Symbol(selectedIcon,Ui.GREEN);icon.setBounds(0,0,Ui.dp(this,20),Ui.dp(this,20));Ui.Symbol arrow=new Ui.Symbol("forward",Ui.GREEN);arrow.setBounds(0,0,Ui.dp(this,16),Ui.dp(this,16));iconButton.setCompoundDrawablesRelative(icon,null,arrow,null);}
    private void updatePreview(){
        if(name==null)return;View root=name.getRootView();ImageView previewIcon=root.findViewWithTag("preview-icon");TextView previewName=root.findViewWithTag("preview-name");if(previewIcon!=null)previewIcon.setImageDrawable(new Ui.Symbol(selectedIcon,selectedColor));if(previewName!=null)previewName.setText(name.getText().toString().trim().isEmpty()?"Название типа":name.getText().toString().trim());
    }

    private void chooseIcon(){
        LinearLayout frame=Ui.column(this);frame.setPadding(Ui.dp(this,24),Ui.dp(this,8),Ui.dp(this,24),0);EditText search=Ui.search(this,"Например, музыка или камера");frame.addView(search);Ui.gap(frame,10);
        GridView grid=new GridView(this);grid.setNumColumns(5);grid.setHorizontalSpacing(Ui.dp(this,7));grid.setVerticalSpacing(Ui.dp(this,7));grid.setStretchMode(GridView.STRETCH_COLUMN_WIDTH);List<Integer> visible=new ArrayList<>();for(int i=0;i<BookType.ICONS.length;i++)visible.add(i);
        BaseAdapter adapter=new BaseAdapter(){public int getCount(){return visible.size();}public Object getItem(int p){return visible.get(p);}public long getItemId(int p){return visible.get(p);}public View getView(int p,View reuse,ViewGroup parent){int index=visible.get(p);ImageButton button=new ImageButton(BookTypeEditorActivity.this);button.setContentDescription(BookType.ICON_LABELS[index]);button.setImageDrawable(new Ui.Symbol(BookType.ICONS[index],BookType.ICONS[index].equals(selectedIcon)?Ui.GREEN:Ui.INK));button.setBackground(Ui.surface(BookTypeEditorActivity.this,BookType.ICONS[index].equals(selectedIcon)?Ui.TINT:Color.WHITE,BookType.ICONS[index].equals(selectedIcon)?Ui.GREEN:Ui.LINE,12));Ui.pad(button,12);button.setMinimumHeight(Ui.dp(BookTypeEditorActivity.this,48));return button;}};
        grid.setAdapter(adapter);frame.addView(grid,new LinearLayout.LayoutParams(-1,Math.min(Ui.dp(this,320),getResources().getDisplayMetrics().heightPixels/3)));
        AlertDialog dialog=new AlertDialog.Builder(this).setTitle("Выберите иконку").setView(frame).setNegativeButton("Закрыть",null).create();grid.setOnItemClickListener((parent,view,position,id)->{selectedIcon=BookType.ICONS[visible.get(position)];dialog.dismiss();updateIconButton();updatePreview();});
        search.addTextChangedListener(new TextWatcher(){public void beforeTextChanged(CharSequence s,int a,int b,int c){}public void afterTextChanged(Editable e){}public void onTextChanged(CharSequence s,int a,int b,int c){String q=s.toString().trim().toLowerCase(Locale.ROOT);visible.clear();for(int i=0;i<BookType.ICONS.length;i++)if((BookType.ICON_LABELS[i]+" "+BookType.ICONS[i]).toLowerCase(Locale.ROOT).contains(q))visible.add(i);adapter.notifyDataSetChanged();}});dialog.show();
    }

    private void chooseCustomColor(){
        float[] hsv=new float[3];Color.colorToHSV(selectedColor,hsv);LinearLayout frame=Ui.column(this);frame.setPadding(Ui.dp(this,24),Ui.dp(this,8),Ui.dp(this,24),0);View preview=new View(this);frame.addView(preview,new LinearLayout.LayoutParams(-1,Ui.dp(this,54)));Ui.gap(frame,12);
        SeekBar hue=slider(Math.round(hsv[0]),360),sat=slider(Math.round(hsv[1]*100),100),value=slider(Math.round(hsv[2]*100),100);frame.addView(Ui.muted(this,"Оттенок",13));frame.addView(hue);frame.addView(Ui.muted(this,"Насыщенность",13));frame.addView(sat);frame.addView(Ui.muted(this,"Яркость",13));frame.addView(value);
        Runnable update=()->preview.setBackground(Ui.round(Color.HSVToColor(new float[]{hue.getProgress(),sat.getProgress()/100f,value.getProgress()/100f}),this));SeekBar.OnSeekBarChangeListener listener=new SeekBar.OnSeekBarChangeListener(){public void onProgressChanged(SeekBar b,int p,boolean from){update.run();}public void onStartTrackingTouch(SeekBar b){}public void onStopTrackingTouch(SeekBar b){}};hue.setOnSeekBarChangeListener(listener);sat.setOnSeekBarChangeListener(listener);value.setOnSeekBarChangeListener(listener);update.run();
        new AlertDialog.Builder(this).setTitle("Другой цвет").setView(frame).setNegativeButton("Отмена",null).setPositiveButton("Выбрать",(d,w)->{selectedColor=Color.HSVToColor(new float[]{hue.getProgress(),sat.getProgress()/100f,value.getProgress()/100f});syncColor();}).show();
    }
    private SeekBar slider(int progress,int max){SeekBar bar=new SeekBar(this);bar.setMax(max);bar.setProgress(progress);return bar;}

    private void save(){
        String value=name.getText().toString().trim();if(value.isEmpty()){name.setError("Введите название");return;}for(BookType type:types)if(type.id!=typeId&&type.name.equalsIgnoreCase(value)){name.setError("Тип с таким названием уже есть");return;}for(BookType type:types)if(type.id!=typeId&&type.color==selectedColor){hex.setError("Этот цвет уже использует тип «"+type.name+"»");return;}
        save.setEnabled(false);app.io.execute(()->{try{if(typeId<0)typeId=app.store.createBookType(value,selectedIcon,selectedColor);else app.store.updateBookType(typeId,value,selectedIcon,selectedColor);runOnUiThread(()->{setResult(RESULT_OK,new Intent().putExtra("typeId",typeId));finish();});}
            catch(Exception e){runOnUiThread(()->{save.setEnabled(true);if(e instanceof android.database.sqlite.SQLiteConstraintException)name.setError("Название или цвет уже используется");else Toast.makeText(this,"Не удалось сохранить тип",Toast.LENGTH_LONG).show();});}});
    }
    private void confirmDelete(){BookType type=null;for(BookType item:types)if(item.id==typeId)type=item;String label=type==null?name.getText().toString():type.name;new AlertDialog.Builder(this).setTitle("Удалить тип?").setMessage("«"+label+"» будет удалён. Книги останутся в библиотеке без типа.").setNegativeButton("Отмена",null).setPositiveButton("Удалить",(d,w)->{save.setEnabled(false);app.io.execute(()->{try{app.store.deleteBookType(typeId);runOnUiThread(()->{setResult(RESULT_OK);finish();});}catch(Exception e){runOnUiThread(()->{save.setEnabled(true);Toast.makeText(this,"Не удалось удалить тип",Toast.LENGTH_LONG).show();});}});}).show();}
}
