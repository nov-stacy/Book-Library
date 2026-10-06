package ru.homebooks.library;

import android.app.*;
import android.content.*;
import android.graphics.*;
import android.os.*;
import android.view.*;
import android.view.inspector.WindowInspector;
import android.widget.*;
import androidx.test.platform.app.InstrumentationRegistry;
import org.junit.Test;
import java.io.*;
import java.util.*;
import static org.junit.Assert.*;

/** Visual audit in the separate testbed application; never touches the real library. */
public class StyleAuditTest {
    private final Instrumentation instrumentation=InstrumentationRegistry.getInstrumentation();
    private final List<Activity> opened=new ArrayList<>();
    private LibraryApp app;
    private File output;
    private android.content.res.Configuration originalConfiguration;
    private CoverLoader.State originalCoverState;
    private final List<String> issues=new ArrayList<>();
    private Activity open(Class<? extends Activity> type,String id) throws Exception {
        Intent intent=new Intent(app,type).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK|Intent.FLAG_ACTIVITY_CLEAR_TASK);
        if(id!=null)intent.putExtra("isbn",id);
        Activity activity=instrumentation.startActivitySync(intent);opened.add(activity);settle();return activity;
    }
    private void settle()throws Exception{app.io.submit(()->{}).get();instrumentation.runOnMainSync(()->{});SystemClock.sleep(250);instrumentation.runOnMainSync(()->{});}
    private View text(View root,String value){
        if(root instanceof TextView&&value.contentEquals(((TextView)root).getText()))return root;
        if(root instanceof ViewGroup)for(int i=0;i<((ViewGroup)root).getChildCount();i++){View found=text(((ViewGroup)root).getChildAt(i),value);if(found!=null)return found;}
        return null;
    }
    private void click(Activity activity,String value)throws Exception{
        instrumentation.runOnMainSync(()->{View view=text(activity.getWindow().getDecorView(),value);assertNotNull(value,view);while(!view.isClickable()&&view.getParent() instanceof View)view=(View)view.getParent();assertTrue(view.performClick());});settle();
    }
    private void capture(View root,String name)throws Exception{capture(root,name,false);}
    private void capture(View root,String name,boolean small)throws Exception{
        Bitmap[] frame={null};
        instrumentation.runOnMainSync(()->{
            if(small)narrow(root);assertTrue(root.getWidth()>0);
            float scale=Math.min(1f,720f/root.getWidth());frame[0]=Bitmap.createBitmap(Math.round(root.getWidth()*scale),Math.round(root.getHeight()*scale),Bitmap.Config.ARGB_8888);
            Canvas canvas=new Canvas(frame[0]);canvas.scale(scale,scale);root.draw(canvas);audit(root,name);
        });
        try(FileOutputStream out=new FileOutputStream(new File(output,name+".jpg"))){assertTrue(frame[0].compress(Bitmap.CompressFormat.JPEG,92,out));}finally{frame[0].recycle();}
    }
    private void audit(View view,String name){
        if(view.getVisibility()!=View.VISIBLE)return;
        if(view instanceof Button||view instanceof ImageButton){
            if(view.getHeight()>0&&view.getHeight()<Ui.dp(app,48)-1)issues.add(name+": touch height "+view.getHeight()+" "+(view instanceof TextView?((TextView)view).getText():view.getContentDescription()));
            if(view instanceof Button){TextView label=(TextView)view;if(label.getLayout()!=null&&label.getHeight()>0&&label.getLayout().getHeight()>label.getHeight()-label.getCompoundPaddingTop()-label.getCompoundPaddingBottom()+2)issues.add(name+": clipped button "+label.getText());}
        }
        if(view instanceof ViewGroup)for(int i=0;i<((ViewGroup)view).getChildCount();i++)audit(((ViewGroup)view).getChildAt(i),name);
    }
    private void scrollBottom(View root){
        if(root instanceof ScrollView){((ScrollView)root).fullScroll(View.FOCUS_DOWN);return;}
        if(root instanceof ViewGroup)for(int i=0;i<((ViewGroup)root).getChildCount();i++)scrollBottom(((ViewGroup)root).getChildAt(i));
    }
    private void enlarge(View root){
        if(root instanceof TextView){TextView label=(TextView)root;label.setTextSize(android.util.TypedValue.COMPLEX_UNIT_PX,label.getTextSize()*1.3f);}
        if(root instanceof ViewGroup)for(int i=0;i<((ViewGroup)root).getChildCount();i++)enlarge(((ViewGroup)root).getChildAt(i));
    }
    private void narrow(View root){
        int width=Ui.dp(app,320),height=Ui.dp(app,700);root.measure(View.MeasureSpec.makeMeasureSpec(width,View.MeasureSpec.EXACTLY),View.MeasureSpec.makeMeasureSpec(height,View.MeasureSpec.EXACTLY));root.layout(0,0,width,height);root.measure(View.MeasureSpec.makeMeasureSpec(width,View.MeasureSpec.EXACTLY),View.MeasureSpec.makeMeasureSpec(height,View.MeasureSpec.EXACTLY));root.layout(0,0,width,height);
    }
    @android.annotation.SuppressLint("NewApi")
    private View dialog() {List<View> windows=WindowInspector.getGlobalWindowViews();return windows.get(windows.size()-1);}
    @Test public void captureScreensAndCheckControls()throws Exception{
        org.junit.Assume.assumeTrue(Build.VERSION.SDK_INT>=29);
        app=(LibraryApp)instrumentation.getTargetContext().getApplicationContext();assertEquals("ru.homebooks.library.testbed",app.getPackageName());
        originalConfiguration=new android.content.res.Configuration(app.getResources().getConfiguration());originalCoverState=app.covers.state.getValue();
        output=new File(app.getCacheDir(),"style-audit");output.mkdirs();
        List<String> ids=new ArrayList<>();long[] group={-1};
        try{
            app.io.submit(()->{
                List<String> stale=new ArrayList<>();for(Book book:app.store.all())if(book.title.startsWith("Тихие истории и другие удивительные приключения ")&&book.author.equals("Анастасия Александрова"))stale.add(book.id);app.store.delete(stale);
                for(BookCollection collection:app.store.collections())if(collection.name.equals("Любимые книги и истории для долгих вечеров"))app.store.deleteCollection(collection.id);
                for(int i=0;i<8;i++){String id=Book.newLocalId();ids.add(id);app.store.save(new Book(id,"Тихие истории и другие удивительные приключения "+(i+1),"Анастасия Александрова",i,ReadingStatus.values()[i%4],BookLocation.PARENTS,"2025-04-12"));}group[0]=app.store.createCollection("Любимые книги и истории для долгих вечеров");app.store.setCollectionBooks(group[0],ids);}).get();
            Activity main=open(MainActivity.class,null);capture(main.getWindow().getDecorView(),"01-library");
            instrumentation.runOnMainSync(()->Navigation.show(main,false));settle();View drawer=dialog();capture(drawer,"02-drawer");
            instrumentation.runOnMainSync(()->enlarge(drawer));settle();capture(drawer,"03-drawer-large-text");
            Activity collections=open(CollectionsActivity.class,null);capture(collections.getWindow().getDecorView(),"04-collections");click(collections,"Изменить");capture(dialog(),"05-edit-collection");
            Activity read=open(ReadActivity.class,null);capture(read.getWindow().getDecorView(),"06-read");
            Activity book=open(AddBookActivity.class,ids.get(3));capture(book.getWindow().getDecorView(),"07-book");instrumentation.runOnMainSync(()->CollectionUi.forBook(book,ids.get(3),()->{}));settle();capture(dialog(),"21-collection-choice");instrumentation.runOnMainSync(()->dialog().findViewById(android.R.id.button2).performClick());settle();
            if("true".equals(InstrumentationRegistry.getArguments().getString("collectionsOnly"))){
                instrumentation.runOnMainSync(()->{android.content.res.Configuration config=new android.content.res.Configuration(book.getResources().getConfiguration());config.fontScale=1.3f;book.getResources().updateConfiguration(config,book.getResources().getDisplayMetrics());});
                Activity scaled=open(MainActivity.class,null);capture(scaled.findViewById(android.R.id.content),"22-collections-large-text",true);
                assertTrue(issues.toString(),issues.isEmpty());return;
            }
            click(book,"Редактировать");capture(book.getWindow().getDecorView(),"08-editor");
            instrumentation.runOnMainSync(()->scrollBottom(book.getWindow().getDecorView()));settle();capture(book.getWindow().getDecorView(),"09-editor-bottom");
            click(book,"Местонахождение");capture(dialog(),"16-location");instrumentation.runOnMainSync(()->dialog().findViewById(android.R.id.button2).performClick());settle();
            click(book,"Дата прочтения");capture(dialog(),"17-date");instrumentation.runOnMainSync(()->dialog().findViewById(android.R.id.button2).performClick());settle();
            instrumentation.runOnMainSync(()->CollectionUi.members(book,group[0],()->{}));settle();capture(dialog(),"10-book-picker");
            Activity add=open(AddBookActivity.class,null);capture(add.getWindow().getDecorView(),"11-add");
            Activity settings=open(SettingsActivity.class,null);capture(settings.getWindow().getDecorView(),"12-settings");
            instrumentation.runOnMainSync(()->app.covers.state.setValue(new CoverLoader.State(true,37,100,23,14)));settle();
            instrumentation.runOnMainSync(()->scrollBottom(settings.getWindow().getDecorView()));settle();capture(settings.getWindow().getDecorView(),"13-settings-progress");
            instrumentation.runOnMainSync(()->enlarge(settings.getWindow().getDecorView()));settle();capture(settings.getWindow().getDecorView(),"14-settings-large-text");
            Activity small=open(MainActivity.class,null);
            // Rebuild with larger text at resource level so recycled list rows use the same scale.
            instrumentation.runOnMainSync(()->{
                android.content.res.Configuration config=new android.content.res.Configuration(small.getResources().getConfiguration());config.fontScale=1.3f;
                small.getResources().updateConfiguration(config,small.getResources().getDisplayMetrics());
            });
            Activity scaled=open(MainActivity.class,null);capture(scaled.findViewById(android.R.id.content),"15-library-narrow-large-text",true);
            click(scaled,"Все книги");capture(dialog(),"18-filter");instrumentation.runOnMainSync(()->text(dialog(),"Хочу прочитать").performClick());settle();
            Activity filtered=open(MainActivity.class,null);instrumentation.runOnMainSync(()->{try{
                java.lang.reflect.Field field=MainActivity.class.getDeclaredField("statusFilter");field.setAccessible(true);field.set(filtered,ReadingStatus.WANT);
                java.lang.reflect.Method update=MainActivity.class.getDeclaredMethod("updateFilters");update.setAccessible(true);update.invoke(filtered);
                java.lang.reflect.Method refresh=MainActivity.class.getDeclaredMethod("refresh");refresh.setAccessible(true);refresh.invoke(filtered);
            }catch(Exception e){throw new AssertionError(e);}});settle();capture(filtered.findViewById(android.R.id.content),"19-long-filter",true);
            Activity scan=open(ScanActivity.class,null);capture(scan.getWindow().getDecorView(),"20-camera-layout");
            try(FileWriter out=new FileWriter(new File(output,"audit.txt"))){for(String issue:issues)out.write(issue+"\n");}
            assertTrue(issues.toString(),issues.isEmpty());
        }finally{
            instrumentation.runOnMainSync(()->{for(Activity activity:opened)if(!activity.isDestroyed())activity.finish();
                if(originalConfiguration!=null)app.getResources().updateConfiguration(originalConfiguration,app.getResources().getDisplayMetrics());
                app.covers.state.setValue(originalCoverState);
            });
            app.io.submit(()->{if(group[0]>=0)app.store.deleteCollection(group[0]);app.store.delete(ids);}).get();
        }
    }
}
