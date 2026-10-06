package ru.homebooks.library;

import android.Manifest;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.view.*;
import android.widget.*;
import androidx.activity.ComponentActivity;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.camera.core.*;
import androidx.camera.lifecycle.ProcessCameraProvider;
import androidx.camera.view.PreviewView;
import androidx.core.content.ContextCompat;
import com.google.common.util.concurrent.ListenableFuture;
import com.google.mlkit.vision.barcode.*;
import com.google.mlkit.vision.barcode.common.Barcode;
import com.google.mlkit.vision.common.InputImage;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;

public final class ScanActivity extends ComponentActivity {
    private final ExecutorService analysis = Executors.newSingleThreadExecutor();
    private final AtomicBoolean delivered = new AtomicBoolean(false);
    private final BarcodeScanner scanner = BarcodeScanning.getClient(new BarcodeScannerOptions.Builder().setBarcodeFormats(Barcode.FORMAT_EAN_13).build());
    private PreviewView preview;
    private TextView message;
    private ProcessCameraProvider provider;
    private Preview boundPreview;
    private ImageAnalysis boundFrames;
    private Camera camera;
    private boolean torch;
    private final ActivityResultLauncher<String> permission = registerForActivityResult(new ActivityResultContracts.RequestPermission(), granted -> {
        if (granted) startCamera(); else showMessage("Нет доступа к камере. Разрешите его в настройках телефона или введите ISBN вручную в окне добавления книги.");
    });
    @Override public void onCreate(Bundle state) {
        super.onCreate(state);
        LinearLayout root = Ui.column(this); root.setBackgroundColor(Ui.PAPER); setContentView(root); Ui.insets(root);
        root.addView(Ui.toolbar(this,"Сканирование",this::finish));
        LinearLayout header = Ui.column(this); header.setPadding(Ui.dp(this, 16), Ui.dp(this, 12), Ui.dp(this, 16), Ui.dp(this, 12));
        message = Ui.muted(this, "", 14); message.setVisibility(View.GONE); header.addView(message);
        String savedTitle = getIntent().getStringExtra("savedTitle");
        if (savedTitle != null && !savedTitle.isEmpty()) {
            Ui.gap(header, 12);
            header.addView(new BookNotice(this, getIntent().getStringExtra("skipIsbn"), savedTitle,
                getIntent().getBooleanExtra("alreadySaved", false), true));
        }
        root.addView(header);
        preview = new PreviewView(this);preview.setImplementationMode(PreviewView.ImplementationMode.COMPATIBLE);
        preview.setBackground(Ui.round(android.graphics.Color.BLACK,this));preview.setClipToOutline(true);
        LinearLayout.LayoutParams cameraLayout=new LinearLayout.LayoutParams(-1,0,1);cameraLayout.setMargins(Ui.dp(this,16),0,Ui.dp(this,16),0);root.addView(preview,cameraLayout);
        LinearLayout controls=Ui.column(this);controls.setPadding(Ui.dp(this,16),Ui.dp(this,12),Ui.dp(this,16),Ui.dp(this,12));
        Button torchButton=Ui.button(this,"Включить фонарик",()->{});
        torchButton.setOnClickListener(v->{if(camera!=null&&camera.getCameraInfo().hasFlashUnit()){torch=!torch;camera.getCameraControl().enableTorch(torch);torchButton.setText(torch?"Выключить фонарик":"Включить фонарик");}else showMessage("На этой камере нет фонарика.");});
        controls.addView(torchButton,new LinearLayout.LayoutParams(-1,-2));root.addView(controls);
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED) startCamera();
        else permission.launch(Manifest.permission.CAMERA);
    }
    private void showMessage(String text){message.setText(text);message.setVisibility(View.VISIBLE);}
    @androidx.annotation.OptIn(markerClass = ExperimentalGetImage.class)
    private void startCamera() {
        ListenableFuture<ProcessCameraProvider> future = ProcessCameraProvider.getInstance(this);
        future.addListener(() -> {
            if (isDestroyed() || isFinishing()) return;
            try {
                provider = future.get();
                Preview view = new Preview.Builder().build(); view.setSurfaceProvider(preview.getSurfaceProvider());
                ImageAnalysis frames = new ImageAnalysis.Builder().setTargetResolution(new android.util.Size(1280, 720)).setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST).build();
                frames.setAnalyzer(analysis, frame -> {
                    if (delivered.get() || frame.getImage() == null) { frame.close(); return; }
                    try {
                        scanner.process(InputImage.fromMediaImage(frame.getImage(), frame.getImageInfo().getRotationDegrees()))
                            .addOnSuccessListener(codes -> {
                                for (Barcode code : codes) {
                                    String isbn = Isbn.normalize(code.getRawValue());
                                    if (isbn != null && !isbn.equals(getIntent().getStringExtra("skipIsbn")) && !isDestroyed() && !isFinishing() && delivered.compareAndSet(false, true)) {
                                        setResult(RESULT_OK, new Intent().putExtra("isbn", isbn)); finish(); break;
                                    }
                                }
                            })
                            .addOnFailureListener(e -> {if(!isDestroyed()&&!isFinishing())showMessage("Не удалось распознать штрихкод. Попробуйте ещё раз или введите ISBN вручную.");})
                            .addOnCompleteListener(task -> frame.close());
                    } catch (RuntimeException e) { frame.close(); }
                });
                releaseCamera();
                boundPreview=view;boundFrames=frames;
                camera = provider.bindToLifecycle(this, CameraSelector.DEFAULT_BACK_CAMERA, view, frames);
                preview.setOnTouchListener((v, event) -> {
                    if (event.getAction() == android.view.MotionEvent.ACTION_UP && camera != null) {
                        MeteringPoint point = preview.getMeteringPointFactory().createPoint(event.getX(), event.getY());
                        camera.getCameraControl().startFocusAndMetering(new FocusMeteringAction.Builder(point).build()); v.performClick();
                    }
                    return true;
                });
            } catch (Exception e) { showMessage("Не удалось открыть камеру. Можно вернуться и ввести ISBN вручную."); }
        }, ContextCompat.getMainExecutor(this));
    }
    private void releaseCamera(){
        if(boundFrames!=null)boundFrames.clearAnalyzer();
        // The provider is shared by consecutive scan screens. An older screen must
        // never unbind the camera already opened by the next one after a quick duplicate lookup.
        if(provider!=null&&boundPreview!=null&&boundFrames!=null)provider.unbind(boundPreview,boundFrames);
        boundPreview=null;boundFrames=null;camera=null;
    }
    @Override protected void onDestroy() {
        releaseCamera();
        analysis.shutdown(); scanner.close(); super.onDestroy();
    }
}
