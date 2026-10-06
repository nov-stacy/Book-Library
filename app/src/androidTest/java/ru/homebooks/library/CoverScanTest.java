package ru.homebooks.library;
import android.graphics.*;
import org.junit.Test;
import static org.junit.Assert.*;
public class CoverScanTest {
    @Test public void cropRemovesOutsideAndPerspectiveReturnsRectangle(){
        Bitmap original=Bitmap.createBitmap(200,300,Bitmap.Config.ARGB_8888);Canvas canvas=new Canvas(original);canvas.drawColor(Color.RED);Paint paint=new Paint();paint.setColor(Color.BLUE);canvas.drawRect(40,60,160,240,paint);
        Bitmap cropped=CoverEditActivity.transformed(original,new float[]{.2f,.2f,.8f,.2f,.8f,.8f,.2f,.8f});assertEquals(120,cropped.getWidth());assertEquals(180,cropped.getHeight());assertEquals(Color.BLUE,cropped.getPixel(60,90));
        Bitmap perspective=CoverEditActivity.transformed(original,new float[]{.2f,.2f,.8f,.25f,.75f,.8f,.25f,.75f});assertTrue(perspective.getWidth()>0);assertTrue(perspective.getHeight()>0);assertEquals(Color.BLUE,perspective.getPixel(perspective.getWidth()/2,perspective.getHeight()/2));
        cropped.recycle();perspective.recycle();original.recycle();
    }
}
