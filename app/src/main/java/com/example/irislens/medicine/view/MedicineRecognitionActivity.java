package com.example.irislens.medicine.view;

import android.database.sqlite.SQLiteDatabase;
import android.os.Bundle;
import android.util.Log;
import android.view.GestureDetector;
import android.view.MotionEvent;
import android.view.WindowManager;
import android.widget.TextView;

import java.util.Collections;
import java.util.List;

import androidx.annotation.NonNull;

import com.example.irislens.common.PermissionManager;
import com.example.irislens.R;
import com.example.irislens.medicine.model.MedicineDbHelper;
import com.example.irislens.medicine.presenter.MedicineRecognitionPresenter;

import org.opencv.android.CameraActivity;
import org.opencv.android.CameraBridgeViewBase;
import org.opencv.core.CvType;
import org.opencv.core.Mat;
import org.opencv.android.OpenCVLoader;

public class MedicineRecognitionActivity extends CameraActivity {
    private PermissionManager permissionManager;
    private CameraBridgeViewBase cameraBridgeViewBase;
    private Mat mRgba;
    private TextView tvResult;
    private GestureDetector gestureDetector;
    private MedicineRecognitionPresenter presenter;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        // Crear los registros de medicamentos y principios activos en la base de datos local
        MedicineDbHelper dbHelper = new MedicineDbHelper(this);
        SQLiteDatabase db = dbHelper.getWritableDatabase();

        cameraBridgeViewBase = findViewById(R.id.camera_view);
        tvResult = findViewById(R.id.tvResult);

        // Mantener la pantalla encendida mientras esta actividad esta en primer plano
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);

        // Inicializar OpenCV y habilitar la vista de la camara
        if (!OpenCVLoader.initLocal()) {
            Log.e("OpenCVInit", "Error al inicializar OpenCV");
        } else {
            Log.d("OpenCVInit", "OpenCV se inicializó correctamente");
            cameraBridgeViewBase.enableView();
        }

        // Inicializar PermissionManager y solicitar permiso de camara
        permissionManager = new PermissionManager();
        permissionManager.getPermissions(this);

        // Inicializar GestureDetector
        gestureDetector = new GestureDetector(this, new GestureDetector.SimpleOnGestureListener() {
            // Este metodo se ejecuta cuando se detecta un doble tap en la pantalla
            @Override
            public boolean onDoubleTap(MotionEvent e) {
                presenter.onDoubleTap();
                return true;
            }
        });

        // Inicializar Presenter para funcionalidad de reconocimiento de medicamentos
        presenter = new MedicineRecognitionPresenter(this, tvResult);

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
                // Imagen original de la camara de OpenCV
                Mat originalImage = inputFrame.rgba();

                // Rotar 90 grados (ya que por defecto la camara de OpenCV viene rotada)
                originalImage = presenter.rotateImage(originalImage);

                presenter.onFrame(originalImage);

                //return mRgba;
                return originalImage;
            }
        });
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        // Liberar los recursos de la vista de camara de OpenCV
        if (cameraBridgeViewBase != null) {
            cameraBridgeViewBase.disableView();
        }
        presenter.onDestroy();
    }

    // Metodo para pasar el evento tactil al GestureDetector
    // Utilizo el GestureDetector para cortar un audio con doble tap en pantalla
    @Override
    public boolean onTouchEvent(MotionEvent event) {
        return gestureDetector.onTouchEvent(event) || super.onTouchEvent(event);
    }

    @Override
    protected List<? extends CameraBridgeViewBase> getCameraViewList() {
        return Collections.singletonList(cameraBridgeViewBase);
    }

    // Solicitar permisos de camara
    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        permissionManager.handlePermissionsResult(requestCode, permissions, grantResults, this);
    }
}