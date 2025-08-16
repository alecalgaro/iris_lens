package com.example.irislens.money.presenter;

import android.app.Activity;
import android.graphics.Bitmap;
import android.os.Handler;
import android.os.Looper;
import android.widget.TextView;

import com.example.irislens.money.model.MoneyDetector;
import com.example.irislens.common.ImageProcessor;
import com.example.irislens.common.TextToSpeechManager;

import org.opencv.core.Mat;
import org.tensorflow.lite.task.vision.detector.Detection;
import android.graphics.RectF;

import java.io.IOException;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class MoneyRecognitionPresenter {
    private final Activity activity;
    private final TextView tvResult;
    private final MoneyDetector detector;
    private final TextToSpeechManager ttsManager;
    private final ExecutorService executor;
    private volatile boolean isProcessing = false;

    public MoneyRecognitionPresenter(Activity activity, TextView tvResult) throws IOException {
        this.activity = activity;
        this.tvResult = tvResult;
        this.ttsManager = new TextToSpeechManager(activity);
        this.executor = Executors.newSingleThreadExecutor();
        this.detector = new MoneyDetector(activity.getApplicationContext());
    }

    public void onFrame(Mat image) {
        if (isProcessing) return;

        Bitmap bitmap = ImageProcessor.convertToBitmap(image);
        isProcessing = true;
        Handler handler = new Handler(Looper.getMainLooper());

        executor.execute(() -> {
            List<Detection> results = detector.detect(bitmap);
            handler.post(() -> handleDetection(results));
        });
    }

    private void handleDetection(List<Detection> results) {
        if (results.isEmpty()) {
            tvResult.setText("No se detectaron billetes");
            ttsManager.speak("No se detectaron billetes");
        } else {
            StringBuilder sb = new StringBuilder("Billetes detectados:\n");
            for (Detection detection : results) {
                if (!detection.getCategories().isEmpty()) {
                    String label = detection.getCategories().get(0).getLabel();
                    float score = detection.getCategories().get(0).getScore();
                    RectF box = detection.getBoundingBox();
                    sb.append(String.format("- %s (%.2f%%)\n", label, score * 100));
                } else {
                    sb.append("- Categoría no detectada\n");
                }
            }

            String resultText = sb.toString();
            tvResult.setText(resultText);
            ttsManager.speak("Se detectaron billetes");
        }

        isProcessing = false;
    }


    public void onDestroy() {
        ttsManager.shutdown();
        executor.shutdown();
    }

    public void onDoubleTap() {
        if (ttsManager.isSpeaking()) {
            ttsManager.stop();
            tvResult.setText("");
        }
    }
}
