// MoneyRecognitionActivity.java
package com.example.irislens.money.view;

import android.graphics.Bitmap;
import android.os.Bundle;
import android.util.Log;
import android.view.Surface;
import android.view.WindowManager;
import android.widget.TextView;

import androidx.annotation.NonNull;

import com.example.irislens.R;
import com.example.irislens.common.BaseSwipeActivity;
import com.example.irislens.common.Functionalities;
import com.example.irislens.common.ImageProcessor;
import com.example.irislens.common.PermissionManager;
import com.example.irislens.common.TextToSpeechManager;
import com.example.irislens.money.presenter.MoneyRecognitionPresenter;
import com.example.irislens.money.model.RoboflowMoneyDetector;

import org.opencv.android.CameraBridgeViewBase;
import org.opencv.android.OpenCVLoader;
import org.opencv.core.CvType;
import org.opencv.core.Mat;
import org.opencv.android.Utils;

import java.util.Collections;
import java.util.List;

public class MoneyRecognitionActivity extends BaseSwipeActivity {

    private PermissionManager permissionManager;
    private CameraBridgeViewBase cameraBridgeViewBase;
    private Mat mRgba;
    private TextView tvResult;
    private MoneyRecognitionPresenter presenter;
    private TextToSpeechManager ttsManager;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_money_recognition);

        currentFunctionalityIndex = Functionalities.MONEY;

        cameraBridgeViewBase = findViewById(R.id.camera_view);
        tvResult = findViewById(R.id.tvResult);
        tvResult.setText("Reconocimiento de billetes. Apunte la cámara hacia el billete que desea reconocer.");

        permissionManager = new PermissionManager();
        permissionManager.getPermissions(this);

        presenter = new MoneyRecognitionPresenter(this, tvResult);

        ttsManager = new TextToSpeechManager(this, () -> {
            ttsManager.speak("Reconocimiento de billetes. Apunte la cámara hacia el billete que desea reconocer.");
        });

        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);

        if (!OpenCVLoader.initLocal()) {
            Log.e("OpenCVInit", "Error al inicializar OpenCV");
        } else {
            Log.d("OpenCVInit", "OpenCV se inicializó correctamente");
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
                // Rotar 90 grados (ya que por defecto la camara de OpenCV viene rotada)
                originalImage = ImageProcessor.rotateImage(originalImage);
                // Pasar la imagen al presentador para el reconocimiento
                presenter.processCameraFrame(originalImage);
                return originalImage;
            }
        });
    }

    @Override
    protected void onPause() {
        super.onPause();
        if (cameraBridgeViewBase != null) {
            cameraBridgeViewBase.disableView();
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (cameraBridgeViewBase != null) {
            cameraBridgeViewBase.enableView();
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (cameraBridgeViewBase != null) {
            cameraBridgeViewBase.disableView();
        }
        presenter.onDestroy();
        if (ttsManager != null) {
            ttsManager.shutdown();
        }
    }

    @Override
    protected List<? extends CameraBridgeViewBase> getCameraViewList() {
        return Collections.singletonList(cameraBridgeViewBase);
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        permissionManager.handlePermissionsResult(requestCode, permissions, grantResults, this);
    }

    @Override
    protected void onDoubleTapDetected() {
        if (ttsManager != null) {
            ttsManager.stop();
        }
    }
}
