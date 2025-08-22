package com.example.irislens.money.presenter;

import android.app.Activity;
import android.graphics.Bitmap;
import android.media.MediaPlayer;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.widget.TextView;

import com.example.irislens.R;
import com.example.irislens.money.model.MoneyDetector;
import com.example.irislens.common.ImageProcessor;
import com.example.irislens.common.TextToSpeechManager;

import org.opencv.core.Mat;
import org.tensorflow.lite.support.label.Category;
import org.tensorflow.lite.task.vision.detector.Detection;
import android.graphics.RectF;

import java.io.IOException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class MoneyRecognitionPresenter {
    private final Activity activity;
    private final TextView tvResult;
    private final MoneyDetector detector;
    private final TextToSpeechManager ttsManager;
    private final ExecutorService executor;
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
                // Reproducir un sonido para indicar al usuario que se ha capturado un frame
                MediaPlayer mediaPlayer = MediaPlayer.create(activity, R.raw.captura);
                mediaPlayer.start();
                // Convertir Mat a Bitmap
                Bitmap bitmap = ImageProcessor.convertToBitmap(image);
                isProcessing = true;
                // Procesar frame en un hilo separado
                Handler handler = new Handler(Looper.getMainLooper());
                executor.execute(() -> {
                    List<Detection> results = detector.detect(bitmap);
                    handler.post(() -> handleDetection(results));
                });
            }
            frameCount = 0;
        }
    }

    private void handleDetection(List<Detection> results) {
        Log.d("Presenter", "Resultados recibidos: " + results.size());

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
            for (Detection detection : results) {
                if (!detection.getCategories().isEmpty()) {
                    Category category = detection.getCategories().get(0);
                    int index = category.getIndex();
                    String label = (index >= 0 && index < detector.getLabels().size())
                            ? detector.getLabels().get(index)
                            : "ID " + index;
                    float score = category.getScore();
                    RectF box = detection.getBoundingBox();

                    // Log detallado
                    Log.d("MoneyPresenter", String.format(
                            "📌 Detectado -> Label: %s | Index: %d | Score: %.2f | Box: %s",
                            label, index, score, box.toString()
                    ));

                    // Agrupar por etiqueta
                    Integer current = countByLabel.get(label);
                    if (current == null) {
                        countByLabel.put(label, 1);
                    } else {
                        countByLabel.put(label, current + 1);
                    }
                }
            }

            // Construir texto simplificado para pantalla
            StringBuilder sb = new StringBuilder();
            StringBuilder sbTTS = new StringBuilder(); // texto separado para TTS

            for (Map.Entry<String, Integer> entry : countByLabel.entrySet()) {
                String fullLabel = entry.getKey();
                String[] parts = fullLabel.split("_"); // separar por "_"
                String baseLabel = parts[0];           // solo la primera parte (ej: "10")

                // Texto completo en pantalla
                sb.append(entry.getValue())
                        .append(" de ")
                        .append(fullLabel)
                        .append("\n");

                // Texto reducido para TTS
                sbTTS.append(entry.getValue())
                        .append(" de ")
                        .append(baseLabel)
                        .append(" ");
            }

            lastResultText = sb.toString().trim();
            tvResult.setText(lastResultText);

            // Mensaje de voz reducido (ej: "2 de 10, 3 de 50")
            ttsManager.speak(sbTTS.toString().trim());
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