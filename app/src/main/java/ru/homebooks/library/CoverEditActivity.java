package ru.homebooks.library;

import android.content.Intent;
import android.graphics.*;
import android.os.Bundle;
import android.view.*;
import android.widget.*;
import androidx.activity.ComponentActivity;
import java.io.*;

/** Local-only four-corner crop; applying prepares a draft, never mutates the library. */
public final class CoverEditActivity extends ComponentActivity {
    private Bitmap source,preview;
    private CropView crop;
    private String inputPath;
    private int rotation;
    private boolean straight,busy;
    private float[] corners=CoverCrop.full();
    private LinearLayout body;
    private Button rotate,align,done;
    @Override public void onCreate(Bundle state){
        super.onCreate(state);inputPath=getIntent().getStringExtra("imagePath");
        if(state!=null){rotation=state.getInt("rotation");straight=state.getBoolean("straight");float[] restored=state.getFloatArray("corners");if(CoverCrop.valid(restored))corners=restored;}
        LinearLayout root=Ui.column(this);root.setBackgroundColor(Ui.PAPER);setContentView(root);Ui.insets(root);root.addView(Ui.toolbar(this,"Обработать фото",this::finish));
        body=Ui.column(this);Ui.pad(body,16);root.addView(body,new LinearLayout.LayoutParams(-1,0,1));
        body.addView(Ui.muted(this,"Переместите четыре точки к углам обложки.",14));Ui.gap(body,16);
        crop=new CropView();body.addView(crop,new LinearLayout.LayoutParams(-1,0,1));Ui.gap(body,16);
        LinearLayout tools=new LinearLayout(this);rotate=Ui.quietAction(this,"Повернуть","refresh",this::rotate);align=Ui.quietAction(this,"Выровнять","document-scan",this::togglePreview);
        LinearLayout.LayoutParams first=new LinearLayout.LayoutParams(0,-2,1);first.setMarginEnd(Ui.dp(this,10));tools.addView(rotate,first);tools.addView(align,new LinearLayout.LayoutParams(0,-2,1));body.addView(tools);Ui.gap(body,16);
        done=Ui.primary(this,"Готово",this::apply);body.addView(done);body.addView(Ui.textButton(this,"Отмена",this::finish));setBusy(true);
        ((LibraryApp)getApplication()).io.execute(()->{
            Bitmap loaded=null;
            try{File file=new File(inputPath);if(!file.getCanonicalPath().startsWith(getCacheDir().getCanonicalPath()+File.separator))throw new IOException();loaded=BitmapFactory.decodeFile(inputPath);if(loaded==null)throw new IOException();
                if(rotation!=0){Matrix m=new Matrix();m.setRotate(rotation);Bitmap b=Bitmap.createBitmap(loaded,0,0,loaded.getWidth(),loaded.getHeight(),m,true);if(b!=loaded)loaded.recycle();loaded=b;}
                Bitmap image=loaded;runOnUiThread(()->{if(isDestroyed()||isFinishing()){image.recycle();return;}source=image;if(straight){preview=transformed(source,corners);align.setText("Границы");}setBusy(false);crop.invalidate();});
            }catch(Exception e){if(loaded!=null)loaded.recycle();runOnUiThread(()->{Toast.makeText(this,"Не удалось открыть фотографию",Toast.LENGTH_LONG).show();finish();});}
        });
    }
    @Override protected void onSaveInstanceState(Bundle state){state.putInt("rotation",rotation);state.putBoolean("straight",straight);state.putFloatArray("corners",corners);super.onSaveInstanceState(state);}
    private void setBusy(boolean value){busy=value;rotate.setEnabled(!value);align.setEnabled(!value);done.setEnabled(!value);}
    private void rotate(){if(source==null||busy)return;clearPreview();Matrix m=new Matrix();m.setRotate(90);Bitmap b=Bitmap.createBitmap(source,0,0,source.getWidth(),source.getHeight(),m,true);if(b!=source)source.recycle();source=b;rotation=(rotation+90)%360;corners=CoverCrop.full();crop.invalidate();}
    private void clearPreview(){straight=false;if(preview!=null){preview.recycle();preview=null;}align.setText("Выровнять");}
    private void togglePreview(){if(source==null||busy)return;if(straight)clearPreview();else{preview=transformed(source,corners);straight=preview!=null;align.setText(straight?"Границы":"Выровнять");}crop.invalidate();}
    static Bitmap transformed(Bitmap bitmap,float[] p){
        if(!CoverCrop.valid(p))throw new IllegalArgumentException("Invalid crop");
        float[] from=new float[8];for(int i=0;i<8;i++)from[i]=p[i]*(i%2==0?bitmap.getWidth():bitmap.getHeight());
        int width=Math.max(1,Math.round(Math.max(distance(from,0,2),distance(from,6,4))));
        int height=Math.max(1,Math.round(Math.max(distance(from,0,6),distance(from,2,4))));
        float scale=Math.min(1f,1024f/Math.max(width,height));width=Math.max(1,Math.round(width*scale));height=Math.max(1,Math.round(height*scale));
        Matrix transform=new Matrix();if(!transform.setPolyToPoly(from,0,new float[]{0,0,width,0,width,height,0,height},0,4))throw new IllegalArgumentException("Invalid perspective");
        Bitmap result=Bitmap.createBitmap(width,height,Bitmap.Config.ARGB_8888);Canvas c=new Canvas(result);c.drawColor(Color.WHITE);c.drawBitmap(bitmap,transform,new Paint(Paint.ANTI_ALIAS_FLAG|Paint.FILTER_BITMAP_FLAG));return result;
    }
    private static float distance(float[] p,int a,int b){return (float)Math.hypot(p[a]-p[b],p[a+1]-p[b+1]);}
    private void apply(){
        if(source==null||busy)return;Bitmap output=transformed(source,corners);setBusy(true);
        ((LibraryApp)getApplication()).io.execute(()->{File file=null;
            try{file=File.createTempFile("book-cover-edited-",".jpg",getCacheDir());try(OutputStream out=new FileOutputStream(file)){if(!output.compress(Bitmap.CompressFormat.JPEG,90,out))throw new IOException();}
                File ready=file;runOnUiThread(()->{if(isDestroyed()||isFinishing()){ready.delete();return;}setResult(RESULT_OK,new Intent().putExtra("coverPath",ready.getAbsolutePath()).putExtra("bookId",getIntent().getStringExtra("bookId")));finish();});
            }catch(Exception e){if(file!=null)file.delete();runOnUiThread(()->{if(!isDestroyed()){setBusy(false);Toast.makeText(this,"Не удалось сохранить снимок",Toast.LENGTH_LONG).show();}});}finally{output.recycle();}
        });
    }
    @Override protected void onDestroy(){if(source!=null)source.recycle();if(preview!=null)preview.recycle();if(isFinishing()&&inputPath!=null)try{File f=new File(inputPath);if(f.getCanonicalPath().startsWith(getCacheDir().getCanonicalPath()+File.separator))f.delete();}catch(IOException ignored){}super.onDestroy();}
    private final class CropView extends View {
        private final Paint paint=new Paint(Paint.ANTI_ALIAS_FLAG|Paint.FILTER_BITMAP_FLAG);
        private final RectF bounds=new RectF();private int active=-1;
        CropView(){super(CoverEditActivity.this);setContentDescription("Границы обложки. Перетащите каждый из четырёх углов.");setBackgroundColor(Ui.TINT);}
        @Override protected void onDraw(Canvas canvas){
            Bitmap image=straight?preview:source;if(image==null)return;
            float pad=Ui.dp(getContext(),22),scale=Math.min(Math.max(1,getWidth()-2*pad)/image.getWidth(),Math.max(1,getHeight()-2*pad)/image.getHeight());
            float w=image.getWidth()*scale,h=image.getHeight()*scale;bounds.set((getWidth()-w)/2,(getHeight()-h)/2,(getWidth()+w)/2,(getHeight()+h)/2);
            paint.setStyle(Paint.Style.FILL);paint.setColor(Color.WHITE);canvas.drawBitmap(image,null,bounds,paint);if(straight)return;
            Path path=new Path();for(int i=0;i<4;i++){float x=bounds.left+corners[2*i]*w,y=bounds.top+corners[2*i+1]*h;if(i==0)path.moveTo(x,y);else path.lineTo(x,y);}path.close();
            paint.setColor(Ui.GREEN);paint.setStrokeWidth(Ui.dp(getContext(),2));paint.setStyle(Paint.Style.STROKE);canvas.drawPath(path,paint);
            for(int i=0;i<4;i++){float x=bounds.left+corners[2*i]*w,y=bounds.top+corners[2*i+1]*h;paint.setStyle(Paint.Style.FILL);paint.setColor(Color.WHITE);canvas.drawCircle(x,y,Ui.dp(getContext(),7),paint);paint.setStyle(Paint.Style.STROKE);paint.setColor(Ui.GREEN);canvas.drawCircle(x,y,Ui.dp(getContext(),7),paint);}
        }
        @Override public boolean onTouchEvent(MotionEvent e){
            if(source==null||straight||busy||bounds.isEmpty())return false;
            if(e.getActionMasked()==MotionEvent.ACTION_DOWN){float best=Ui.dp(getContext(),36);active=-1;for(int i=0;i<4;i++){float d=(float)Math.hypot(e.getX()-bounds.left-corners[2*i]*bounds.width(),e.getY()-bounds.top-corners[2*i+1]*bounds.height());if(d<best){best=d;active=i;}}if(active<0)return false;getParent().requestDisallowInterceptTouchEvent(true);return true;}
            if(e.getActionMasked()==MotionEvent.ACTION_MOVE&&active>=0){float[] p=corners.clone();p[2*active]=Math.max(0,Math.min(1,(e.getX()-bounds.left)/bounds.width()));p[2*active+1]=Math.max(0,Math.min(1,(e.getY()-bounds.top)/bounds.height()));if(CoverCrop.valid(p))corners=p;invalidate();return true;}
            if(e.getActionMasked()==MotionEvent.ACTION_UP||e.getActionMasked()==MotionEvent.ACTION_CANCEL){active=-1;getParent().requestDisallowInterceptTouchEvent(false);performClick();return true;}return true;
        }
        @Override public boolean performClick(){super.performClick();return true;}
    }
}
