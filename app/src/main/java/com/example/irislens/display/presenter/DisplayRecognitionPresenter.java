package com.example.irislens.display.presenter;

import android.app.Activity;
import android.graphics.Bitmap;
import android.graphics.Color;
import android.graphics.RectF;
import android.media.MediaPlayer;
import android.os.Handler;
import android.os.Looper;
import android.text.SpannableString;
import android.text.Spanned;
import android.text.style.ForegroundColorSpan;
import android.util.Log;
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
    private final TextView tvDebug; // ✅ Nuevo: para mostrar detecciones raw
    private final DisplayDetector detector;
    private final TextToSpeechManager ttsManager;
    private final ExecutorService executor;
    private final Handler mainHandler;

    private volatile boolean isProcessing = false;
    private int frameCount = 0;

    private static final int NO_DETECTION_THRESHOLD = 8;
    private int noDetectionCount = 0;
    private String lastResultText = "";

    // ⚠️ CAMBIO CRÍTICO: Umbrales más permisivos
    private static final float MEDIUM_CONFIDENCE_THRESHOLD = 0.55f; // Verde
    private static final float LOW_CONFIDENCE_THRESHOLD = 0.45f;   // Naranja
    // Debajo de LOW = Rojo

    // Sistema de confirmación deshabilitado por defecto
    private boolean useDigitCountConfirmation = false;
    private int lastDetectedDigitCount = 0;
    private int consistentCountFrames = 0;
    private static final int FRAMES_TO_CONFIRM_COUNT = 3;

    public DisplayRecognitionPresenter(Activity activity, TextView tvResult, TextView tvDebug) throws IOException {
        this.activity = activity;
        this.tvResult = tvResult;
        this.tvDebug = tvDebug;
        this.ttsManager = new TextToSpeechManager(activity);
        this.executor = Executors.newSingleThreadExecutor();
        this.mainHandler = new Handler(Looper.getMainLooper());
        this.detector = new DisplayDetector(activity.getApplicationContext());
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
                    public void onDetectionComplete(@NonNull List<DisplayDetector.DetectionResult> results,
                                                    @NonNull List<DisplayDetector.DetectionResult> rawResults) {
                        mainHandler.post(() -> handleDetection(results, rawResults));
                    }

                    @Override
                    public void onDetectionError(@NonNull Exception error) {
                        Log.e(TAG, "Error en detección TensorFlow Lite", error);
                        mainHandler.post(() -> {
                            isProcessing = false;
                            tvResult.setText("Error: " + error.getMessage());
                        });
                    }
                });
            }
            frameCount = 0;
        }
    }

    private void handleDetection(List<DisplayDetector.DetectionResult> results,
                                 List<DisplayDetector.DetectionResult> rawResults) {

        // ✅ NUEVO: Mostrar detecciones RAW en TextView de debug
        showRawDetections(rawResults);

        if (results.isEmpty()) {
            noDetectionCount++;
            tvResult.setText("");

            if (noDetectionCount >= NO_DETECTION_THRESHOLD) {
                ttsManager.speak("No se detectaron dígitos. Acerque más la cámara al display.");
                Log.d(TAG, "⚠️ Sin detecciones tras " + NO_DETECTION_THRESHOLD + " frames");
                noDetectionCount = 0;
            }
        } else {
            noDetectionCount = 0;

            // Construir número desde las detecciones
            DisplayResult displayResult = buildNumberFromDetections(results);

            if (!displayResult.isEmpty()) {
                // Log detallado de cada detección
                StringBuilder logBuilder = new StringBuilder("🔍 DETECCIONES:\n");
                for (DisplayDetector.DetectionResult det : results) {
                    logBuilder.append(String.format("  %s | %.3f | [%.0f, %.0f]\n",
                            det.getLabel(), det.getConfidence(),
                            det.getBoundingBox().left, det.getBoundingBox().top));
                }
                Log.d(TAG, logBuilder.toString());

                // Mostrar resultado con colores
                setColoredText(displayResult);

                // Guardar último resultado
                lastResultText = displayResult.getPlainText();

                // Hablar resultado
                String speechText = displayResult.getSpeechText();
                ttsManager.speak(speechText);

                Log.d(TAG, "✅ Detectado: " + lastResultText +
                        " | Dígitos: " + results.size());
            }
        }

        isProcessing = false;
    }

    /** ✅ NUEVO: Mostrar detecciones sin filtrar */
    private void showRawDetections(List<DisplayDetector.DetectionResult> rawResults) {
        if (tvDebug == null) return;

        StringBuilder sb = new StringBuilder();
        sb.append("🔬 RAW (").append(rawResults.size()).append("):\n");

        // Ordenar por posición X
        List<DisplayDetector.DetectionResult> sorted = new ArrayList<>(rawResults);
        Collections.sort(sorted, (a, b) ->
                Float.compare(a.getBoundingBox().left, b.getBoundingBox().left));

        for (DisplayDetector.DetectionResult det : sorted) {
            sb.append(String.format("%s:%.2f ",
                    det.getLabel(), det.getConfidence()));
        }

        tvDebug.setText(sb.toString());
    }

    private DisplayResult buildNumberFromDetections(List<DisplayDetector.DetectionResult> results) {
        if (results.isEmpty()) return new DisplayResult();

        List<DigitWithPosition> digits = new ArrayList<>();

        for (DisplayDetector.DetectionResult detection : results) {
            String label = detection.getLabel().trim();
            float confidence = detection.getConfidence();
            RectF box = detection.getBoundingBox();
            float xPosition = box.left + (box.width() / 2);

            // ✅ Sistema de confianza de 3 niveles
            ConfidenceLevel level;
            if (confidence >= MEDIUM_CONFIDENCE_THRESHOLD) {
                level = ConfidenceLevel.HIGH;
            } else if (confidence >= LOW_CONFIDENCE_THRESHOLD) {
                level = ConfidenceLevel.MEDIUM;
            } else {
                level = ConfidenceLevel.LOW;
            }

            digits.add(new DigitWithPosition(label, xPosition, confidence, level));
        }

        // Ordenar por posición X (izquierda a derecha)
        Collections.sort(digits, new Comparator<DigitWithPosition>() {
            @Override
            public int compare(DigitWithPosition d1, DigitWithPosition d2) {
                return Float.compare(d1.xPosition, d2.xPosition);
            }
        });

        return new DisplayResult(digits);
    }

    private void setColoredText(DisplayResult result) {
        String fullText = result.getDisplayText();
        SpannableString spannable = new SpannableString(fullText);

        int position = 0;
        for (DigitWithPosition digit : result.digits) {
            int color;
            switch (digit.confidenceLevel) {
                case HIGH:
                    color = Color.GREEN;
                    break;
                case MEDIUM:
                    color = Color.rgb(255, 165, 0); // Naranja
                    break;
                case LOW:
                    color = Color.rgb(255, 100, 100); // Rojo claro
                    break;
                default:
                    color = Color.RED;
            }

            spannable.setSpan(
                    new ForegroundColorSpan(color),
                    position,
                    position + digit.digit.length(),
                    Spanned.SPAN_EXCLUSIVE_EXCLUSIVE
            );
            position += digit.digit.length();
        }

        tvResult.setText(spannable);
    }

    private enum ConfidenceLevel {
        HIGH,    // >= 0.55 (Verde)
        MEDIUM,  // >= 0.45 (Naranja)
        LOW      // < 0.45 (Rojo)
    }

    private static class DigitWithPosition {
        String digit;
        float xPosition;
        float confidence;
        ConfidenceLevel confidenceLevel;

        DigitWithPosition(String digit, float xPosition, float confidence, ConfidenceLevel level) {
            this.digit = digit;
            this.xPosition = xPosition;
            this.confidence = confidence;
            this.confidenceLevel = level;
        }
    }

    private static class DisplayResult {
        List<DigitWithPosition> digits;

        DisplayResult() {
            this.digits = new ArrayList<>();
        }

        DisplayResult(List<DigitWithPosition> digits) {
            this.digits = digits;
        }

        boolean isEmpty() {
            return digits.isEmpty();
        }

        String getDisplayText() {
            StringBuilder sb = new StringBuilder();
            for (DigitWithPosition digit : digits) {
                sb.append(digit.digit);
            }
            return sb.toString();
        }

        String getPlainText() {
            return getDisplayText();
        }

        String getSpeechText() {
            StringBuilder sb = new StringBuilder();
            for (int i = 0; i < digits.size(); i++) {
                sb.append(digits.get(i).digit);
                if (i < digits.size() - 1) {
                    sb.append(", ");
                }
            }
            return sb.toString();
        }
    }

    public void onDestroy() {
        if (ttsManager != null) ttsManager.shutdown();
        if (executor != null && !executor.isShutdown()) executor.shutdown();
        if (detector != null) detector.close();
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