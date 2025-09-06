package com.example.irislens.money.presenter;

import android.app.Activity;
import android.graphics.Bitmap;
import android.graphics.RectF;
import android.media.MediaPlayer;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.widget.TextView;

import androidx.annotation.NonNull;

import com.example.irislens.R;
import com.example.irislens.common.ImageProcessor;
import com.example.irislens.common.TextToSpeechManager;
import com.example.irislens.money.model.MoneyDetector;

import org.opencv.core.Mat;

import java.io.IOException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class MoneyRecognitionPresenter {
    private static final String TAG = "MoneyPresenter";

    private final Activity activity;
    private final TextView tvResult;
    private final MoneyDetector detector;
    private final TextToSpeechManager ttsManager;
    private final ExecutorService executor;
    private final Handler mainHandler;

    private volatile boolean isProcessing = false;
    private int frameCount = 0;

    private static final int NO_DETECTION_THRESHOLD = 8; // limite de frames seguidos sin deteccion
    private int noDetectionCount = 0;
    private String lastResultText = "";

    public MoneyRecognitionPresenter(Activity activity, TextView tvResult) throws IOException {
        this.activity = activity;
        this.tvResult = tvResult;
        this.ttsManager = new TextToSpeechManager(activity);
        this.executor = Executors.newSingleThreadExecutor();
        this.mainHandler = new Handler(Looper.getMainLooper());

        // Inicializar MoneyDetector con TensorFlow Lite directo
        this.detector = new MoneyDetector(activity.getApplicationContext());
    }

    /** Procesa un frame de la camara.
     * @param image Frame en formato Mat (OpenCV)
     */
    public void onFrame(Mat image) {
        // Evitar procesamiento concurrente
        if (isProcessing) return;
        frameCount++;
        if (frameCount == 10) {
            if (!ttsManager.isSpeaking()) {
                // Reproducir sonido de captura
                MediaPlayer mediaPlayer = MediaPlayer.create(activity, R.raw.captura);
                if (mediaPlayer != null) {
                    mediaPlayer.start();
                    mediaPlayer.setOnCompletionListener(MediaPlayer::release);
                }

                // Convertir Mat a Bitmap
                Bitmap bitmap = ImageProcessor.convertToBitmap(image);
                isProcessing = true;

                detector.detect(bitmap, new MoneyDetector.DetectionCallback() {
                    @Override
                    public void onDetectionComplete(@NonNull List<MoneyDetector.DetectionResult> results) {
                        // El callback ya se ejecuta en background, llevamos a main thread
                        mainHandler.post(() -> handleDetection(results));
                    }

                    @Override
                    public void onDetectionError(@NonNull Exception error) {
                        Log.e(TAG, "Error en detección TensorFlow Lite", error);
                        mainHandler.post(() -> {
                            isProcessing = false;
                            tvResult.setText("Error en la detección: " + error.getMessage());
                        });
                    }
                });
            }
            frameCount = 0;
        }
    }

    /**
     * Maneja los resultados de detección del nuevo MoneyDetector
     * @param results Lista de MoneyDetector.DetectionResult
     */
    private void handleDetection(List<MoneyDetector.DetectionResult> results) {
        if (results.isEmpty()) {
            noDetectionCount++;

            // Mantener el texto vacio si no hay deteccion
            tvResult.setText("");

            // Si se supera el umbral de no deteccion, emitir mensaje de aviso
            if (noDetectionCount >= NO_DETECTION_THRESHOLD) {
                ttsManager.speak("No se pudo detectar. Mejore la posición de la cámara o del objeto.");
                Log.d("MoneyPresenter", "⚠️ No se pudo detectar tras " + NO_DETECTION_THRESHOLD + " frames");
                noDetectionCount = 0; // reset contador
            }
        } else {
            noDetectionCount = 0; // reset contador si hay deteccion

            // Agrupar detecciones de billetes por label
            Map<String, Integer> countByLabel = new HashMap<>();
            Map<String, Float> maxConfidenceByLabel = new HashMap<>();

            for (MoneyDetector.DetectionResult detection : results) {
                String label = detection.getLabel();
                float confidence = detection.getConfidence();
                RectF box = detection.getBoundingBox(); // Ahora es RectF

                // Log detallado
                Log.d(TAG, String.format(
                        "Detectado -> Label: %s | Score: %.2f | Box: [%.1f,%.1f,%.1f,%.1f]",
                        label, confidence, box.left, box.top, box.right, box.bottom
                ));

                // Agrupar por etiqueta
                Integer currentCount = countByLabel.get(label);
                countByLabel.put(label, (currentCount == null) ? 1 : currentCount + 1);

                // Mantener la confianza máxima por etiqueta
                Float currentMaxConf = maxConfidenceByLabel.get(label);
                if (currentMaxConf == null || confidence > currentMaxConf) {
                    maxConfidenceByLabel.put(label, confidence);
                }
            }

            // Construir texto para pantalla y TTS
            StringBuilder displayText = new StringBuilder();
            StringBuilder ttsText = new StringBuilder();

            for (Map.Entry<String, Integer> entry : countByLabel.entrySet()) {
                String fullLabel = entry.getKey();
                int count = entry.getValue();
                float maxConf = maxConfidenceByLabel.get(fullLabel);

                // Procesar el label para extraer información relevante
                String processedLabel = processLabel(fullLabel);

                // Texto para pantalla (más detallado)
                displayText.append(String.format("%d de %s (%.1f%%)\n",
                        count, processedLabel, maxConf * 100));

                // Texto para TTS (más simple)
                String[] parts = fullLabel.split("_");
                String simpleLabel = parts.length > 0 ? parts[0] : fullLabel;
                ttsText.append(String.format("%d de %s ", count, simpleLabel));
            }

            // Actualizar UI
            lastResultText = displayText.toString().trim();
            tvResult.setText(lastResultText);

            // Mensaje de voz
            String speechText = ttsText.toString().trim();
            if (!speechText.isEmpty()) {
                ttsManager.speak(speechText);
            }
        }

        isProcessing = false;
    }

    /**
     * Procesa el label para hacerlo más legible
     * @param rawLabel Label original del modelo
     * @return Label procesado para mostrar
     */
    private String processLabel(String rawLabel) {
        if (rawLabel == null || rawLabel.isEmpty()) {
            return "billete desconocido";
        }

        // Reemplazar guiones bajos por espacios y mejorar legibilidad
        String processed = rawLabel.replace("_", " ");

        // Casos específicos para billetes argentinos
        processed = processed.replace(" f ", " frente ")
                .replace(" d ", " dorso ")
                .replace("heroes", "héroes")
                .replace("animales", "de animales");

        return processed;
    }

    /** Obtiene estadisticas de las detecciones para debugging */
    private void logDetectionStats(List<MoneyDetector.DetectionResult> results) {
        if (results.isEmpty()) {
            Log.d(TAG, "Sin detecciones en este frame");
            return;
        }

        float avgConfidence = 0f;
        float maxConfidence = 0f;
        float minConfidence = 1f;

        for (MoneyDetector.DetectionResult result : results) {
            float conf = result.getConfidence();
            avgConfidence += conf;
            maxConfidence = Math.max(maxConfidence, conf);
            minConfidence = Math.min(minConfidence, conf);
        }

        avgConfidence /= results.size();

        Log.d(TAG, String.format(
                "Stats -> Count: %d | Avg: %.3f | Max: %.3f | Min: %.3f",
                results.size(), avgConfidence, maxConfidence, minConfidence
        ));
    }

    /** Liberar recursos al destruir el presenter */
    public void onDestroy() {
        if (ttsManager != null) {
            ttsManager.shutdown();
        }

        if (executor != null && !executor.isShutdown()) {
            executor.shutdown();
        }

        if (detector != null) {
            detector.close();
        }
    }

    /** Detener TTS y limpiar resultado con un doble tap */
    public void onDoubleTap() {
        if (ttsManager.isSpeaking()) {
            ttsManager.stop();
            tvResult.setText("");
        }
    }

    /**
     * Getter para el ultimo resultado (útil para testing)
     */
    public String getLastResultText() {
        return lastResultText;
    }

    /**
     * Verifica si el presenter esta procesando actualmente
     */
    public boolean isProcessing() {
        return isProcessing;
    }
}