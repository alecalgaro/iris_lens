package com.example.irislens.display.view;

import android.os.Bundle;
import android.os.Handler;
import android.view.Surface;
import android.view.WindowManager;
import android.view.MotionEvent;
import android.util.Log;
import android.widget.TextView;

import androidx.annotation.NonNull;

import com.example.irislens.R;
import com.example.irislens.common.TextToSpeechManager;
import com.example.irislens.display.presenter.DisplayRecognitionPresenter;
import com.example.irislens.common.BaseSwipeActivity;
import com.example.irislens.common.Functionalities;
import com.example.irislens.common.PermissionManager;

import org.opencv.android.CameraBridgeViewBase;
import org.opencv.android.OpenCVLoader;
import org.opencv.core.CvType;
import org.opencv.core.Mat;

import java.io.IOException;
import java.util.Collections;
import java.util.List;

public class DisplayRecognitionActivity extends BaseSwipeActivity {
    private static final String TAG = "DisplayActivity";

    private PermissionManager permissionManager;
    private CameraBridgeViewBase cameraBridgeViewBase;
    private Mat mRgba;
    private TextView tvResult;
    private TextView tvDebug; // ✅ Nuevo: para debug
    private DisplayRecognitionPresenter presenter;
    private TextToSpeechManager ttsManager;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_display_recognition);

        currentFunctionalityIndex = Functionalities.DISPLAY;

        cameraBridgeViewBase = findViewById(R.id.camera_view);
        tvResult = findViewById(R.id.tvResult);
        tvDebug = findViewById(R.id.tvDebug); // ✅ Agregar esto al layout XML

        tvResult.setText("Reconocimiento de displays. Apunte la cámara.");

        permissionManager = new PermissionManager();
        permissionManager.getPermissions(this);

        try {
            presenter = new DisplayRecognitionPresenter(this, tvResult, tvDebug);
        } catch (IOException e) {
            Log.e(TAG, "Error cargando modelo TFLite", e);
            tvResult.setText("Error: " + e.getMessage());
        }

        ttsManager = new TextToSpeechManager(this);
        new Handler().postDelayed(() -> {
            ttsManager.speak("Reconocimiento de displays. Apunte la cámara.");
        }, 500);

        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);

        if (!OpenCVLoader.initLocal()) {
            Log.e("OpenCVInit", "Error al inicializar OpenCV");
        } else {
            cameraBridgeViewBase.enableView();
        }

        cameraBridgeViewBase.setCvCameraViewListener(new CameraBridgeViewBase.CvCameraViewListener2() {
            @Override
            public void onCameraViewStarted(int width, int height) {
                mRgba = new Mat(height, width, CvType.CV_8UC4);
            }

            @Override
            public void onCameraViewStopped() {
                mRgba.release();
            }

            @Override
            public Mat onCameraFrame(CameraBridgeViewBase.CvCameraViewFrame inputFrame) {
                Mat originalImage = inputFrame.rgba();

                int rotation = getWindowManager().getDefaultDisplay().getRotation();
                if (rotation == Surface.ROTATION_0) {
                    originalImage = com.example.irislens.common.ImageProcessor.rotateImage(originalImage);
                }

                if (presenter != null) {
                    presenter.processCameraFrame(originalImage);
                }

                return originalImage;
            }
        });
    }

    @Override
    protected void onPause() {
        super.onPause();
        if (cameraBridgeViewBase != null) cameraBridgeViewBase.disableView();
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (cameraBridgeViewBase != null) cameraBridgeViewBase.enableView();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (cameraBridgeViewBase != null) cameraBridgeViewBase.disableView();
        if (presenter != null) presenter.onDestroy();
        if (ttsManager != null) ttsManager.shutdown();
    }

    @Override
    protected void onDoubleTapDetected() {
        if (presenter != null) presenter.onDoubleTap();
    }

    @Override
    protected List<? extends CameraBridgeViewBase> getCameraViewList() {
        return Collections.singletonList(cameraBridgeViewBase);
    }

    @Override
    public boolean dispatchTouchEvent(MotionEvent ev) {
        return super.dispatchTouchEvent(ev);
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        permissionManager.handlePermissionsResult(requestCode, permissions, grantResults, this);
    }
}