// ════════════════════════════════════════════════════════════════
// 2. MoneyRecognitionPresenter.java - VERSIÓN FINAL
// ════════════════════════════════════════════════════════════════
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
import com.example.irislens.common.AppVoiceManager;
import com.example.irislens.common.ImageProcessor;
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
    private final AppVoiceManager voiceManager;
    private final ExecutorService executor;
    private final Handler mainHandler;

    private volatile boolean isProcessing = false;
    private volatile boolean isAnnouncing = false;
    private int frameCount = 0;

    private static final int NO_DETECTION_THRESHOLD = 8;
    private int noDetectionCount = 0;
    private String lastResultText = "";

    public MoneyRecognitionPresenter(Activity activity, TextView tvResult) throws IOException {
        this.activity = activity;
        this.tvResult = tvResult;
        this.voiceManager = AppVoiceManager.getInstance(activity);
        this.executor = Executors.newSingleThreadExecutor();
        this.mainHandler = new Handler(Looper.getMainLooper());
        this.detector = new MoneyDetector(activity.getApplicationContext());
    }

    public void processCameraFrame(Mat image) {
        if (isProcessing || isAnnouncing) {
            return;
        }

        frameCount++;
        if (frameCount == 5) {
            frameCount = 0;

            MediaPlayer mediaPlayer = MediaPlayer.create(activity, R.raw.captura);
            if (mediaPlayer != null) {
                mediaPlayer.start();
                mediaPlayer.setOnCompletionListener(MediaPlayer::release);
            }

            Bitmap bitmap = ImageProcessor.convertToBitmap(image);
            isProcessing = true;

            detector.detect(bitmap, new MoneyDetector.DetectionCallback() {
                @Override
                public void onDetectionComplete(@NonNull List<MoneyDetector.DetectionResult> results) {
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
    }

    private void handleDetection(List<MoneyDetector.DetectionResult> results) {
        if (results.isEmpty()) {
            noDetectionCount++;

            if (noDetectionCount >= NO_DETECTION_THRESHOLD) {
                String msg = "No se pudo detectar. Mejore la posición de la cámara o del objeto.";

                // ✅ Mostrar y hablar
                tvResult.setText(msg);
                isAnnouncing = true;
                voiceManager.speak(msg);

                // ✅ Limpiar automáticamente
                int duration = calculateSpeechDuration(msg);
                mainHandler.postDelayed(() -> {
                    tvResult.setText("");
                    isAnnouncing = false;
                    Log.d(TAG, "🧹 Texto limpiado automáticamente");
                }, duration);

                Log.d(TAG, "⚠️ No se pudo detectar tras " + NO_DETECTION_THRESHOLD + " frames");
                noDetectionCount = 0;
            }
        } else {
            noDetectionCount = 0;

            Map<String, Integer> countByLabel = new HashMap<>();
            Map<String, Float> maxConfidenceByLabel = new HashMap<>();

            for (MoneyDetector.DetectionResult detection : results) {
                String label = detection.getLabel();
                float confidence = detection.getConfidence();
                RectF box = detection.getBoundingBox();

                Log.d(TAG, String.format(
                        "Detectado -> Label: %s | Score: %.2f | Box: [%.1f,%.1f,%.1f,%.1f]",
                        label, confidence, box.left, box.top, box.right, box.bottom
                ));

                Integer currentCount = countByLabel.get(label);
                countByLabel.put(label, (currentCount == null) ? 1 : currentCount + 1);

                Float currentMaxConf = maxConfidenceByLabel.get(label);
                if (currentMaxConf == null || confidence > currentMaxConf) {
                    maxConfidenceByLabel.put(label, confidence);
                }
            }

            StringBuilder displayText = new StringBuilder();
            StringBuilder ttsText = new StringBuilder();

            for (Map.Entry<String, Integer> entry : countByLabel.entrySet()) {
                String fullLabel = entry.getKey();
                int count = entry.getValue();
                float maxConf = maxConfidenceByLabel.get(fullLabel);

                String processedLabel = processLabel(fullLabel);

                displayText.append(String.format("%d de %s (%.1f%%)\n",
                        count, processedLabel, maxConf * 100));

                String[] parts = fullLabel.split("_");
                String simpleLabel = parts.length > 0 ? parts[0] : fullLabel;
                ttsText.append(String.format("%d de %s ", count, simpleLabel));
            }

            // ✅ Actualizar UI
            lastResultText = displayText.toString().trim();
            tvResult.setText(lastResultText);

            // ✅ Hablar
            String speechText = ttsText.toString().trim();
            if (!speechText.isEmpty()) {
                isAnnouncing = true;
                voiceManager.speak(speechText);

                // ✅ Limpiar automáticamente
                int duration = calculateSpeechDuration(speechText);
                mainHandler.postDelayed(() -> {
                    tvResult.setText("");
                    isAnnouncing = false;
                    Log.d(TAG, "🧹 Texto limpiado automáticamente");
                }, duration);
            }
        }

        isProcessing = false;
    }

    /**
     * ✅ Calcula duración estimada del habla
     */
    private int calculateSpeechDuration(String text) {
        int wordCount = text.split("\\s+").length;
        int baseDuration = (int) ((wordCount / 2.5) * 1000);
        return baseDuration + 1000;
    }

    private String processLabel(String rawLabel) {
        if (rawLabel == null || rawLabel.isEmpty()) {
            return "billete desconocido";
        }

        String processed = rawLabel.replace("_", " ");
        processed = processed.replace(" f ", " frente ")
                .replace(" d ", " dorso ")
                .replace("heroes", "héroes")
                .replace("animales", "de animales");

        return processed;
    }

    public void onDestroy() {
        mainHandler.removeCallbacksAndMessages(null);
        if (executor != null && !executor.isShutdown()) executor.shutdown();
        if (detector != null) detector.close();
    }

    public void onDoubleTap() {
        Log.d(TAG, "👆 Doble tap detectado");
        voiceManager.stop();
        tvResult.setText("");
        isAnnouncing = false;
        mainHandler.removeCallbacksAndMessages(null);
        Log.d(TAG, "✅ Voz detenida y pantalla limpia");
    }

    public String getLastResultText() {
        return lastResultText;
    }

    public boolean isProcessing() {
        return isProcessing;
    }
}