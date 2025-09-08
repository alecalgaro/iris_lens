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

    // Variables para detección automática de rotación
    private int detectedRotation = -1;  // -1 significa no detectado aún
    private boolean rotationDetected = false;

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
                Mat originalImage = null;
                try {
                    // Obtener la imagen en formato RGBA
                    originalImage = inputFrame.rgba();

                    if (originalImage == null || originalImage.empty()) {
                        Log.w("CameraRotation", "Frame vacío recibido");
                        return originalImage;
                    }

                    // Detectar automáticamente la rotación correcta en los primeros frames
                    if (!rotationDetected) {
                        detectedRotation = detectOptimalRotation(originalImage);
                        rotationDetected = true;
                        Log.d("CameraRotation", "Rotación detectada: " + detectedRotation + " grados");
                    }

                    // Aplicar la rotación detectada
                    if (detectedRotation > 0) {
                        Mat rotatedImage = rotateImageSafely(originalImage, detectedRotation);
                        if (rotatedImage != null && !rotatedImage.empty()) {
                            originalImage = rotatedImage;
                        }
                    }

                    // Procesar el frame en el presentador
                    if (presenter != null) {
                        presenter.processCameraFrame(originalImage);
                    }

                    // Devolver la imagen corregida para mostrarla en pantalla
                    return originalImage;

                } catch (Exception e) {
                    Log.e("CameraRotation", "Error en onCameraFrame: " + e.getMessage(), e);
                    // En caso de error, devolver imagen original o crear una imagen vacía
                    if (originalImage != null && !originalImage.empty()) {
                        return originalImage;
                    } else {
                        return new Mat(480, 640, CvType.CV_8UC4); // Imagen vacía como fallback
                    }
                }
            }
        });
    }

    /**
     * Detecta la rotación óptima basándose en las dimensiones de la imagen y orientación del dispositivo
     */
    private int detectOptimalRotation(Mat image) {
        try {
            if (image == null || image.empty()) {
                Log.w("CameraRotation", "Imagen nula o vacía para detección");
                return 0;
            }

            int rotation = getWindowManager().getDefaultDisplay().getRotation();
            int imageWidth = image.cols();
            int imageHeight = image.rows();

            Log.d("CameraRotation", "Dimensiones imagen: " + imageWidth + "x" + imageHeight);
            Log.d("CameraRotation", "Rotación dispositivo: " + rotation);

            // Lógica simplificada y más robusta
            boolean imageIsLandscape = imageWidth > imageHeight;
            boolean deviceIsPortrait = (rotation == Surface.ROTATION_0 || rotation == Surface.ROTATION_180);

            if (imageIsLandscape && deviceIsPortrait) {
                Log.d("CameraRotation", "Detectado: imagen landscape en dispositivo portrait -> rotar 90°");
                return 90;
            } else if (!imageIsLandscape && !deviceIsPortrait) {
                Log.d("CameraRotation", "Detectado: imagen portrait en dispositivo landscape -> rotar 270°");
                return 270;
            }

            // Si las orientaciones coinciden, no rotar
            Log.d("CameraRotation", "Orientaciones coinciden -> sin rotación");
            return 0;

        } catch (Exception e) {
            Log.e("CameraRotation", "Error al detectar rotación: " + e.getMessage(), e);
            return 0; // Sin rotación por defecto si hay error
        }
    }

    /**
     * Rota una imagen de forma segura
     */
    private Mat rotateImageSafely(Mat source, int degrees) {
        if (source == null || source.empty() || degrees == 0) {
            return source;
        }

        try {
            // Normalizar grados a múltiplos de 90
            degrees = ((degrees % 360) + 360) % 360;
            Log.d("CameraRotation", "Aplicando rotación de " + degrees + "°");

            Mat rotated = new Mat();

            // Solo usar los métodos más seguros y eficientes
            if (degrees == 90) {
                // Rotar 90° en sentido horario
                org.opencv.core.Core.transpose(source, rotated);
                org.opencv.core.Core.flip(rotated, rotated, 1);
                return rotated;
            } else if (degrees == 180) {
                // Rotar 180°
                org.opencv.core.Core.flip(source, rotated, -1);
                return rotated;
            } else if (degrees == 270) {
                // Rotar 270° (o -90°)
                org.opencv.core.Core.transpose(source, rotated);
                org.opencv.core.Core.flip(rotated, rotated, 0);
                return rotated;
            } else {
                Log.w("CameraRotation", "Rotación no estándar (" + degrees + "°), sin rotar");
                return source;
            }

        } catch (Exception e) {
            Log.e("CameraRotation", "Error al rotar imagen: " + e.getMessage(), e);
            return source; // Devolver imagen original si hay error
        }
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
        // Resetear detección de rotación al reanudar
        rotationDetected = false;
        detectedRotation = -1;
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
        if (presenter != null) {
            presenter.onDestroy();
        }

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