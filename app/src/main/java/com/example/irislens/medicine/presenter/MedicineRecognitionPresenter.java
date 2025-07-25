package com.example.irislens.medicine.presenter;

import android.app.Activity;
import android.graphics.Bitmap;
import android.os.Handler;
import android.os.Looper;
import android.widget.TextView;

import com.example.irislens.medicine.model.DatabaseManager;
import com.example.irislens.common.ImageProcessor;
import com.example.irislens.medicine.model.ReadImageText;
import com.example.irislens.common.TextToSpeechManager;
import com.example.irislens.medicine.model.Tools;

import org.opencv.core.Mat;

import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import androidx.core.util.Pair;

/**
 * Presenter para el reconocimiento de medicamentos.
 * Maneja la logica de procesamiento de imagenes para el reconocimiento.
 */
public class MedicineRecognitionPresenter {
    private final Activity activity;
    private final TextView tvResult;
    private final DatabaseManager dbManager;
    private final TextToSpeechManager ttsManager;
    private final ReadImageText readImageText;
    private final ExecutorService executor;
    private int noDetectionCount = 0;
    private boolean rotate = false;
    private int rotationState = 0; // 0 = sin rotacion, 1 = 180°, 2 = 90°, 3 = 270°
    private int frameCount = 0;
    private volatile boolean isProcessing = false;

    public MedicineRecognitionPresenter(Activity activity, TextView tvResult) {
        this.activity = activity;
        this.tvResult = tvResult;
        this.dbManager = new DatabaseManager(activity.getApplicationContext());
        this.ttsManager = new TextToSpeechManager(activity);
        this.readImageText = new ReadImageText(activity.getApplicationContext());
        this.executor = Executors.newSingleThreadExecutor();
    }

    // Rotar la imagen 90 grados (por defecto la camara de OpenCV viene rotada)
    public Mat rotateImage(Mat image) {
        return ImageProcessor.rotateImage(image);
    }

    // Procesar un frame de la camara
    public void onFrame(Mat image) {
        if (isProcessing) return;
        frameCount++;
        if (frameCount == 10) {
            if (!ttsManager.isSpeaking()) {
                // Preprocesar la imagen para mejorar la visibilidad del texto
                Pair<Mat, Double> processedImageAndBrightness = ImageProcessor.preprocessImage(image);
                Mat mRgba = processedImageAndBrightness.first;
                double meanBrightness = processedImageAndBrightness.second;
                if (meanBrightness < 10) {
                    ttsManager.speak("Debe estar en un lugar más iluminado para evitar errores de detección");
                }

                // Si no se ha detectado nada antes, intenta las rotaciones
                if (noDetectionCount > 0 && rotate) {
                    switch (rotationState) {
                        case 0:
                            processImageAndSearchMatches(mRgba);
                            rotationState = 1;
                            break;
                        case 1:
                            mRgba = ImageProcessor.rotateImage180(mRgba);
                            processImageAndSearchMatches(mRgba);
                            rotationState = 2;
                            break;
                        case 2:
                            mRgba = ImageProcessor.rotateImage90(mRgba);
                            processImageAndSearchMatches(mRgba);
                            rotationState = 3;
                            break;
                        case 3:
                            mRgba = ImageProcessor.rotateImage270(mRgba);
                            processImageAndSearchMatches(mRgba);
                            rotationState = 0;
                            break;
                    }
                } else {
                    processImageAndSearchMatches(mRgba);
                }
            }
            frameCount = 0;
        }
    }

    // Procesar la imagen y buscar coincidencias
    private void processImageAndSearchMatches(Mat image) {
        Bitmap bitmap = ImageProcessor.convertToBitmap(image);
        isProcessing = true;
        Handler handler = new Handler(Looper.getMainLooper());

        executor.execute(() -> {
            String result = readImageText.processImage(bitmap);
            handler.post(() -> handleImageProcessingResult(result));
        });
    }

    // Manejar el resultado del procesamiento de la imagen
    private void handleImageProcessingResult(String result) {
        String finalResult = Tools.cleanupText(result);
        List<Pair<String, String>> matches = Tools.searchSimilarity(finalResult, dbManager.getReadableDatabase());
        activity.runOnUiThread(() -> {
            if (!matches.isEmpty()) {
                // Si se encontraron coincidencias, muestra las descripciones de los medicamentos
                StringBuilder sb = new StringBuilder();
                for (Pair<String, String> match : matches) {
                    sb.append(match.second).append("\n");
                }
                ttsManager.speak(sb.toString());
                tvResult.setText(sb.toString());
                noDetectionCount = 0;
                rotate = false;
                rotationState = 0;
            } else {
                // Si no se encontraron coincidencias
                noDetectionCount++;
                rotate = true;
                tvResult.setText("");
            }

            // Si no se ha detectado nada en 8 intentos, reproducir un mensaje de audio
            if (noDetectionCount == 8) {
                ttsManager.speak("No se pudo detectar. Mejore la posición de la cámara o del objeto.");
                noDetectionCount = 0;
                rotate = false;
            }

            isProcessing = false;
        });
    }

    // Detener TTS y limpiar resultado en doble tap
    public void onDoubleTap() {
        if (ttsManager.isSpeaking()) {
            ttsManager.stop();
            tvResult.setText("");
        }
    }

    // Liberar recursos
    public void onDestroy() {
        ttsManager.shutdown();
        executor.shutdown();
    }
}
