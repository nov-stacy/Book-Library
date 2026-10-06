package ru.homebooks.library;

import android.app.*;
import android.content.*;
import android.content.res.ColorStateList;
import android.graphics.*;
import android.graphics.drawable.ColorDrawable;
import android.os.Bundle;
import android.text.InputType;
import android.view.*;
import android.view.inputmethod.InputMethodManager;
import android.widget.*;
import androidx.activity.ComponentActivity;
import java.util.*;

public final class ReadingStatsActivity extends ComponentActivity {
    private LibraryApp app;
    private LinearLayout content;
    private List<Book> books=Collections.emptyList();
    private Map<String,BookType> typeLabels=Collections.emptyMap();
    private Map<Integer,Integer> challenges=Collections.emptyMap();
    private int selectedYear,request;

    @Override public void onCreate(Bundle state){
        super.onCreate(state);app=(LibraryApp)getApplication();selectedYear=state==null?Calendar.getInstance().get(Calendar.YEAR):state.getInt("year",Calendar.getInstance().get(Calendar.YEAR));
        LinearLayout root=Ui.column(this);root.setBackgroundColor(Ui.PAPER);setContentView(root);Ui.insets(root);
        root.addView(Ui.toolbar(this,"Чтение","menu",()->Navigation.show(this,false)));
        ScrollView scroll=new ScrollView(this);scroll.setFillViewport(true);root.addView(scroll,new LinearLayout.LayoutParams(-1,0,1));
        content=Ui.column(this);content.setPadding(Ui.dp(this,16),Ui.dp(this,8),Ui.dp(this,16),Ui.dp(this,32));scroll.addView(content);
    }
    @Override protected void onResume(){super.onResume();load();}
    @Override protected void onSaveInstanceState(Bundle state){state.putInt("year",selectedYear);super.onSaveInstanceState(state);}
    private void load(){
        int token=++request;
        app.io.execute(()->{try{
            List<Book> loaded=app.store.all();Map<String,BookType> labels=app.store.bookTypeLabels();Map<Integer,Integer> goals=app.store.readingChallenges();
            runOnUiThread(()->{if(isDestroyed()||token!=request)return;books=loaded;typeLabels=labels;challenges=goals;render();});
        }catch(Exception e){runOnUiThread(()->{if(!isDestroyed())Toast.makeText(this,"Не удалось загрузить статистику чтения",Toast.LENGTH_LONG).show();});}});
    }
    private void render(){
        content.removeAllViews();ReadingStats.Year stats=ReadingStats.calculate(selectedYear,books,typeLabels);
        content.addView(yearSelector(stats));Ui.gap(content,12);content.addView(challengeCard(stats));Ui.gap(content,24);
        content.addView(Ui.eyebrow(this,"ЧТЕНИЕ ПО МЕСЯЦАМ"));Ui.gap(content,10);content.addView(monthCard(stats));Ui.gap(content,12);content.addView(bestCard(stats));Ui.gap(content,24);
        content.addView(Ui.eyebrow(this,"ПО ТИПАМ"));Ui.gap(content,10);content.addView(typesCard(stats));Ui.gap(content,24);
        Button history=Ui.quietAction(this,"Прочитанные книги","book-open",()->startActivity(new Intent(this,ReadActivity.class)));content.addView(history,new LinearLayout.LayoutParams(-1,-2));
    }
    private View yearSelector(ReadingStats.Year stats){
        LinearLayout row=new LinearLayout(this);row.setGravity(Gravity.CENTER_VERTICAL);row.setPadding(Ui.dp(this,16),Ui.dp(this,10),Ui.dp(this,12),Ui.dp(this,10));row.setMinimumHeight(Ui.dp(this,64));
        row.setBackground(new android.graphics.drawable.RippleDrawable(ColorStateList.valueOf(0x14735081),Ui.surface(this,Color.WHITE,Ui.LINE,16),null));row.setOnClickListener(v->showYears());row.setFocusable(true);
        LinearLayout words=Ui.column(this);row.addView(words,new LinearLayout.LayoutParams(0,-2,1));TextView year=Ui.heading(this,Integer.toString(selectedYear));year.setTextSize(21);words.addView(year);
        TextView summary=Ui.muted(this,yearSummary(stats,challenges.get(selectedYear)),13);words.addView(summary);
        ImageView down=new ImageView(this);down.setImageDrawable(new Ui.Symbol("down",Ui.GREEN));row.addView(down,new LinearLayout.LayoutParams(Ui.dp(this,22),Ui.dp(this,22)));
        row.setContentDescription("Год "+selectedYear+". "+summary.getText()+". Выбрать другой год");return row;
    }
    private View challengeCard(ReadingStats.Year stats){
        LinearLayout card=card();Integer goal=challenges.get(selectedYear);
        if(goal==null){
            card.addView(Ui.eyebrow(this,"ЧЕЛЛЕНДЖ НА "+selectedYear));Ui.gap(card,10);card.addView(Ui.heading(this,"Цель не задана"));Ui.gap(card,14);
            Button create=Ui.primary(this,"Создать челлендж",()->editGoal(null));card.addView(create,new LinearLayout.LayoutParams(-1,-2));return card;
        }
        LinearLayout top=new LinearLayout(this);top.setGravity(Gravity.CENTER_VERTICAL);card.addView(top);
        LinearLayout words=Ui.column(this);top.addView(words,new LinearLayout.LayoutParams(0,-2,1));words.addView(Ui.eyebrow(this,"ЧЕЛЛЕНДЖ НА "+selectedYear));
        TextView count=Ui.heading(this,stats.total+" из "+goal);count.setTextSize(30);words.addView(count);
        ImageButton edit=Ui.iconButton(this,"edit","Изменить цель на "+selectedYear,()->editGoal(goal));top.addView(edit,new LinearLayout.LayoutParams(Ui.dp(this,48),Ui.dp(this,48)));
        Ui.gap(card,14);ProgressBar progress=new ProgressBar(this,null,android.R.attr.progressBarStyleHorizontal);progress.setMax(goal);progress.setProgress(Math.min(stats.total,goal));progress.setProgressTintList(ColorStateList.valueOf(Ui.GREEN));progress.setProgressBackgroundTintList(ColorStateList.valueOf(Ui.LINE));card.addView(progress,new LinearLayout.LayoutParams(-1,Ui.dp(this,9)));Ui.gap(card,10);
        LinearLayout status=new LinearLayout(this);status.setGravity(Gravity.CENTER_VERTICAL);TextView left=Ui.text(this,statusLeft(stats.total,goal),14);left.setTypeface(Typeface.create("sans-serif-medium",Typeface.NORMAL));status.addView(left,new LinearLayout.LayoutParams(0,-2,1));TextView right=Ui.muted(this,statusRight(stats.total,goal),14);right.setGravity(Gravity.END);status.addView(right);card.addView(status);
        Ui.gap(card,16);Ui.divider(card);Ui.gap(card,14);
        LinearLayout pace=new LinearLayout(this);pace.addView(metric("План на месяц",monthlyPlan(goal,stats.total)),new LinearLayout.LayoutParams(0,-2,1));pace.addView(metric("Текущий темп",pace(stats.total)),new LinearLayout.LayoutParams(0,-2,1));card.addView(pace);
        return card;
    }
    private View metric(String label,String value){LinearLayout box=Ui.column(this);box.addView(Ui.muted(this,label,12));TextView number=Ui.text(this,value,18);number.setTypeface(Typeface.create("sans-serif-medium",Typeface.NORMAL));box.addView(number);return box;}
    private String monthlyPlan(int goal,int read){int value=ReadingStats.monthlyPlan(goal,read,selectedYear,Calendar.getInstance());return value==0?"Цель выполнена":value+" "+booksWord(value);}
    private String pace(int read){double value=ReadingStats.pace(read,selectedYear,Calendar.getInstance());return String.format(new Locale("ru"),"%.1f книги",value);}
    private String statusLeft(int read,int goal){
        if(read>=goal)return "Цель выполнена";int current=Calendar.getInstance().get(Calendar.YEAR);if(selectedYear<current)return "План не выполнен";if(selectedYear>current)return "Цель ещё не началась";
        return read>=ReadingStats.plannedByNow(goal,selectedYear,Calendar.getInstance())?"Идёте по плану":"Отставание от плана";
    }
    private String statusRight(int read,int goal){if(read>goal)return "На "+(read-goal)+" "+booksWord(read-goal)+" больше";if(read==goal)return "Точно по цели";if(selectedYear<Calendar.getInstance().get(Calendar.YEAR))return "Не хватило "+(goal-read)+" "+booksWord(goal-read);return "Осталось "+(goal-read)+" "+booksWord(goal-read);}
    private View monthCard(ReadingStats.Year stats){
        LinearLayout card=card();TextView selected=Ui.muted(this,"",14);int month=defaultMonth(stats);MonthlyChart chart=new MonthlyChart(this,stats.months,month,index->selected.setText(ReadingStats.MONTHS[index]+" · "+stats.months[index]+" "+booksWord(stats.months[index])));card.addView(chart,new LinearLayout.LayoutParams(-1,Ui.dp(this,184)));Ui.gap(card,8);selected.setText(ReadingStats.MONTHS[month]+" · "+stats.months[month]+" "+booksWord(stats.months[month]));selected.setGravity(Gravity.CENTER);card.addView(selected);return card;
    }
    private int defaultMonth(ReadingStats.Year stats){if(selectedYear==Calendar.getInstance().get(Calendar.YEAR))return Calendar.getInstance().get(Calendar.MONTH);for(int i=11;i>=0;i--)if(stats.months[i]>0)return i;return 0;}
    private View bestCard(ReadingStats.Year stats){LinearLayout card=card();LinearLayout row=new LinearLayout(this);row.setGravity(Gravity.CENTER_VERTICAL);card.addView(row);ImageView icon=new ImageView(this);icon.setImageDrawable(new Ui.Symbol("star",Ui.GREEN));row.addView(icon,new LinearLayout.LayoutParams(Ui.dp(this,28),Ui.dp(this,28)));LinearLayout words=Ui.column(this);words.setPadding(Ui.dp(this,12),0,0,0);row.addView(words,new LinearLayout.LayoutParams(0,-2,1));words.addView(Ui.muted(this,"Лучший месяц",13));TextView name=Ui.text(this,stats.bestMonths(),17);name.setTypeface(Typeface.create("sans-serif-medium",Typeface.NORMAL));words.addView(name);TextView value=Ui.text(this,stats.bestCount()==0?"—":stats.bestCount()+" "+booksWord(stats.bestCount()),16);value.setTextColor(Ui.GREEN);row.addView(value);return card;}
    private View typesCard(ReadingStats.Year stats){
        LinearLayout card=card();if(stats.types.isEmpty()){TextView empty=Ui.muted(this,"В этом году пока нет прочитанных книг",15);empty.setGravity(Gravity.CENTER);empty.setPadding(0,Ui.dp(this,16),0,Ui.dp(this,16));card.addView(empty);return card;}
        int shown=Math.min(3,stats.types.size()),other=0;for(int i=shown;i<stats.types.size();i++)other+=stats.types.get(i).count;
        for(int i=0;i<shown;i++){if(i>0){Ui.gap(card,12);Ui.divider(card);Ui.gap(card,12);}addType(card,stats.types.get(i),stats.total);}
        if(other>0){Ui.gap(card,12);Ui.divider(card);Ui.gap(card,12);addType(card,new ReadingStats.TypeCount("Другие","tag",0xFF9A8F9E,other),stats.total);}
        return card;
    }
    private void addType(LinearLayout card,ReadingStats.TypeCount type,int total){
        LinearLayout row=new LinearLayout(this);row.setGravity(Gravity.CENTER_VERTICAL);card.addView(row);ImageView icon=new ImageView(this);icon.setImageDrawable(new Ui.Symbol(type.icon,type.color));row.addView(icon,new LinearLayout.LayoutParams(Ui.dp(this,22),Ui.dp(this,22)));TextView name=Ui.text(this,type.name,15);name.setPadding(Ui.dp(this,10),0,Ui.dp(this,10),0);row.addView(name,new LinearLayout.LayoutParams(0,-2,1));TextView count=Ui.text(this,Integer.toString(type.count),15);count.setTypeface(Typeface.create("sans-serif-medium",Typeface.NORMAL));row.addView(count);Ui.gap(card,7);ProgressBar bar=new ProgressBar(this,null,android.R.attr.progressBarStyleHorizontal);bar.setMax(Math.max(1,total));bar.setProgress(type.count);bar.setProgressTintList(ColorStateList.valueOf(type.color));bar.setProgressBackgroundTintList(ColorStateList.valueOf(Ui.LINE));card.addView(bar,new LinearLayout.LayoutParams(-1,Ui.dp(this,6)));
    }
    private LinearLayout card(){LinearLayout card=Ui.column(this);card.setPadding(Ui.dp(this,16),Ui.dp(this,16),Ui.dp(this,16),Ui.dp(this,16));card.setBackground(Ui.surface(this,Color.WHITE,Ui.LINE,16));return card;}

