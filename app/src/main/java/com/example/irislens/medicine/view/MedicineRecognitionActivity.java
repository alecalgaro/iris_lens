package com.example.irislens.medicine.view;

import android.os.Bundle;
import android.os.Handler;

import com.example.irislens.common.ImageProcessor;
import com.example.irislens.common.TextToSpeechManager;
import android.util.Log;
import android.view.MotionEvent;
import android.view.Surface;
import android.view.WindowManager;
import android.widget.TextView;

import java.util.Collections;
import java.util.List;
import androidx.annotation.NonNull;

import com.example.irislens.common.BaseSwipeActivity;
import com.example.irislens.common.Functionalities;
import com.example.irislens.common.PermissionManager;
import com.example.irislens.R;
import com.example.irislens.medicine.presenter.MedicineRecognitionPresenter;
import org.opencv.android.CameraBridgeViewBase;
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

        // Inicializar la base de datos y sincronizarla con Firestore
        presenter.initDatabase();

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
            }

            @Override
            public void onCameraViewStopped() {
                mRgba.release();
            }

            @Override
            public Mat onCameraFrame(CameraBridgeViewBase.CvCameraViewFrame inputFrame) {
                // Obtener la imagen en formato RGBA
                Mat originalImage = inputFrame.rgba();

                // Rotar la imagen si la orientacion del dispositivo es vertical
                int rotation = getWindowManager().getDefaultDisplay().getRotation();
                if (rotation == Surface.ROTATION_0) {
                    originalImage = com.example.irislens.common.ImageProcessor.rotateImage(originalImage);
                }
                // Procesar el frame en el presentador
                if (presenter != null){
                    presenter.processCameraFrame(originalImage);
                }
                // Devolver la imagen original para mostrarla en pantalla
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
        if (ttsManager != null) {
            ttsManager.stop();
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
}
