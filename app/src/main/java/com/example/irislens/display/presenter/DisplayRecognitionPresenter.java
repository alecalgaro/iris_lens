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
    private final DisplayDetector detector;
    private final TextToSpeechManager ttsManager;
    private final ExecutorService executor;
    private final Handler mainHandler;

    private volatile boolean isProcessing = false;
    private int frameCount = 0;

    private static final int NO_DETECTION_THRESHOLD = 8;
    private int noDetectionCount = 0;
    private String lastResultText = "";

    // Umbral para considerar un dígito como "confiable"
    private static final float HIGH_CONFIDENCE_THRESHOLD = 0.85f;

    // Para detectar la cantidad de dígitos esperados
    private int lastDetectedDigitCount = 0;
    private int consistentCountFrames = 0;
    private static final int FRAMES_TO_CONFIRM_COUNT = 3;

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
            resetDigitCountTracking();

            if (noDetectionCount >= NO_DETECTION_THRESHOLD) {
                ttsManager.speak("No se pudo detectar. Mejore la posición de la cámara o del display.");
                Log.d(TAG, "⚠️ No se pudo detectar tras " + NO_DETECTION_THRESHOLD + " frames");
                noDetectionCount = 0;
            }
        } else {
            noDetectionCount = 0;

            // Determinar cantidad esperada de dígitos
            int currentDigitCount = results.size();
            updateExpectedDigitCount(currentDigitCount);

            // Construir el número con detección de incertidumbre
            DisplayResult displayResult = buildNumberFromDetections(results, lastDetectedDigitCount);

            if (!displayResult.isEmpty()) {
                // Log de cada detección
                for (DisplayDetector.DetectionResult detection : results) {
                    String label = detection.getLabel();
                    float confidence = detection.getConfidence();
                    RectF box = detection.getBoundingBox();

                    Log.d(TAG, String.format(
                            "Detectado -> Dígito: %s | Score: %.2f | Box: [%.1f,%.1f,%.1f,%.1f]",
                            label, confidence, box.left, box.top, box.right, box.bottom
                    ));
                }

                // Mostrar resultado con colores
                setColoredText(displayResult);

                // Guardar último resultado
                lastResultText = displayResult.getPlainText();

                // Hablar el resultado (adaptado para manejar "?")
                String speechText = displayResult.getSpeechText();
                ttsManager.speak(speechText);

                Log.d(TAG, "✅ Número detectado: " + lastResultText +
                        " | Dígitos esperados: " + lastDetectedDigitCount);
            }
        }

        isProcessing = false;
    }

    private void updateExpectedDigitCount(int currentCount) {
        if (currentCount == lastDetectedDigitCount) {
            consistentCountFrames++;
        } else {
            consistentCountFrames = 1;
            if (consistentCountFrames >= FRAMES_TO_CONFIRM_COUNT) {
                lastDetectedDigitCount = currentCount;
                Log.d(TAG, "📊 Cantidad de dígitos confirmada: " + lastDetectedDigitCount);
            }
        }
    }

    private void resetDigitCountTracking() {
        consistentCountFrames = 0;
        // No reseteamos lastDetectedDigitCount para mantener la última referencia válida
    }

    private DisplayResult buildNumberFromDetections(List<DisplayDetector.DetectionResult> results,
                                                    int expectedDigitCount) {
        if (results.isEmpty()) return new DisplayResult();

        List<DigitWithPosition> digits = new ArrayList<>();

        for (DisplayDetector.DetectionResult detection : results) {
            String label = detection.getLabel().trim();
            float confidence = detection.getConfidence();
            RectF box = detection.getBoundingBox();
            float xPosition = box.left + (box.width() / 2);

            boolean isHighConfidence = confidence >= HIGH_CONFIDENCE_THRESHOLD;
            digits.add(new DigitWithPosition(label, xPosition, confidence, isHighConfidence));
        }

        // Ordenar por posición X (izquierda a derecha)
        Collections.sort(digits, new Comparator<DigitWithPosition>() {
            @Override
            public int compare(DigitWithPosition d1, DigitWithPosition d2) {
                return Float.compare(d1.xPosition, d2.xPosition);
            }
        });

        // Detectar huecos si sabemos cuántos dígitos esperar
        if (expectedDigitCount > 0 && digits.size() < expectedDigitCount) {
            digits = fillMissingDigits(digits, expectedDigitCount);
        }

        return new DisplayResult(digits);
    }

    private List<DigitWithPosition> fillMissingDigits(List<DigitWithPosition> digits,
                                                      int expectedCount) {
        if (digits.size() >= expectedCount) return digits;

        // Calcular distancia promedio entre dígitos detectados
        float avgDistance = 0;
        if (digits.size() > 1) {
            float totalDistance = 0;
            for (int i = 1; i < digits.size(); i++) {
                totalDistance += digits.get(i).xPosition - digits.get(i-1).xPosition;
            }
            avgDistance = totalDistance / (digits.size() - 1);
        }

        List<DigitWithPosition> filledDigits = new ArrayList<>();

        // Si no hay dígitos detectados, llenar todo con "?"
        if (digits.isEmpty()) {
            for (int i = 0; i < expectedCount; i++) {
                filledDigits.add(new DigitWithPosition("?", i * 100, 0.0f, false));
            }
            return filledDigits;
        }

        // Llenar huecos basándose en la posición esperada
        int digitIndex = 0;
        for (int expectedIndex = 0; expectedIndex < expectedCount; expectedIndex++) {
            if (digitIndex < digits.size()) {
                // Verificar si hay un hueco
                float expectedX = digits.get(0).xPosition + (expectedIndex * avgDistance);
                float actualX = digits.get(digitIndex).xPosition;

                // Si la posición actual está cerca de la esperada, usar el dígito detectado
                if (Math.abs(actualX - expectedX) < avgDistance * 0.6f || avgDistance == 0) {
                    filledDigits.add(digits.get(digitIndex));
                    digitIndex++;
                } else {
                    // Hueco detectado, insertar "?"
                    filledDigits.add(new DigitWithPosition("?", expectedX, 0.0f, false));
                }
            } else {
                // No hay más dígitos detectados, llenar con "?"
                float expectedX = filledDigits.isEmpty() ? 0 :
                        filledDigits.get(filledDigits.size()-1).xPosition + avgDistance;
                filledDigits.add(new DigitWithPosition("?", expectedX, 0.0f, false));
            }
        }

        return filledDigits;
    }

    private void setColoredText(DisplayResult result) {
        String fullText = result.getDisplayText();
        SpannableString spannable = new SpannableString(fullText);

        int position = 0;
        for (DigitWithPosition digit : result.digits) {
            int color;
            if (digit.digit.equals("?")) {
                color = Color.RED; // Rojo para desconocido
            } else if (digit.isHighConfidence) {
                color = Color.GREEN; // Verde para alta confianza
            } else {
                color = Color.rgb(255, 165, 0); // Naranja para baja confianza
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

    private static class DigitWithPosition {
        String digit;
        float xPosition;
        float confidence;
        boolean isHighConfidence;

        DigitWithPosition(String digit, float xPosition, float confidence, boolean isHighConfidence) {
            this.digit = digit;
            this.xPosition = xPosition;
            this.confidence = confidence;
            this.isHighConfidence = isHighConfidence;
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
            int unknownCount = 0;

            for (DigitWithPosition digit : digits) {
                if (digit.digit.equals("?")) {
                    unknownCount++;
                } else {
                    if (unknownCount > 0) {
                        sb.append(unknownCount == 1 ? "dígito desconocido, " :
                                unknownCount + " dígitos desconocidos, ");
                        unknownCount = 0;
                    }
                    sb.append(digit.digit).append(", ");
                }
            }

            if (unknownCount > 0) {
                sb.append(unknownCount == 1 ? "dígito desconocido" :
                        unknownCount + " dígitos desconocidos");
            }

            String result = sb.toString();
            // Limpiar comas finales
            if (result.endsWith(", ")) {
                result = result.substring(0, result.length() - 2);
            }

            return result.isEmpty() ? "Sin detección" : result;
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