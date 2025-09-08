package com.example.irislens.medicine.view;

import android.content.res.Configuration;
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
 * VERSIÓN UNIVERSAL - funciona en CUALQUIER dispositivo Android sin importar marca/modelo
 */
public class MedicineRecognitionActivity extends BaseSwipeActivity {

    private PermissionManager permissionManager;
    private CameraBridgeViewBase cameraBridgeViewBase;
    private Mat mRgba;
    private TextView tvResult;
    private MedicineRecognitionPresenter presenter;
    private TextToSpeechManager ttsManager;

    // Variables para detección automática UNIVERSAL
    private int detectedRotation = -1;
    private boolean rotationDetected = false;
    private int frameCounter = 0;
    private int[] rotationCandidates = new int[5]; // Array para estabilizar detección

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_medicine_recognition);

        currentFunctionalityIndex = Functionalities.MEDICINE;

        cameraBridgeViewBase = findViewById(R.id.camera_view);
        tvResult = findViewById(R.id.tvResult);
        tvResult.setText("Reconocimiento de medicamentos. Apunte la cámara hacia el objeto que desea reconocer.");

        permissionManager = new PermissionManager();
        permissionManager.getPermissions(this);

        presenter = new MedicineRecognitionPresenter(this, tvResult);
        presenter.initDatabase();

        ttsManager = new TextToSpeechManager(this);
        new Handler().postDelayed(() -> {
            ttsManager.speak("Reconocimiento de medicamentos. " +
                    "Apunte la cámara hacia el objeto que desea reconocer.");
        }, 500);

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
                Log.d("CameraSetup", "Cámara iniciada: " + width + "x" + height);
            }

            @Override
            public void onCameraViewStopped() {
                if (mRgba != null) {
                    mRgba.release();
                }
            }

            @Override
            public Mat onCameraFrame(CameraBridgeViewBase.CvCameraViewFrame inputFrame) {
                Mat originalImage = null;
                try {
                    originalImage = inputFrame.rgba();

                    if (originalImage == null || originalImage.empty()) {
                        return originalImage;
                    }

                    // DETECCIÓN ESTABILIZADA - múltiples métodos sin depender del modelo
                    if (!rotationDetected) {
                        int currentRotation = detectUniversalRotation(originalImage);

                        // Almacenar candidato para estabilización
                        rotationCandidates[frameCounter % rotationCandidates.length] = currentRotation;
                        frameCounter++;

                        // Decidir rotación después de varios frames
                        if (frameCounter >= rotationCandidates.length) {
                            detectedRotation = getMostConsistentRotation();
                            rotationDetected = true;
                            Log.d("CameraRotation", "Rotación final detectada: " + detectedRotation + "°");
                        }
                    }

                    // Aplicar rotación si es necesaria
                    if (rotationDetected && detectedRotation > 0) {
                        Mat rotatedImage = applyOptimalRotation(originalImage, detectedRotation);
                        if (rotatedImage != null && !rotatedImage.empty()) {
                            originalImage = rotatedImage;
                        }
                    }

                    // Procesar frame
                    if (presenter != null) {
                        presenter.processCameraFrame(originalImage);
                    }

                    return originalImage;

                } catch (Exception e) {
                    Log.e("CameraFrame", "Error en frame: " + e.getMessage(), e);
                    return originalImage != null ? originalImage : createSafeEmptyFrame();
                }
            }
        });
    }

    /**
     * Detección UNIVERSAL que funciona sin importar marca/modelo
     * Combina MÚLTIPLES indicadores para máxima compatibilidad
     */
    private int detectUniversalRotation(Mat image) {
        try {
            if (image == null || image.empty()) {
                return 0;
            }

            int imageWidth = image.cols();
            int imageHeight = image.rows();

            // INDICADOR 1: Relación de aspecto de la imagen
            boolean imageIsLandscape = imageWidth > imageHeight;
            double aspectRatio = (double) imageWidth / imageHeight;

            // INDICADOR 2: Configuración del sistema Android
            int systemOrientation = getResources().getConfiguration().orientation;
            boolean systemIsPortrait = (systemOrientation == Configuration.ORIENTATION_PORTRAIT);

            // INDICADOR 3: Rotación del display
            int displayRotation = getWindowManager().getDefaultDisplay().getRotation();

            // INDICADOR 4: Dimensiones de la pantalla
            android.util.DisplayMetrics displayMetrics = getResources().getDisplayMetrics();
            boolean screenIsPortrait = displayMetrics.heightPixels > displayMetrics.widthPixels;

            Log.d("UniversalRotation", String.format(
                    "Imagen: %dx%d (aspect: %.2f, landscape: %s)",
                    imageWidth, imageHeight, aspectRatio, imageIsLandscape));
            Log.d("UniversalRotation", String.format(
                    "Sistema: orientation=%d, display_rotation=%d, screen_portrait=%s",
                    systemOrientation, displayRotation, screenIsPortrait));

            // LÓGICA UNIVERSAL: Consenso entre múltiples indicadores
            int rotationScore0 = 0;   // Sin rotación
            int rotationScore90 = 0;  // 90 grados
            int rotationScore180 = 0; // 180 grados
            int rotationScore270 = 0; // 270 grados

            // Votos basados en imagen vs sistema
            if (imageIsLandscape && systemIsPortrait) {
                rotationScore90 += 2; // Fuerte indicador
            }
            if (!imageIsLandscape && !systemIsPortrait) {
                rotationScore270 += 2; // Fuerte indicador
            }
            if (imageIsLandscape == !systemIsPortrait) {
                rotationScore0 += 1; // Orientaciones coinciden
            }

            // Votos basados en display rotation
            switch (displayRotation) {
                case Surface.ROTATION_0:
                    if (imageIsLandscape) rotationScore90 += 1;
                    else rotationScore0 += 1;
                    break;
                case Surface.ROTATION_90:
                    if (imageIsLandscape) rotationScore0 += 1;
                    else rotationScore270 += 1;
                    break;
                case Surface.ROTATION_180:
                    if (imageIsLandscape) rotationScore270 += 1;
                    else rotationScore180 += 1;
                    break;
                case Surface.ROTATION_270:
                    if (imageIsLandscape) rotationScore180 += 1;
                    else rotationScore90 += 1;
                    break;
            }

            // Votos basados en relación de aspecto extrema
            if (aspectRatio > 1.5) {  // Muy landscape
                if (systemIsPortrait) rotationScore90 += 1;
            }
            if (aspectRatio < 0.67) { // Muy portrait
                if (!systemIsPortrait) rotationScore270 += 1;
            }

            // Encontrar la rotación con mayor puntaje
            int maxScore = Math.max(Math.max(rotationScore0, rotationScore90),
                    Math.max(rotationScore180, rotationScore270));

            int selectedRotation = 0;
            if (maxScore == rotationScore90) selectedRotation = 90;
            else if (maxScore == rotationScore180) selectedRotation = 180;
            else if (maxScore == rotationScore270) selectedRotation = 270;

            Log.d("UniversalRotation", String.format(
                    "Scores - 0°:%d, 90°:%d, 180°:%d, 270°:%d -> Seleccionado: %d°",
                    rotationScore0, rotationScore90, rotationScore180, rotationScore270, selectedRotation));

            return selectedRotation;

        } catch (Exception e) {
            Log.e("UniversalRotation", "Error en detección: " + e.getMessage(), e);
            return 0;
        }
    }

    /**
     * Obtiene la rotación más consistente de los últimos frames
     */
    private int getMostConsistentRotation() {
        // Contar frecuencia de cada rotación detectada
        int[] counts = new int[4]; // 0°, 90°, 180°, 270°

        for (int rotation : rotationCandidates) {
            switch (rotation) {
                case 0: counts[0]++; break;
                case 90: counts[1]++; break;
                case 180: counts[2]++; break;
                case 270: counts[3]++; break;
            }
        }

        // Encontrar la rotación más frecuente
        int maxCount = 0;
        int mostFrequentRotation = 0;

        for (int i = 0; i < counts.length; i++) {
            if (counts[i] > maxCount) {
                maxCount = counts[i];
                mostFrequentRotation = i * 90;
            }
        }

        Log.d("RotationStabilization", String.format(
                "Frecuencias - 0°:%d, 90°:%d, 180°:%d, 270°:%d -> Elegido: %d°",
                counts[0], counts[1], counts[2], counts[3], mostFrequentRotation));

        return mostFrequentRotation;
    }

    /**
     * Aplica rotación de forma óptima y segura
     */
    private Mat applyOptimalRotation(Mat source, int degrees) {
        if (source == null || source.empty() || degrees == 0) {
            return source;
        }

        try {
            degrees = ((degrees % 360) + 360) % 360;

            Mat result = new Mat();

            switch (degrees) {
                case 90:
                    org.opencv.core.Core.transpose(source, result);
                    org.opencv.core.Core.flip(result, result, 1);
                    break;
                case 180:
                    org.opencv.core.Core.flip(source, result, -1);
                    break;
                case 270:
                    org.opencv.core.Core.transpose(source, result);
                    org.opencv.core.Core.flip(result, result, 0);
                    break;
                default:
                    return source;
            }

            return result;

        } catch (Exception e) {
            Log.e("Rotation", "Error al aplicar rotación: " + e.getMessage(), e);
            return source;
        }
    }

    /**
     * Crear frame seguro como fallback
     */
    private Mat createSafeEmptyFrame() {
        try {
            return new Mat(480, 640, CvType.CV_8UC4);
        } catch (Exception e) {
            return null;
        }
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
        // Resetear para re-evaluar
        rotationDetected = false;
        detectedRotation = -1;
        frameCounter = 0;
        rotationCandidates = new int[5];
    }

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

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        permissionManager.handlePermissionsResult(requestCode, permissions, grantResults, this);
    }

    @Override
    public boolean dispatchTouchEvent(MotionEvent ev) {
        return super.dispatchTouchEvent(ev);
    }
}