    private void showYears(){
        Sheet sheet=sheet();sheet.panel.addView(Ui.heading(this,"Выберите год"));Ui.gap(sheet.panel,10);
        Set<Integer> found=new TreeSet<>(Collections.reverseOrder());found.add(Calendar.getInstance().get(Calendar.YEAR));found.add(selectedYear);found.addAll(challenges.keySet());
        for(Book book:books)if(book.status==ReadingStatus.READ&&book.readOn.length()>=4){try{found.add(Integer.parseInt(book.readOn.substring(0,4)));}catch(NumberFormatException ignored){}}
        ScrollView scroll=new ScrollView(this);LinearLayout list=Ui.column(this);scroll.addView(list);sheet.panel.addView(scroll,new LinearLayout.LayoutParams(-1,found.size()>5?Math.min(Ui.dp(this,420),getResources().getDisplayMetrics().heightPixels*2/3):-2));
        int index=0;for(int year:found){if(index++>0)Ui.divider(list);ReadingStats.Year stats=ReadingStats.calculate(year,books,typeLabels);LinearLayout row=new LinearLayout(this);row.setGravity(Gravity.CENTER_VERTICAL);row.setPadding(Ui.dp(this,4),Ui.dp(this,10),Ui.dp(this,4),Ui.dp(this,10));row.setMinimumHeight(Ui.dp(this,62));row.setOnClickListener(v->{selectedYear=year;sheet.dialog.dismiss();render();});row.setBackground(new android.graphics.drawable.RippleDrawable(ColorStateList.valueOf(0x14735081),Ui.round(year==selectedYear?Ui.TINT:Color.TRANSPARENT,this),null));
            LinearLayout words=Ui.column(this);row.addView(words,new LinearLayout.LayoutParams(0,-2,1));TextView title=Ui.text(this,Integer.toString(year),17);title.setTypeface(Typeface.create("sans-serif-medium",Typeface.NORMAL));if(year==selectedYear)title.setTextColor(Ui.GREEN);words.addView(title);words.addView(Ui.muted(this,yearSummary(stats,challenges.get(year)),13));if(year==selectedYear){ImageView check=new ImageView(this);check.setImageDrawable(new Ui.Symbol("check",Ui.GREEN));row.addView(check,new LinearLayout.LayoutParams(Ui.dp(this,22),Ui.dp(this,22)));}list.addView(row,new LinearLayout.LayoutParams(-1,-2));}
    }

