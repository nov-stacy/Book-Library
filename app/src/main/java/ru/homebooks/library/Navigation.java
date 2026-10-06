package ru.homebooks.library;

import android.app.*;
import android.content.Intent;
import android.graphics.Color;
import android.view.*;
import android.widget.*;

final class Navigation {
    static void show(Activity activity,boolean collections){
        Dialog drawer=new Dialog(activity);drawer.requestWindowFeature(Window.FEATURE_NO_TITLE);
        ScrollView scroll=new ScrollView(activity);scroll.setFillViewport(true);scroll.setBackgroundColor(Ui.PAPER);
        LinearLayout panel=Ui.column(activity);panel.setPadding(Ui.dp(activity,16),Ui.dp(activity,24),Ui.dp(activity,16),Ui.dp(activity,24));scroll.addView(panel);
        LinearLayout heading=Ui.column(activity);heading.setPadding(Ui.dp(activity,16),0,Ui.dp(activity,16),0);
        heading.addView(Ui.heading(activity,"Моя библиотека"));Ui.gap(heading,8);heading.addView(Ui.muted(activity,"Книги, серии, коллекции и чтение",14));panel.addView(heading);Ui.gap(panel,24);
        Button books=item(activity,"Книги",()->open(activity,drawer,MainActivity.class));
        Button groups=item(activity,"Коллекции",()->open(activity,drawer,CollectionsActivity.class));
        Button types=item(activity,"Типы книг",()->open(activity,drawer,BookTypesActivity.class));
        Button series=item(activity,"Серии",()->open(activity,drawer,SeriesActivity.class));
        Button read=item(activity,"Чтение",()->open(activity,drawer,ReadingStatsActivity.class));
        Button settings=item(activity,"Настройки",()->open(activity,drawer,SettingsActivity.class));
        Button active=activity instanceof ReadActivity||activity instanceof ReadingStatsActivity?read:activity instanceof SettingsActivity?settings:activity instanceof SeriesActivity||activity instanceof SeriesDetailActivity||activity instanceof SeriesEditorActivity?series:activity instanceof BookTypesActivity?types:collections?groups:books;
        active.setTextColor(Ui.GREEN);active.setBackground(new android.graphics.drawable.RippleDrawable(android.content.res.ColorStateList.valueOf(0x22735081),Ui.round(Ui.TINT,activity),null));
        for(Button button:new Button[]{books,groups,types,series,read}){panel.addView(button,new LinearLayout.LayoutParams(-1,-2));Ui.gap(panel,8);}
        Ui.gap(panel,16);Ui.divider(panel);Ui.gap(panel,16);panel.addView(settings,new LinearLayout.LayoutParams(-1,-2));
        drawer.setContentView(scroll);drawer.setCanceledOnTouchOutside(true);Window window=drawer.getWindow();if(window!=null){window.setWindowAnimations(R.style.DrawerAnimation);window.setBackgroundDrawableResource(android.R.color.transparent);window.addFlags(WindowManager.LayoutParams.FLAG_DIM_BEHIND);WindowManager.LayoutParams params=window.getAttributes();params.gravity=Gravity.START|Gravity.TOP;params.width=Math.min(Ui.dp(activity,336),activity.getResources().getDisplayMetrics().widthPixels-Ui.dp(activity,48));params.height=-1;params.dimAmount=.32f;window.setAttributes(params);}drawer.show();
    }
    private static Button item(Activity activity,String label,Runnable click){
        Button button=Ui.textButton(activity,label,click);button.setTextColor(Ui.INK);button.setTextSize(17);
        button.setMinHeight(Ui.dp(activity,60));button.setMinimumHeight(Ui.dp(activity,60));
        button.setPadding(Ui.dp(activity,16),Ui.dp(activity,16),Ui.dp(activity,16),Ui.dp(activity,16));return button;
    }
    private static void open(Activity activity,Dialog drawer,Class<?> target){
        drawer.dismiss();
        if(activity.getClass()==target && !(activity instanceof MainActivity&&(activity.getIntent().hasExtra("collectionId")||activity.getIntent().hasExtra("bookTypeId"))))return;
        if(activity.getClass()==target){activity.startActivity(new Intent(activity,target));activity.finish();return;}
        activity.startActivity(new Intent(activity,target).addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP));
    }
}
