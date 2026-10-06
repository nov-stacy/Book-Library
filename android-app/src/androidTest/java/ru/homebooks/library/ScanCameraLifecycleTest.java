package ru.homebooks.library;

import android.app.*;
import android.content.Intent;
import android.os.*;
import androidx.camera.core.*;
import androidx.camera.lifecycle.ProcessCameraProvider;
import androidx.test.platform.app.InstrumentationRegistry;
import org.junit.Test;
import java.lang.reflect.Field;
import java.util.*;
import static org.junit.Assert.*;

/** A late destroy of an old scanner must not disconnect its replacement. QA only. */
public class ScanCameraLifecycleTest {
    @Test public void consecutiveScannerKeepsOwnCameraAfterPreviousScreenIsDestroyed()throws Exception{
        Instrumentation instrumentation=InstrumentationRegistry.getInstrumentation();
        android.content.Context context=instrumentation.getTargetContext();
        assertEquals("ru.homebooks.library.testbed",context.getPackageName());
        try(ParcelFileDescriptor command=instrumentation.getUiAutomation().executeShellCommand("pm grant ru.homebooks.library.testbed android.permission.CAMERA")){
            try(java.io.InputStream in=new ParcelFileDescriptor.AutoCloseInputStream(command)){while(in.read()!=-1){}}
        }
        List<ScanActivity> opened=new ArrayList<>();
        try{
            ScanActivity previous=null;
            for(int i=0;i<3;i++){
                Intent intent=new Intent(context,ScanActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK|Intent.FLAG_ACTIVITY_MULTIPLE_TASK)
                    .putExtra("skipIsbn","9780306406157").putExtra("savedTitle","Проверка продолжения сканирования")
                    .putExtra("alreadySaved",i!=2);
                Instrumentation.ActivityMonitor monitor=instrumentation.addMonitor(ScanActivity.class.getName(),null,false);
                context.startActivity(intent);
                ScanActivity next=(ScanActivity)instrumentation.waitForMonitorWithTimeout(monitor,5000);instrumentation.removeMonitor(monitor);
                assertNotNull("Scanner activity must open",next);opened.add(next);
                waitForCamera(instrumentation,next);
                if(previous!=null){
                    ScanActivity old=previous;instrumentation.runOnMainSync(old::finish);
                    long until=SystemClock.uptimeMillis()+5000;
                    while(!old.isDestroyed()&&SystemClock.uptimeMillis()<until)SystemClock.sleep(30);
                    assertTrue("Previous scanner should be destroyed",old.isDestroyed());
                    instrumentation.runOnMainSync(()->assertBound(next));
                }
                previous=next;
            }
        }finally{instrumentation.runOnMainSync(()->{for(ScanActivity activity:opened)if(!activity.isDestroyed())activity.finish();});}
    }
    private static void waitForCamera(Instrumentation instrumentation,ScanActivity activity)throws Exception{
        boolean[] ready={false};long until=SystemClock.uptimeMillis()+10000;
        while(SystemClock.uptimeMillis()<until){
            instrumentation.runOnMainSync(()->ready[0]=field(activity,"camera")!=null);
            if(ready[0]){instrumentation.runOnMainSync(()->assertBound(activity));return;}
            SystemClock.sleep(50);
        }
        fail("Scanner did not bind camera");
    }
    private static void assertBound(ScanActivity activity){
        ProcessCameraProvider provider=(ProcessCameraProvider)field(activity,"provider");
        Preview preview=(Preview)field(activity,"boundPreview");ImageAnalysis frames=(ImageAnalysis)field(activity,"boundFrames");
        assertNotNull(provider);assertNotNull(preview);assertNotNull(frames);
        assertTrue("Preview must remain connected",provider.isBound(preview));
        assertTrue("Barcode analysis must remain connected",provider.isBound(frames));
    }
    private static Object field(Object instance,String name){
        try{Field field=instance.getClass().getDeclaredField(name);field.setAccessible(true);return field.get(instance);}catch(Exception e){throw new AssertionError(e);}
    }
}