    private Sheet sheet(){
        Dialog dialog=new Dialog(this);dialog.requestWindowFeature(Window.FEATURE_NO_TITLE);LinearLayout panel=Ui.column(this);panel.setPadding(Ui.dp(this,20),Ui.dp(this,18),Ui.dp(this,20),Ui.dp(this,24));panel.setBackground(Ui.surface(this,Color.WHITE,Color.WHITE,22));dialog.setContentView(panel);dialog.setCanceledOnTouchOutside(true);dialog.show();Window window=dialog.getWindow();if(window!=null){window.setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));window.addFlags(WindowManager.LayoutParams.FLAG_DIM_BEHIND);WindowManager.LayoutParams params=window.getAttributes();params.gravity=Gravity.BOTTOM;params.width=-1;params.height=-2;params.dimAmount=.32f;window.setAttributes(params);}return new Sheet(dialog,panel);
    }
    private void editGoal(Integer current){
        Sheet sheet=sheet();Dialog dialog=sheet.dialog;LinearLayout panel=sheet.panel;panel.addView(Ui.heading(this,current==null?"Новый челлендж":"Цель на "+selectedYear));Ui.gap(panel,6);panel.addView(Ui.muted(this,"Количество книг",13));Ui.gap(panel,8);
        EditText input=Ui.input(this,"");input.setInputType(InputType.TYPE_CLASS_NUMBER);input.setSingleLine(true);if(current!=null){input.setText(Integer.toString(current));input.setSelection(input.length());}panel.addView(input,new LinearLayout.LayoutParams(-1,-2));Ui.gap(panel,16);
        Button save=Ui.primary(this,"Сохранить",()->{String raw=input.getText().toString().trim();int goal;try{goal=Integer.parseInt(raw);}catch(NumberFormatException e){input.setError("Введите число от 1 до 999");return;}if(goal<1||goal>999){input.setError("Введите число от 1 до 999");return;}app.store.setReadingChallenge(selectedYear,goal);dialog.dismiss();load();});panel.addView(save,new LinearLayout.LayoutParams(-1,-2));Ui.gap(panel,8);
        LinearLayout actions=new LinearLayout(this);Button cancel=Ui.quietAction(this,"Отмена",dialog::dismiss);cancel.setGravity(Gravity.CENTER);actions.addView(cancel,new LinearLayout.LayoutParams(0,-2,1));if(current!=null){Button delete=Ui.dangerAction(this,"Удалить цель",()->{app.store.deleteReadingChallenge(selectedYear);dialog.dismiss();load();});delete.setGravity(Gravity.CENTER);LinearLayout.LayoutParams dp=new LinearLayout.LayoutParams(0,-2,1);dp.setMarginStart(Ui.dp(this,8));actions.addView(delete,dp);}panel.addView(actions);
        input.requestFocus();input.postDelayed(()->{InputMethodManager keyboard=(InputMethodManager)getSystemService(INPUT_METHOD_SERVICE);keyboard.showSoftInput(input,InputMethodManager.SHOW_IMPLICIT);},180);
    }
    private String yearSummary(ReadingStats.Year stats,Integer goal){if(goal==null)return stats.total+" "+booksWord(stats.total)+" · цель не задана";if(stats.total>=goal)return stats.total+" из "+goal+" · цель выполнена";return stats.total+" из "+goal+" · осталось "+(goal-stats.total);}
    private String booksWord(int number){int n=Math.abs(number)%100,n1=n%10;if(n>10&&n<20)return "книг";if(n1==1)return "книга";if(n1>=2&&n1<=4)return "книги";return "книг";}
    private static final class Sheet{final Dialog dialog;final LinearLayout panel;Sheet(Dialog dialog,LinearLayout panel){this.dialog=dialog;this.panel=panel;}}

    private interface MonthListener{void select(int index);}
    private static final class MonthlyChart extends View {
        private final int[] values;private final Paint paint=new Paint(Paint.ANTI_ALIAS_FLAG);private final MonthListener listener;private int selected;
        MonthlyChart(Context context,int[] values,int selected,MonthListener listener){super(context);this.values=values.clone();this.selected=selected;this.listener=listener;setFocusable(true);updateDescription();}
        @Override protected void onDraw(Canvas canvas){super.onDraw(canvas);float density=getResources().getDisplayMetrics().density,left=4*density,right=getWidth()-4*density,top=12*density,bottom=getHeight()-26*density;int max=1;for(int value:values)max=Math.max(max,value);float slot=(right-left)/12f,bar=Math.min(18*density,slot*.58f);paint.setTextAlign(Paint.Align.CENTER);paint.setTypeface(Typeface.create("sans-serif",Typeface.NORMAL));paint.setTextSize(10*density);
            paint.setColor(Ui.LINE);canvas.drawLine(left,bottom,right,bottom,paint);for(int i=0;i<12;i++){float x=left+slot*(i+.5f),height=(bottom-top)*values[i]/max;paint.setColor(i==selected?Ui.GREEN:0xFFB8A6C0);canvas.drawRoundRect(x-bar/2,bottom-height,x+bar/2,bottom,bar/2,bar/2,paint);paint.setColor(i==selected?Ui.GREEN:Ui.MUTED);canvas.drawText(ReadingStats.SHORT_MONTHS[i],x,getHeight()-6*density,paint);}}
        @Override public boolean onTouchEvent(android.view.MotionEvent event){if(event.getAction()!=android.view.MotionEvent.ACTION_UP)return true;selected=Math.max(0,Math.min(11,(int)(event.getX()*12/getWidth())));listener.select(selected);updateDescription();invalidate();performClick();return true;}
        @Override public boolean performClick(){super.performClick();return true;}
        private void updateDescription(){StringBuilder text=new StringBuilder("Прочитано по месяцам. ");for(int i=0;i<12;i++){if(i>0)text.append(", ");text.append(ReadingStats.MONTHS[i]).append(": ").append(values[i]);}setContentDescription(text.toString());}
    }
}
