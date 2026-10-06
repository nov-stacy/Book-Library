package ru.homebooks.library;

import android.app.*;
import android.content.Intent;
import android.graphics.*;
import android.net.Uri;
import android.os.Bundle;
import android.view.*;
import android.widget.*;
import androidx.test.platform.app.InstrumentationRegistry;
import org.junit.Test;
import java.io.*;
import java.lang.reflect.*;
import java.util.*;
import static org.junit.Assert.*;

/** Single-card save boundary, including imported books and the real photo editor activity. */
public class BookConfirmationTest {
    private final Instrumentation instrumentation=InstrumentationRegistry.getInstrumentation();
    private LibraryApp app;
    private final List<String> ids=new ArrayList<>();
    private final List<Activity> activities=new ArrayList<>();
    private void init(){app=(LibraryApp)instrumentation.getTargetContext().getApplicationContext();assertEquals("ru.homebooks.library.testbed",app.getPackageName());}
    @Test public void isbnResultIsEditableAndSavesOnlyOnce()throws Exception{
        init();try{
            AddBookActivity a=found();String id=(String)get(a,"isbn");assertNull(app.store.find(id));
            assertNull(text(a.getWindow().getDecorView(),"Это ваша книга?"));assertNotNull(text(a.getWindow().getDecorView(),"Найти в интернете"));
            instrumentation.runOnMainSync(()->{((EditText)get(a,"title")).setText("Исправленное название");((EditText)get(a,"author")).setText("Мой автор");});
            instrumentation.runOnMainSync(()->invoke(a,"saveScannedCover",new Class[]{String.class,byte[].class},id,jpeg(Color.BLUE)));settle();
            assertNull(app.store.find(id));assertFalse(app.store.cover(id).exists());assertEquals("Исправленное название",get(a,"draftTitle"));assertNotNull(get(a,"draftCoverPath"));
            Bundle state=new Bundle();instrumentation.runOnMainSync(()->invoke(a,"onSaveInstanceState",new Class[]{Bundle.class},state));assertFalse(state.getBoolean("confirming"));assertNotNull(state.getString("draftCoverPath"));
            click(a,"Сохранить книгу");assertEquals("Исправленное название",app.store.find(id).title);assertEquals("Мой автор",app.store.find(id).author);assertTrue(app.store.cover(id).exists());
        }finally{cleanup();}
    }
    @Test public void importedBookCoverStaysDraftUntilSaveAndKeepsCollection()throws Exception{
        init();long group=app.store.createCollection("Draft cover "+System.nanoTime());
        try{
            AddBookActivity a=found();String id=(String)get(a,"isbn");String isbn="9785171605292";
            app.store.saveWithCollections(new Book(id,isbn,"Моя книга","Автор",42,ReadingStatus.READ,BookLocation.PARENTS,"2025-03-12"),Collections.singletonList(group));
            instrumentation.runOnMainSync(()->invoke(a,"openIsbn",new Class[]{String.class},isbn));settle();
            assertEquals(id,get(a,"isbn"));assertTrue((Boolean)get(a,"duplicateCoverOffer"));
            instrumentation.runOnMainSync(()->invoke(a,"saveScannedCover",new Class[]{String.class,byte[].class},id,jpeg(Color.BLUE)));settle();assertFalse(app.store.cover(id).exists());
            click(a,"Сохранить книгу");Book saved=app.store.find(id);assertEquals("Моя книга",saved.title);assertEquals(ReadingStatus.READ,saved.status);assertEquals(BookLocation.PARENTS,saved.location);assertEquals("2025-03-12",saved.readOn);assertTrue(app.store.cover(id).exists());
            for(BookCollection c:app.store.collections())if(c.id==group)assertTrue(c.isbns.contains(id));
            AddBookActivity host=found();final BookNotice[] notice=new BookNotice[1];instrumentation.runOnMainSync(()->{notice[0]=new BookNotice(host,isbn,"Моя книга",true,true);host.setContentView(notice[0]);});settle();
            instrumentation.runOnMainSync(()->assertTrue(((ImageView)notice[0].getChildAt(0)).getDrawable() instanceof android.graphics.drawable.BitmapDrawable));
        }finally{cleanup();app.store.deleteCollection(group);}
    }
    @Test public void photoEditorReturnsToSameFieldsWithoutConfirmation()throws Exception{
        init();File photo=File.createTempFile("photo-fixture-",".jpg",app.getCacheDir());try(FileOutputStream out=new FileOutputStream(photo)){out.write(jpeg(Color.BLUE));}
        try{
            AddBookActivity a=found();String id=(String)get(a,"isbn");instrumentation.runOnMainSync(()->((EditText)get(a,"title")).setText("Название перед фото"));
            CoverEditActivity edit=openPhoto(a,photo);click(edit,"Отмена");settle();assertNull(get(a,"draftCoverPath"));assertEquals("Название перед фото",((EditText)get(a,"title")).getText().toString());
            edit=openPhoto(a,photo);snapshot("cover-format");click(edit,"Повернуть");click(edit,"Выровнять");click(edit,"Готово");settle();
            snapshot("scan-card");assertNotNull(get(a,"draftCoverPath"));assertEquals("Название перед фото",((EditText)get(a,"title")).getText().toString());assertNull(app.store.find(id));
            assertNull(text(a.getWindow().getDecorView(),"Это ваша книга?"));assertNotNull(text(a.getWindow().getDecorView(),"Изменить обложку"));
            click(a,"Сохранить книгу");assertNotNull(app.store.find(id));assertNotNull(BitmapFactory.decodeFile(app.store.cover(id).getAbsolutePath()));
        }finally{photo.delete();cleanup();}
    }
    @Test public void abandoningExistingCoverEditKeepsOriginal()throws Exception{
        init();try{
            AddBookActivity a=found();String id=(String)get(a,"isbn");byte[] original=jpeg(Color.RED);
            app.store.save(new Book(id,"Моя книга","Автор",42));app.store.saveCover(id,original);
            instrumentation.runOnMainSync(()->{set(a,"lookupEntry",false);invoke(a,"openIsbn",new Class[]{String.class},id);});settle();click(a,"Редактировать");
            instrumentation.runOnMainSync(()->invoke(a,"saveScannedCover",new Class[]{String.class,byte[].class},id,jpeg(Color.BLUE)));settle();
            assertArrayEquals(original,read(app.store.cover(id)));String draft=(String)get(a,"draftCoverPath");click(a,"Отмена");settle();assertFalse(new File(draft).exists());assertArrayEquals(original,read(app.store.cover(id)));
        }finally{cleanup();}
    }
    @Test public void failedSearchOnlyReturnsToSameCard()throws Exception{
        init();Field field=BookLookup.class.getDeclaredField("productionTransport");field.setAccessible(true);Object original=field.get(null);
        try{
            field.set(null,(BookLookup.Transport)(url,limit)->{throw new IOException("offline fixture");});AddBookActivity a=found();String id=(String)get(a,"isbn");click(a,"Найти в интернете");
            long end=System.currentTimeMillis()+15000;boolean clicked=false;
            while(System.currentTimeMillis()<end&&!clicked){android.view.accessibility.AccessibilityNodeInfo root=instrumentation.getUiAutomation().getRootInActiveWindow();if(root!=null)for(android.view.accessibility.AccessibilityNodeInfo n:root.findAccessibilityNodeInfosByText("Вернуться"))if("Вернуться".contentEquals(n.getText())){assertTrue(n.performAction(android.view.accessibility.AccessibilityNodeInfo.ACTION_CLICK));clicked=true;}if(!clicked)Thread.sleep(100);}
            assertTrue(clicked);settle();assertNull(app.store.find(id));assertNull(get(a,"draftCoverPath"));assertEquals("Найденная книга",((EditText)get(a,"title")).getText().toString());
        }finally{field.set(null,original);cleanup();}
    }
    private void snapshot(String name)throws Exception{
        if(!"true".equals(InstrumentationRegistry.getArguments().getString("snapshots")))return;
        Thread.sleep(650);instrumentation.waitForIdleSync();Bitmap bitmap=instrumentation.getUiAutomation().takeScreenshot();assertNotNull(bitmap);try(FileOutputStream out=new FileOutputStream(new File(app.getCacheDir(),name+".png"))){bitmap.compress(Bitmap.CompressFormat.PNG,100,out);}bitmap.recycle();
    }
    private CoverEditActivity openPhoto(AddBookActivity a,File photo)throws Exception{
        Instrumentation.ActivityMonitor monitor=instrumentation.addMonitor(CoverEditActivity.class.getName(),null,false);
        instrumentation.runOnMainSync(()->invoke(a,"previewCover",new Class[]{Uri.class,boolean.class},Uri.fromFile(photo),true));
        CoverEditActivity edit=(CoverEditActivity)instrumentation.waitForMonitorWithTimeout(monitor,5000);instrumentation.removeMonitor(monitor);assertNotNull(edit);activities.add(edit);settle();return edit;
    }
    private AddBookActivity found()throws Exception{
        AddBookActivity a=(AddBookActivity)instrumentation.startActivitySync(new Intent(app,AddBookActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK|Intent.FLAG_ACTIVITY_MULTIPLE_TASK));activities.add(a);
        String id=Book.newLocalId();ids.add(id);instrumentation.runOnMainSync(()->{a.getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);set(a,"isbn",id);set(a,"knownIsbn","");set(a,"lookupEntry",true);set(a,"addedAt",42L);a.showLookupResult(new BookLookup.Result("Найденная книга","Автор",null,""));});settle();return a;
    }
    private void cleanup()throws Exception{instrumentation.runOnMainSync(()->{for(Activity a:activities)if(!a.isDestroyed())a.finish();});settle();app.store.delete(ids);}
    private void settle()throws Exception{for(int i=0;i<3;i++){app.io.submit(()->{}).get();instrumentation.waitForIdleSync();}}
    private void click(Activity a,String label)throws Exception{instrumentation.runOnMainSync(()->{View b=text(a.getWindow().getDecorView(),label);assertNotNull(label,b);assertTrue(b.performClick());});settle();}
    private View text(View root,String label){if(root instanceof TextView&&label.contentEquals(((TextView)root).getText()))return root;if(root instanceof ViewGroup){ViewGroup g=(ViewGroup)root;for(int i=0;i<g.getChildCount();i++){View v=text(g.getChildAt(i),label);if(v!=null)return v;}}return null;}
    private static byte[] jpeg(int color){Bitmap b=Bitmap.createBitmap(100,160,Bitmap.Config.ARGB_8888);b.eraseColor(color);ByteArrayOutputStream out=new ByteArrayOutputStream();b.compress(Bitmap.CompressFormat.JPEG,90,out);b.recycle();return out.toByteArray();}
    private static byte[] read(File file)throws Exception{try(InputStream in=new FileInputStream(file);ByteArrayOutputStream out=new ByteArrayOutputStream()){byte[] b=new byte[4096];int n;while((n=in.read(b))!=-1)out.write(b,0,n);return out.toByteArray();}}
    private static Object get(Object o,String name){try{Field f=o.getClass().getDeclaredField(name);f.setAccessible(true);return f.get(o);}catch(Exception e){throw new AssertionError(e);}}
    private static void set(Object o,String name,Object value){try{Field f=o.getClass().getDeclaredField(name);f.setAccessible(true);f.set(o,value);}catch(Exception e){throw new AssertionError(e);}}
    private static Object invoke(Object o,String name,Class[] types,Object... args){try{Method m=o.getClass().getDeclaredMethod(name,types);m.setAccessible(true);return m.invoke(o,args);}catch(Exception e){throw new AssertionError(e);}}
}
