package com.example.irislens.display.presenter;

import android.app.Activity;
import android.graphics.Bitmap;
import android.graphics.RectF;
import android.media.MediaPlayer;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;

import com.example.irislens.R;
import com.example.irislens.common.ImageProcessor;
import com.example.irislens.common.TextToSpeechManager;
import com.example.irislens.display.model.DisplayDetector;

import org.opencv.core.Mat;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class DisplayRecognitionPresenter {
    private static final String TAG = "DisplayPresenter";

    private final Activity activity;
    private final TextView tvResult;
    private final ImageView ivDebugPreview; // NUEVO: Vista para debug
    private final DisplayDetector detector;
    private final TextToSpeechManager ttsManager;
    private final ExecutorService executor;
    private final Handler mainHandler;

    private volatile boolean isProcessing = false;
    private int frameCount = 0;

    private static final int NO_DETECTION_THRESHOLD = 8;
    private int noDetectionCount = 0;
    private String lastResultText = "";

    // DEBUG: Control de previsualización
    private boolean debugPreviewShown = false;
    private static final long DEBUG_PREVIEW_DURATION = 2000; // 2 segundos

    public DisplayRecognitionPresenter(Activity activity, TextView tvResult, ImageView ivDebugPreview) throws IOException {
        this.activity = activity;
        this.tvResult = tvResult;
        this.ivDebugPreview = ivDebugPreview;
        this.ttsManager = new TextToSpeechManager(activity);
        this.executor = Executors.newSingleThreadExecutor();
        this.mainHandler = new Handler(Looper.getMainLooper());

        this.detector = new DisplayDetector(activity.getApplicationContext());

        // Configurar callback de debug para ver imagen preprocesada
        detector.setDebugCallback(preprocessed -> {
            mainHandler.post(() -> showDebugPreview(preprocessed));
        });
    }

    /** Muestra la imagen preprocesada durante 2 segundos */
    private void showDebugPreview(Bitmap preprocessed) {
        if (debugPreviewShown) return;
        debugPreviewShown = true;

        // Mostrar la imagen preprocesada
        ivDebugPreview.setImageBitmap(preprocessed);
        ivDebugPreview.setAlpha(1.0f);

        Log.d(TAG, "🔍 Mostrando preview de imagen preprocesada por 2 segundos");

        // Ocultar después de 2 segundos
        mainHandler.postDelayed(() -> {
            ivDebugPreview.animate()
                    .alpha(0f)
                    .setDuration(500)
                    .withEndAction(() -> debugPreviewShown = false)
                    .start();
        }, DEBUG_PREVIEW_DURATION);
    }

    public void processCameraFrame(Mat image) {
        if (isProcessing) return;
        frameCount++;
        if (frameCount == 5) {
            if (!ttsManager.isSpeaking()) {
                MediaPlayer mediaPlayer = MediaPlayer.create(activity, R.raw.captura);
                if (mediaPlayer != null) {
                    mediaPlayer.start();
                    mediaPlayer.setOnCompletionListener(MediaPlayer::release);
                }

                Bitmap bitmap = ImageProcessor.convertToBitmap(image);
                isProcessing = true;

                detector.detect(bitmap, new DisplayDetector.DetectionCallback() {
                    @Override
                    public void onDetectionComplete(@NonNull List<DisplayDetector.DetectionResult> results) {
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

    private void handleDetection(List<DisplayDetector.DetectionResult> results) {
        if (results.isEmpty()) {
            noDetectionCount++;
            tvResult.setText("");

            if (noDetectionCount >= NO_DETECTION_THRESHOLD) {
                ttsManager.speak("No se pudo detectar. Mejore la posición de la cámara o del objeto.");
                Log.d(TAG, "⚠️ No se pudo detectar tras " + NO_DETECTION_THRESHOLD + " frames");
                noDetectionCount = 0;
            }
        } else {
            noDetectionCount = 0;

            List<DisplayDetector.DetectionResult> sortedDigits = new ArrayList<>(results);
            Collections.sort(sortedDigits, new Comparator<DisplayDetector.DetectionResult>() {
                @Override
                public int compare(DisplayDetector.DetectionResult o1, DisplayDetector.DetectionResult o2) {
                    RectF box1 = o1.getBoundingBox();
                    RectF box2 = o2.getBoundingBox();
                    float centerX1 = (box1.left + box1.right) / 2f;
                    float centerX2 = (box2.left + box2.right) / 2f;
                    return Float.compare(centerX1, centerX2);
                }
            });

            StringBuilder displayNumber = new StringBuilder();
            StringBuilder ttsNumber = new StringBuilder();
            float totalConfidence = 0f;

            for (DisplayDetector.DetectionResult detection : sortedDigits) {
                String label = detection.getLabel();
                float confidence = detection.getConfidence();
                RectF box = detection.getBoundingBox();

                Log.d(TAG, String.format(
                        "Detectado -> Label: %s | Score: %.2f | Box: [%.1f,%.1f,%.1f,%.1f]",
                        label, confidence, box.left, box.top, box.right, box.bottom
                ));

                String digit = extractDigit(label);
                displayNumber.append(digit);
                ttsNumber.append(digit).append(" ");
                totalConfidence += confidence;
            }

            float avgConfidence = totalConfidence / sortedDigits.size();
            String finalNumber = displayNumber.toString();
            lastResultText = String.format("%s (%.1f%%)", finalNumber, avgConfidence * 100);
            tvResult.setText(lastResultText);

            String speechText = ttsNumber.toString().trim();
            if (!speechText.isEmpty()) {
                ttsManager.speak(speechText);
            }

            Log.d(TAG, "Número detectado: " + finalNumber + " | Confianza promedio: " + avgConfidence);
        }

        isProcessing = false;
    }

    private String extractDigit(String label) {
        if (label == null || label.isEmpty()) {
            return "?";
        }

        if (label.matches("\\d")) {
            return label;
        }

        String[] parts = label.split("_");
        for (int i = parts.length - 1; i >= 0; i--) {
            if (parts[i].matches("\\d")) {
                return parts[i];
            }
        }

        for (char c : label.toCharArray()) {
            if (Character.isDigit(c)) {
                return String.valueOf(c);
            }
        }

        return "?";
    }

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

    public void onDoubleTap() {
        if (ttsManager.isSpeaking()) {
            ttsManager.stop();
            tvResult.setText("");
        }
    }

    public String getLastResultText() {
        return lastResultText;
    }

    public boolean isProcessing() {
        return isProcessing;
    }
}