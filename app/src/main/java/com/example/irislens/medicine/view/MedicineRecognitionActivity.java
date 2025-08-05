package com.example.irislens.medicine.view;

import android.content.Context;
import android.database.sqlite.SQLiteDatabase;
import android.hardware.Camera;
import android.os.Bundle;
import android.os.Handler;
import com.example.irislens.common.TextToSpeechManager;
import android.util.Log;
import android.view.MotionEvent;
import android.view.WindowManager;
import android.view.Surface;
import android.widget.TextView;
import android.widget.Toast;

import java.util.Collections;
import java.util.List;
import androidx.annotation.NonNull;

import com.example.irislens.common.BaseSwipeActivity;
import com.example.irislens.common.Functionalities;
import com.example.irislens.common.PermissionManager;
import com.example.irislens.R;
import com.example.irislens.medicine.model.MedicineDbHelper;
import com.example.irislens.medicine.presenter.MedicineRecognitionPresenter;
import org.opencv.android.CameraBridgeViewBase;
import org.opencv.core.Core;
import org.opencv.core.CvType;
import org.opencv.core.Mat;
import org.opencv.android.OpenCVLoader;

/**
 * Actividad que realiza el reconocimiento de medicamentos
 */
public class MedicineRecognitionActivity extends BaseSwipeActivity {

    // Encargado de manejar los permisos necesarios para acceder a la camara y otros recursos
    private PermissionManager permissionManager;

    // Componente visual de OpenCV encargado de mostrar la vista en vivo desde la camara
    private CameraBridgeViewBase cameraBridgeViewBase;

    // Matriz que contiene la imagen capturada en formato RGBA
    private Mat mRgba;

    // TextView donde se muestra el resultado del reconocimiento de medicamentos
    private TextView tvResult;

    // Encapsula la logica de presentacion para el reconocimiento de medicamentos
    private MedicineRecognitionPresenter presenter;

    // Encargado de sintetizar voz a partir de texto
    private TextToSpeechManager ttsManager;
    private int rotationDegrees = 0;
    private boolean needToRotate = false;

    /**
     * Configura la camara, permisos y procesamiento de imagen
     *
     * @param savedInstanceState Estado guardado
     */
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_medicine_recognition);

        currentFunctionalityIndex = Functionalities.MEDICINE; // indice para el swipe de esta funcionalidad

        cameraBridgeViewBase = findViewById(R.id.camera_view);
        tvResult = findViewById(R.id.tvResult);
        tvResult.setText("Reconocimiento de medicamentos. Apunte la cámara hacia el objeto que desea reconocer.");

        // Inicializar PermissionManager y solicitar permiso de camara
        permissionManager = new PermissionManager();
        permissionManager.getPermissions(this);

        // Inicializar Presenter para funcionalidad de reconocimiento de medicamentos
        presenter = new MedicineRecognitionPresenter(this, tvResult);

        // Crear los registros de medicamentos y principios activos en la base de datos local
        MedicineDbHelper dbHelper = new MedicineDbHelper(this);
        SQLiteDatabase db = dbHelper.getWritableDatabase();
        if(db != null) {
            Toast.makeText(this, "Actualizando base de datos...", Toast.LENGTH_SHORT).show();
            presenter.sincronizarConFirestore(db);
        }

        // Mensaje de voz sobre la funcionalidad
        ttsManager = new TextToSpeechManager(this);
        // Espera breve para asegurar que TTS este listo
        new Handler().postDelayed(() -> {
            ttsManager.speak("Reconocimiento de medicamentos. " +
                    "Apunte la cámara hacia el objeto que desea reconocer.");
        }, 500);

        // Mantener la pantalla encendida mientras esta actividad esta en primer plano
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);

        // Inicializar OpenCV y habilitar la vista de la camara
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

                int cameraId = 0; // 0: camara trasera
                Camera.CameraInfo info = new Camera.CameraInfo();
                Camera.getCameraInfo(cameraId, info);

                int cameraOrientation = info.orientation;
                boolean isFrontFacing = (info.facing == Camera.CameraInfo.CAMERA_FACING_FRONT);

                int deviceRotation = getDeviceRotation();

                rotationDegrees = calculateRotation(isFrontFacing, cameraOrientation, deviceRotation);
                needToRotate = (rotationDegrees != 0);
            }

            @Override
            public void onCameraViewStopped() {
                mRgba.release();
            }

            @Override
            public Mat onCameraFrame(CameraBridgeViewBase.CvCameraViewFrame inputFrame) {
                Mat originalImage = inputFrame.rgba();

                // Aplicar rotacion de la camara solo si es necesario
                if (needToRotate) {
                    Mat rotated = new Mat();
                    int code = getOpenCVRotationCode(rotationDegrees);
                    if (code != -1) {
                        Core.rotate(originalImage, rotated, code);
                        originalImage = rotated;
                    }
                }

                presenter.onFrame(originalImage);
                return originalImage;
            }
        });
    }

    /**
     * Desactiva la camara al pausar la actividad
     */
    @Override
    protected void onPause() {
        super.onPause();
        if (cameraBridgeViewBase != null) {
            cameraBridgeViewBase.disableView();
        }
    }

    /**
     * Reactiva la camara al volver a la funcionalidad
     */
    @Override
    protected void onResume() {
        super.onResume();
        if (cameraBridgeViewBase != null) {
            cameraBridgeViewBase.enableView();
        }
    }

    /**
     * Libera recursos al destruir la actividad
     */
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


    /**
     * Heredada, cuando detecta doble tap, pausa el audio
     */
    @Override
    protected void onDoubleTapDetected() {
        if (presenter != null) {
            presenter.onDoubleTap();
        }
    }

    @Override
    protected List<? extends CameraBridgeViewBase> getCameraViewList() {
        return Collections.singletonList(cameraBridgeViewBase);
    }

    /**
     * Solicitar permisos de camara
     */
    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        permissionManager.handlePermissionsResult(requestCode, permissions, grantResults, this);
    }

    /**
     * Intercepta todos los eventos tactiles antes de que sean procesados por las vistas hijas
     */
    @Override
    public boolean dispatchTouchEvent(MotionEvent ev) {
        return super.dispatchTouchEvent(ev);
    }

    private int getDeviceRotation() {
        int rotation = ((WindowManager) getSystemService(Context.WINDOW_SERVICE))
                .getDefaultDisplay().getRotation();
        switch (rotation) {
            case Surface.ROTATION_0:
                return 0;
            case Surface.ROTATION_90:
                return 90;
            case Surface.ROTATION_180:
                return 180;
            case Surface.ROTATION_270:
                return 270;
            default:
                return 0;
        }
    }

    private int calculateRotation(boolean isFrontFacing, int cameraOrientation, int deviceRotation) {
        int result;
        if (isFrontFacing) {
            result = (cameraOrientation + deviceRotation) % 360;
            result = (360 - result) % 360; // Compensar espejo
        } else {
            result = (cameraOrientation - deviceRotation + 360) % 360;
        }
        return result;
    }

    private int getOpenCVRotationCode(int degrees) {
        switch (degrees) {
            case 90:
                return Core.ROTATE_90_CLOCKWISE;
            case 180:
                return Core.ROTATE_180;
            case 270:
                return Core.ROTATE_90_COUNTERCLOCKWISE;
            default:
                return -1;
        }
    }

}
