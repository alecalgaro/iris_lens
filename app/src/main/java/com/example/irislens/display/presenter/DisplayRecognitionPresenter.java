package com.example.irislens.display.presenter;

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
    private final DisplayDetector detector;
    private final TextToSpeechManager ttsManager;
    private final ExecutorService executor;
    private final Handler mainHandler;

    private volatile boolean isProcessing = false;
    private int frameCount = 0;

    private static final int NO_DETECTION_THRESHOLD = 8;
    private int noDetectionCount = 0;
    private String lastResultText = "";

    public DisplayRecognitionPresenter(Activity activity, TextView tvResult) throws IOException {
        this.activity = activity;
        this.tvResult = tvResult;
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
                ttsManager.speak("No se pudo detectar. Mejore la posición de la cámara o del display.");
                Log.d(TAG, "⚠️ No se pudo detectar tras " + NO_DETECTION_THRESHOLD + " frames");
                noDetectionCount = 0;
            }
        } else {
            noDetectionCount = 0;

            String numberText = buildNumberFromDetections(results);

            if (!numberText.isEmpty()) {
                for (DisplayDetector.DetectionResult detection : results) {
                    String label = detection.getLabel();
                    float confidence = detection.getConfidence();
                    RectF box = detection.getBoundingBox();

                    Log.d(TAG, String.format(
                            "Detectado -> Dígito: %s | Score: %.2f | Box: [%.1f,%.1f,%.1f,%.1f]",
                            label, confidence, box.left, box.top, box.right, box.bottom
                    ));
                }

                lastResultText = numberText;
                tvResult.setText(numberText);
                ttsManager.speak(numberText);
                Log.d(TAG, "✅ Número detectado: " + numberText);
            }
        }

        isProcessing = false;
    }

    private String buildNumberFromDetections(List<DisplayDetector.DetectionResult> results) {
        if (results.isEmpty()) return "";

        List<DigitWithPosition> digits = new ArrayList<>();

        for (DisplayDetector.DetectionResult detection : results) {
            String label = detection.getLabel().trim();
            RectF box = detection.getBoundingBox();
            float xPosition = box.left + (box.width() / 2);
            digits.add(new DigitWithPosition(label, xPosition));
        }

        Collections.sort(digits, new Comparator<DigitWithPosition>() {
            @Override
            public int compare(DigitWithPosition d1, DigitWithPosition d2) {
                return Float.compare(d1.xPosition, d2.xPosition);
            }
        });

        StringBuilder numberBuilder = new StringBuilder();
        for (DigitWithPosition digit : digits) {
            numberBuilder.append(digit.digit);
        }

        return numberBuilder.toString();
    }

    private static class DigitWithPosition {
        String digit;
        float xPosition;

        DigitWithPosition(String digit, float xPosition) {
            this.digit = digit;
            this.xPosition = xPosition;
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