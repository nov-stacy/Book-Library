package ru.homebooks.library;

import android.content.Context;
import android.content.res.ColorStateList;
import android.graphics.*;
import android.graphics.drawable.*;
import android.view.*;
import android.widget.*;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;

final class Ui {
    static final int INK = Color.rgb(48, 40, 54), GREEN = Color.rgb(115, 80, 129), PAPER = Color.rgb(248, 246, 243);
    static final int MUTED = Color.rgb(124, 115, 128), TINT = Color.rgb(238, 231, 244), LINE = Color.rgb(231, 225, 233);
    static int dp(Context c, int n) { return Math.round(n * c.getResources().getDisplayMetrics().density); }
    static LinearLayout column(Context c) { LinearLayout v = new LinearLayout(c); v.setOrientation(LinearLayout.VERTICAL); return v; }
    static void pad(View v, int n) { int d = dp(v.getContext(), n); v.setPadding(d,d,d,d); }
    static void gap(LinearLayout parent, int n) { parent.addView(new View(parent.getContext()), new LinearLayout.LayoutParams(1, dp(parent.getContext(), n))); }
    static TextView text(Context c, String s, int size) {
        TextView v = new TextView(c); v.setText(s); v.setTextSize(size); v.setTextColor(INK); v.setLineSpacing(dp(c, 3), 1); return v;
    }
    static TextView muted(Context c, String s, int size) { TextView v = text(c,s,size); v.setTextColor(MUTED); return v; }
    static TextView eyebrow(Context c, String s) { TextView v = text(c,s,12); v.setLetterSpacing(.10f); v.setTextColor(GREEN); v.setTypeface(Typeface.DEFAULT, Typeface.BOLD); return v; }
    static TextView heading(Context c, String s) { TextView v = text(c, s, 23); v.setTypeface(Typeface.create("serif", Typeface.NORMAL)); return v; }
    static GradientDrawable round(int color, Context c) { GradientDrawable d = new GradientDrawable(); d.setColor(color); d.setCornerRadius(dp(c, 12)); return d; }
    static GradientDrawable surface(Context c,int fill,int stroke,int radius){
        GradientDrawable d=round(fill,c);d.setCornerRadius(dp(c,radius));d.setStroke(dp(c,1),stroke);return d;
    }
    static void divider(LinearLayout parent){View line=new View(parent.getContext());line.setBackgroundColor(LINE);parent.addView(line,new LinearLayout.LayoutParams(-1,dp(parent.getContext(),1)));}
    static Button collectionButton(Context c,String title,Runnable click){
        Button button=button(c,title,click);button.setTextSize(15);button.setGravity(Gravity.START|Gravity.CENTER_VERTICAL);
        button.setMinHeight(dp(c,48));button.setMinimumHeight(dp(c,48));button.setPadding(dp(c,14),dp(c,8),dp(c,14),dp(c,8));
        button.setTextColor(GREEN);button.setBackground(new android.graphics.drawable.RippleDrawable(ColorStateList.valueOf(0x14735081),surface(c,Color.WHITE,LINE,12),null));
        Symbol folder=new Symbol("folder",GREEN),arrow=new Symbol("forward",GREEN);folder.setBounds(0,0,dp(c,18),dp(c,18));arrow.setBounds(0,0,dp(c,16),dp(c,16));
        button.setCompoundDrawablesRelative(folder,null,arrow,null);button.setCompoundDrawablePadding(dp(c,10));return button;
    }
    static Button withIcon(Context c,Button button,String icon,int color){
        Symbol symbol=new Symbol(icon,color);symbol.setBounds(0,0,dp(c,20),dp(c,20));
        button.setCompoundDrawablesRelative(symbol,null,null,null);button.setCompoundDrawablePadding(dp(c,12));return button;
    }
    static Button quietAction(Context c,String title,String icon,Runnable click){return withIcon(c,quietAction(c,title,click),icon,0xFF68566F);}
    static Button dangerAction(Context c,String title,Runnable click){Button button=button(c,title,click);button.setTextColor(0xFF984C52);button.setBackground(new RippleDrawable(ColorStateList.valueOf(0x18984C52),round(0xFFF6EAEB,c),null));return button;}
    static Button action(Context c,String title,String icon,Runnable click){return withIcon(c,action(c,title,click),icon,Color.WHITE);}
    static Button quietAction(Context c,String title,Runnable click){
        Button b=button(c,title,click);b.setGravity(Gravity.START|Gravity.CENTER_VERTICAL);
        b.setTypeface(Typeface.create("sans-serif",Typeface.NORMAL));b.setTextColor(0xFF68566F);
        b.setBackground(new RippleDrawable(ColorStateList.valueOf(0x14735081),round(0xFFEEE7F0,c),null));return b;
    }
    static Button action(Context c,String title,Runnable click){
        Button b=button(c,title,click);b.setGravity(Gravity.START|Gravity.CENTER_VERTICAL);b.setTypeface(Typeface.create("sans-serif-medium",Typeface.NORMAL));
        Symbol arrow=new Symbol("forward",Color.WHITE);arrow.setBounds(0,0,dp(c,16),dp(c,16));b.setCompoundDrawablesRelative(null,null,arrow,null);b.setCompoundDrawablePadding(dp(c,12));return b;
    }
    static final class PropertyRow extends LinearLayout {
        final TextView valueView;
        final String label;
        PropertyRow(Context c,String label,String value,int color){super(c);this.label=label;valueView=text(c,value,15);valueView.setTextColor(color);}
        void setValue(String value){valueView.setText(value);setContentDescription(label+": "+value+". Изменить");}
        void setIcon(String icon,int color){Symbol symbol=new Symbol(icon,color);symbol.setBounds(0,0,dp(getContext(),16),dp(getContext(),16));valueView.setCompoundDrawablesRelative(symbol,null,null,null);valueView.setCompoundDrawablePadding(dp(getContext(),7));}
    }
    static PropertyRow property(Context c,String label,String value,int valueColor,Runnable click){
        PropertyRow row=new PropertyRow(c,label,value,valueColor);row.setGravity(Gravity.CENTER_VERTICAL);row.setMinimumHeight(dp(c,56));row.setPadding(dp(c,16),dp(c,12),dp(c,16),dp(c,12));
        row.setBackground(new RippleDrawable(ColorStateList.valueOf(0x14735081),round(Color.TRANSPARENT,c),null));row.setOnClickListener(v->click.run());row.setFocusable(true);row.setContentDescription(label+": "+value+". Изменить");
        LinearLayout words=column(c);row.addView(words,new LinearLayout.LayoutParams(0,-2,1));
        TextView name=muted(c,label,13);words.addView(name);gap(words,4);
        words.addView(row.valueView);
        ImageView arrow=new ImageView(c);arrow.setImageDrawable(new Symbol("forward",MUTED));LinearLayout.LayoutParams ap=new LinearLayout.LayoutParams(dp(c,16),dp(c,16));ap.setMarginStart(dp(c,12));row.addView(arrow,ap);return row;
    }
    static BaseAdapter choices(Context c,String[] labels,int selected){return new BaseAdapter(){
        public int getCount(){return labels.length;}public Object getItem(int p){return labels[p];}public long getItemId(int p){return p;}
        public View getView(int p,View reuse,ViewGroup parent){return choice(c,labels[p],p==selected);}
    };}
    static CheckedTextView choice(Context c,String label,boolean checked){
        CheckedTextView row=new CheckedTextView(c);row.setTextSize(16);row.setTextColor(checked?GREEN:INK);row.setTypeface(Typeface.create("sans-serif",Typeface.NORMAL));row.setText(label);row.setGravity(Gravity.CENTER_VERTICAL);row.setMinHeight(dp(c,56));row.setPadding(dp(c,24),dp(c,14),dp(c,24),dp(c,14));
        row.setLineSpacing(dp(c,3),1);row.setChecked(checked);row.setCheckMarkDrawable(checked?new Symbol("check",GREEN,dp(c,20)):null);
        row.setBackground(new RippleDrawable(ColorStateList.valueOf(0x14735081),round(checked?0xFFF1ECF3:Color.TRANSPARENT,c),null));return row;
    }
    static Button button(Context c, String s, Runnable click) { return button(c,s,false,click); }
    static Button primary(Context c, String s, Runnable click) { return button(c,s,true,click); }
    private static Button button(Context c, String s, boolean primary, Runnable click) {
        Button b = new Button(c); b.setText(s); b.setTextSize(15); b.setAllCaps(false); b.setTypeface(Typeface.create("sans-serif-medium",Typeface.NORMAL));
        b.setTextColor(new ColorStateList(new int[][]{new int[]{-android.R.attr.state_enabled}, new int[]{}}, new int[]{0xFF655A6C, Color.WHITE}));
        StateListDrawable fill=new StateListDrawable();fill.addState(new int[]{-android.R.attr.state_enabled},round(LINE,c));fill.addState(new int[]{},round(primary?GREEN:0xFF5E426B,c));
        b.setBackground(new RippleDrawable(ColorStateList.valueOf(0x337D628F),fill,null));
        b.setMinHeight(dp(c,52)); b.setMinimumHeight(dp(c,52)); b.setPadding(dp(c,16),dp(c,12),dp(c,16),dp(c,12));
        b.setStateListAnimator(null); b.setOnClickListener(v -> click.run()); return b;
    }
    static EditText input(Context c, String hint) {
        EditText v = new EditText(c); v.setTextSize(16); v.setTextColor(INK); v.setHintTextColor(MUTED); v.setHint(hint);
        StateListDrawable background=new StateListDrawable();
        background.addState(new int[]{android.R.attr.state_focused},surface(c,Color.WHITE,GREEN,12));
        background.addState(new int[]{},surface(c,Color.WHITE,LINE,12));
        v.setBackground(background); v.setPadding(dp(c,16),dp(c,12),dp(c,16),dp(c,12)); v.setMinHeight(dp(c,48)); return v;
    }
    static Button editButton(Context c,String description,Runnable click){
        Button button=button(c,"Изменить",click);button.setContentDescription(description);
        button.setMinWidth(dp(c,104));button.setMinimumWidth(dp(c,104));
        button.setMinHeight(dp(c,48));button.setMinimumHeight(dp(c,48));button.setSingleLine(true);button.setPadding(dp(c,14),dp(c,10),dp(c,14),dp(c,10));
        return button;
    }
    static EditText search(Context c,String hint){
        EditText input=input(c,hint);input.setSingleLine(true);input.setTextSize(14);searchIcon(input);return input;
    }
    static Button textButton(Context c,String text,Runnable click){
        Button button=button(c,text,click);button.setTextColor(GREEN);button.setGravity(Gravity.START|Gravity.CENTER_VERTICAL);
        button.setPadding(0,dp(c,10),0,dp(c,10));button.setMinWidth(dp(c,48));button.setMinimumWidth(dp(c,48));
        button.setBackground(new RippleDrawable(ColorStateList.valueOf(0x14735081),round(Color.TRANSPARENT,c),null));return button;
    }
    static View dialogField(Context c,View field){
        LinearLayout frame=column(c);frame.setPadding(dp(c,24),dp(c,16),dp(c,24),dp(c,8));frame.addView(field,new LinearLayout.LayoutParams(-1,-2));return frame;
    }
    static void searchIcon(EditText input){
        Context c=input.getContext();Symbol icon=new Symbol("search",MUTED);icon.setBounds(0,0,dp(c,18),dp(c,18));input.setCompoundDrawablesRelative(icon,null,null,null);input.setCompoundDrawablePadding(dp(c,10));
    }
    static ImageButton iconButton(Context c, String icon, String description, Runnable click) {
        ImageButton b = new ImageButton(c); b.setMinimumWidth(dp(c,48));b.setMinimumHeight(dp(c,48));b.setScaleType(ImageView.ScaleType.FIT_CENTER);b.setImageDrawable(new Symbol(icon,GREEN)); b.setContentDescription(description);
        b.setBackground(new RippleDrawable(ColorStateList.valueOf(0x227D628F),round(Color.TRANSPARENT,c),null)); pad(b,13);
        b.setOnClickListener(v -> click.run()); return b;
    }
    static LinearLayout toolbar(Context c, String title, Runnable back) {
        return toolbar(c,title,"back",back);
    }
    static LinearLayout toolbar(Context c,String title,String icon,Runnable click){
        LinearLayout bar = new LinearLayout(c); bar.setGravity(Gravity.CENTER_VERTICAL);
        bar.setPadding(dp(c,12),dp(c,4),dp(c,16),dp(c,4));
        ImageButton arrow = iconButton(c,icon,icon.equals("menu")?"Открыть меню":"Назад",click);
        bar.addView(arrow,new LinearLayout.LayoutParams(dp(c,48),dp(c,48)));
        TextView name=heading(c,title);name.setPadding(dp(c,8),0,0,0);name.setMaxLines(2);name.setEllipsize(android.text.TextUtils.TruncateAt.END);
        bar.addView(name,new LinearLayout.LayoutParams(0,-2,1));return bar;
    }
    static void insets(View root) {
        ViewCompat.setOnApplyWindowInsetsListener(root, (v, insets) -> {
            androidx.core.graphics.Insets edges = insets.getInsets(WindowInsetsCompat.Type.systemBars() | WindowInsetsCompat.Type.ime());
            v.setPadding(edges.left, edges.top, edges.right, edges.bottom); return insets;
        }); ViewCompat.requestApplyInsets(root);
    }
    static final class Symbol extends Drawable {
        private final String kind; private final int size; private final Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
        Symbol(String kind,int color){this(kind,color,24);}
        Symbol(String kind, int color,int size) { this.size=size;this.kind = kind; p.setColor(color); p.setStyle(Paint.Style.STROKE); p.setStrokeWidth(1.7f); p.setStrokeCap(Paint.Cap.ROUND); p.setStrokeJoin(Paint.Join.ROUND); }
        public void draw(Canvas c) {
            c.save(); c.translate(getBounds().left,getBounds().top); c.scale(getBounds().width()/24f,getBounds().height()/24f);
            Path path = new Path();
            switch(kind) {
                case "globe": c.drawCircle(12,12,9,p);c.drawOval(8,3,16,21,p);c.drawLine(3,12,21,12,p);break;
                case "refresh": c.drawArc(4,4,20,20,45,285,false,p);path.moveTo(20,4);path.lineTo(20,10);path.lineTo(14,10);c.drawPath(path,p);break;
                case "link": c.save();c.rotate(45,12,12);c.drawRoundRect(8,2,16,13,4,4,p);c.drawRoundRect(8,11,16,22,4,4,p);c.restore();break;
                case "crop": path.moveTo(7,3);path.lineTo(7,17);path.lineTo(21,17);path.moveTo(3,7);path.lineTo(17,7);path.lineTo(17,21);c.drawPath(path,p);break;
                case "image": c.drawRoundRect(3,3,21,21,2,2,p);c.drawCircle(8,8,1.5f,p);path.moveTo(3,18);path.lineTo(10,11);path.lineTo(14,15);path.lineTo(17,12);path.lineTo(21,16);c.drawPath(path,p);break;
                case "document-scan":
                    for(int i=0;i<4;i++){c.save();c.rotate(i*90,12,12);path.reset();path.moveTo(3,8);path.lineTo(3,3);path.lineTo(8,3);c.drawPath(path,p);c.restore();}c.drawLine(7,12,17,12,p);break;
                case "copy": c.drawRoundRect(8,8,21,21,2,2,p);path.moveTo(5,16);path.lineTo(3,16);path.lineTo(3,3);path.lineTo(16,3);path.lineTo(16,5);c.drawPath(path,p);break;
                case "document": path.moveTo(5,3);path.lineTo(15,3);path.lineTo(20,8);path.lineTo(20,21);path.lineTo(5,21);path.close();path.moveTo(15,3);path.lineTo(15,8);path.lineTo(20,8);path.moveTo(9,13);path.lineTo(16,13);path.moveTo(9,17);path.lineTo(16,17);c.drawPath(path,p);break;
                case "folder": path.moveTo(3,6);path.lineTo(9,6);path.lineTo(11,9);path.lineTo(21,9);path.lineTo(21,20);path.lineTo(3,20);path.close();c.drawPath(path,p);break;
                case "edit": path.moveTo(4,16);path.lineTo(4,20);path.lineTo(8,20);path.lineTo(20,8);path.lineTo(16,4);path.close();c.drawPath(path,p);c.drawLine(13,7,17,11,p);break;
                case "down": path.moveTo(5,9);path.lineTo(12,16);path.lineTo(19,9);c.drawPath(path,p);break;
                case "forward": path.moveTo(9,5);path.lineTo(16,12);path.lineTo(9,19);c.drawPath(path,p);break;
                case "menu": c.drawLine(4,6,20,6,p);c.drawLine(4,12,20,12,p);c.drawLine(4,18,20,18,p);break;
                case "more": c.drawCircle(12,5,1,p);c.drawCircle(12,12,1,p);c.drawCircle(12,19,1,p);break;
                case "settings":
                    c.drawCircle(12,12,6,p);c.drawCircle(12,12,2,p);
                    for(int i=0;i<8;i++){c.save();c.rotate(i*45,12,12);c.drawLine(12,3,12,5,p);c.restore();}break;
                case "check": path.moveTo(5,12);path.lineTo(10,17);path.lineTo(19,7);c.drawPath(path,p);break;
                case "back": path.moveTo(14,5);path.lineTo(7,12);path.lineTo(14,19); c.drawPath(path,p); break;
                case "export": c.drawLine(12,3,12,15,p);path.moveTo(7,10);path.lineTo(12,15);path.lineTo(17,10);c.drawPath(path,p);path.reset();path.moveTo(4,16);path.lineTo(4,21);path.lineTo(20,21);path.lineTo(20,16);c.drawPath(path,p);break;
                case "scan":
                    for (int i=0;i<4;i++) {c.save();c.rotate(i*90,12,12);path.reset();path.moveTo(3,8);path.lineTo(3,3);path.lineTo(8,3);c.drawPath(path,p);c.restore();}
                    for(int x=7;x<=17;x+=3)c.drawLine(x,8,x,16,p); break;
                case "search": c.drawCircle(10,10,6,p);c.drawLine(15,15,21,21,p);break;
                case "code": path.moveTo(8,7);path.lineTo(3,12);path.lineTo(8,17);path.moveTo(16,7);path.lineTo(21,12);path.lineTo(16,17);path.moveTo(14,4);path.lineTo(10,20);c.drawPath(path,p);break;
                case "sparkles": c.drawLine(12,2,12,10,p);c.drawLine(8,6,16,6,p);c.drawLine(6,13,6,20,p);c.drawLine(2,16.5f,10,16.5f,p);c.drawLine(18,10,18,15,p);c.drawLine(15.5f,12.5f,20.5f,12.5f,p);break;
                case "pencil": c.save();c.rotate(-45,12,12);c.drawRoundRect(9,2,15,19,2,2,p);path.moveTo(9,19);path.lineTo(12,23);path.lineTo(15,19);c.drawPath(path,p);c.restore();break;
                case "scissors": c.drawCircle(6,7,3,p);c.drawCircle(6,17,3,p);c.drawLine(8.5f,9,21,18,p);c.drawLine(8.5f,15,21,6,p);break;
                case "game": c.drawRoundRect(3,7,21,18,4,4,p);c.drawLine(8,10,8,15,p);c.drawLine(5.5f,12.5f,10.5f,12.5f,p);c.drawCircle(16,11,1,p);c.drawCircle(18,14,1,p);break;
                case "book-open": path.moveTo(3,5);path.quadTo(8,4,12,8);path.quadTo(16,4,21,5);path.lineTo(21,19);path.quadTo(16,18,12,21);path.quadTo(8,18,3,19);path.close();c.drawPath(path,p);c.drawLine(12,8,12,21,p);break;
                case "images": c.drawRoundRect(5,3,21,18,2,2,p);c.drawCircle(10,8,1.5f,p);path.moveTo(5,16);path.lineTo(11,10);path.lineTo(15,14);path.lineTo(18,11);path.lineTo(21,14);path.moveTo(3,7);path.lineTo(3,21);path.lineTo(18,21);c.drawPath(path,p);break;
                case "palette": c.drawOval(3,3,21,20,p);c.drawCircle(8,8,1,p);c.drawCircle(13,6,1,p);c.drawCircle(17,9,1,p);c.drawCircle(8,14,1,p);c.drawOval(13,13,19,18,p);break;
                case "library": c.drawRect(4,4,8,20,p);c.drawRect(10,3,14,20,p);c.save();c.rotate(-10,18,12);c.drawRect(16,4,20,20,p);c.restore();break;
                case "tag": path.moveTo(3,5);path.lineTo(13,5);path.lineTo(21,13);path.lineTo(13,21);path.lineTo(3,11);path.close();c.drawPath(path,p);c.drawCircle(8,10,1.5f,p);break;
                case "brush": c.drawLine(8,17,18,4,p);c.drawRoundRect(15,3,20,8,2,2,p);path.moveTo(9,15);path.quadTo(3,15,4,21);path.quadTo(10,22,11,17);c.drawPath(path,p);break;
                case "camera": c.drawRoundRect(3,7,21,20,2,2,p);path.moveTo(8,7);path.lineTo(10,4);path.lineTo(15,4);path.lineTo(17,7);c.drawPath(path,p);c.drawCircle(12,13,4,p);break;
                case "film": c.drawRoundRect(3,4,21,20,2,2,p);for(int y=7;y<=17;y+=5){c.drawCircle(6,y,1,p);c.drawCircle(18,y,1,p);}c.drawLine(9,4,9,20,p);c.drawLine(15,4,15,20,p);break;
                case "music": c.drawLine(9,5,9,17,p);c.drawLine(9,5,19,3,p);c.drawLine(19,3,19,15,p);c.drawCircle(6,18,3,p);c.drawCircle(16,16,3,p);break;
                case "heart": path.moveTo(12,21);path.cubicTo(9,17,3,14,3,8);path.cubicTo(3,3,10,2,12,7);path.cubicTo(14,2,21,3,21,8);path.cubicTo(21,14,15,17,12,21);c.drawPath(path,p);break;
                case "star": for(int i=0;i<10;i++){double a=-Math.PI/2+i*Math.PI/5;float radius=i%2==0?9:4;float px=12+(float)Math.cos(a)*radius,py=12+(float)Math.sin(a)*radius;if(i==0)path.moveTo(px,py);else path.lineTo(px,py);}path.close();c.drawPath(path,p);break;
                case "map": path.moveTo(3,6);path.lineTo(9,3);path.lineTo(15,6);path.lineTo(21,3);path.lineTo(21,18);path.lineTo(15,21);path.lineTo(9,18);path.lineTo(3,21);path.close();c.drawPath(path,p);c.drawLine(9,3,9,18,p);c.drawLine(15,6,15,21,p);break;
                case "compass": c.drawCircle(12,12,9,p);path.moveTo(15,7);path.lineTo(13,13);path.lineTo(7,17);path.lineTo(10,10);path.close();c.drawPath(path,p);break;
                case "cooking": c.drawRoundRect(4,9,20,19,2,2,p);c.drawLine(2,9,22,9,p);c.drawLine(9,5,15,5,p);c.drawLine(6,22,18,22,p);break;
                case "leaf": path.moveTo(4,20);path.cubicTo(4,8,10,3,21,3);path.cubicTo(21,14,16,20,4,20);c.drawPath(path,p);c.drawLine(4,20,17,7,p);break;
                case "flower": c.drawCircle(12,12,2,p);for(int i=0;i<6;i++){c.save();c.rotate(i*60,12,12);c.drawOval(10,3,14,10,p);c.restore();}c.drawLine(12,14,12,22,p);break;
                case "cat": case "dog": path.moveTo(5,9);path.lineTo(5,4);path.lineTo(9,7);path.quadTo(12,5,15,7);path.lineTo(19,4);path.lineTo(19,14);path.quadTo(18,21,12,21);path.quadTo(6,21,5,14);path.close();c.drawPath(path,p);c.drawCircle(9,12,1,p);c.drawCircle(15,12,1,p);c.drawLine(10,17,14,17,p);break;
                case "sport": c.drawLine(7,12,17,12,p);c.drawLine(5,8,5,16,p);c.drawLine(3,9,3,15,p);c.drawLine(19,8,19,16,p);c.drawLine(21,9,21,15,p);break;
                case "puzzle": path.moveTo(4,4);path.lineTo(10,4);path.quadTo(8,9,12,9);path.quadTo(16,9,14,4);path.lineTo(20,4);path.lineTo(20,10);path.quadTo(15,8,15,12);path.quadTo(15,16,20,14);path.lineTo(20,20);path.lineTo(4,20);path.close();c.drawPath(path,p);break;
                case "science": path.moveTo(9,3);path.lineTo(15,3);path.moveTo(10,3);path.lineTo(10,10);path.lineTo(5,20);path.lineTo(19,20);path.lineTo(14,10);path.lineTo(14,3);path.moveTo(7,16);path.lineTo(17,16);c.drawPath(path,p);break;
                case "history": c.drawCircle(12,12,9,p);c.drawLine(12,6,12,12,p);c.drawLine(12,12,17,15,p);break;
                case "graduation": path.moveTo(2,9);path.lineTo(12,4);path.lineTo(22,9);path.lineTo(12,14);path.close();path.moveTo(6,11);path.lineTo(6,17);path.quadTo(12,21,18,17);path.lineTo(18,11);c.drawPath(path,p);break;
                case "tools": c.drawCircle(7,17,2,p);path.moveTo(8,15);path.lineTo(17,6);path.quadTo(15,2,20,3);path.quadTo(21,8,17,7);path.lineTo(9,18);c.drawPath(path,p);break;
                default: c.drawRoundRect(4,3,20,21,2,2,p);c.drawLine(8,3,8,21,p);c.drawLine(11,8,16,8,p);c.drawLine(11,11,16,11,p);
            } c.restore();
        }
        public int getIntrinsicWidth(){return size;}public int getIntrinsicHeight(){return size;}
        public void setAlpha(int a) { p.setAlpha(a); } public void setColorFilter(ColorFilter f) { p.setColorFilter(f); } public int getOpacity() { return PixelFormat.TRANSLUCENT; }
    }
    static final class ShelfArt extends View {
        private final Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
        ShelfArt(Context c) { super(c); setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_NO); }
        protected void onDraw(Canvas c) {
            super.onDraw(c); c.save(); float scale=Math.min(getWidth()/260f,getHeight()/190f); c.translate((getWidth()-260*scale)/2,(getHeight()-190*scale)/2);c.scale(scale,scale);
            p.setColor(TINT);c.drawOval(25,5,235,180,p);p.setColor(0xFFE2D8E9);c.drawOval(26,156,236,169,p);
            book(c,56,49,38,110,0xFFB9A1C5,0);book(c,100,30,42,129,GREEN,0);book(c,153,61,36,99,0xFFCEAA86,-12);
            p.setColor(GREEN);c.drawCircle(218,43,3,p);c.drawLine(32,39,42,39,p);c.drawLine(37,34,37,44,p);c.restore();
        }
        private void book(Canvas c,int x,int y,int w,int h,int color,int angle) {
            c.save();c.rotate(angle,x+w/2f,y+h);p.setColor(color);c.drawRoundRect(x,y,x+w,y+h,5,5,p);
            p.setColor(0x66FFFFFF);c.drawRect(x+6,y,x+8,y+h,p);c.drawRoundRect(x+13,y+15,x+w-7,y+18,1,1,p);c.drawRoundRect(x+13,y+23,x+w-7,y+25,1,1,p);
            p.setColor(0xFFEFE9DF);c.drawRoundRect(x+5,y+h-9,x+w-4,y+h-3,2,2,p);c.restore();
        }
    }
}
