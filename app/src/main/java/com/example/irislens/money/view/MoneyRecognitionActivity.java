package com.example.irislens.money.view;

import android.os.Bundle;
import android.view.Surface;
import android.view.WindowManager;
import android.view.MotionEvent;
import android.util.Log;
import android.widget.TextView;

import androidx.annotation.NonNull;

import com.example.irislens.R;
import com.example.irislens.money.presenter.MoneyRecognitionPresenter;
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

public class MoneyRecognitionActivity extends BaseSwipeActivity {

    private PermissionManager permissionManager;
    private CameraBridgeViewBase cameraBridgeViewBase;
    private Mat mRgba;
    private TextView tvResult;
    private MoneyRecognitionPresenter presenter;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_medicine_recognition); // Usa el mismo layout

        currentFunctionalityIndex = Functionalities.MONEY;

        cameraBridgeViewBase = findViewById(R.id.camera_view);
        tvResult = findViewById(R.id.tvResult);
        tvResult.setText("Reconocimiento de billetes. Apunte la cámara hacia el billete que desea reconocer.");

        permissionManager = new PermissionManager();
        permissionManager.getPermissions(this);

        try {
            presenter = new MoneyRecognitionPresenter(this, tvResult);
        } catch (IOException e) {
            tvResult.setText("Error cargando el modelo de billetes");
        }

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
                    presenter.onFrame(originalImage);
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