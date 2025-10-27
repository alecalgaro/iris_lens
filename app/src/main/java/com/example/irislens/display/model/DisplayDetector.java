package com.example.irislens.display.model;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.RectF;
import android.util.Log;

import androidx.annotation.NonNull;

import org.tensorflow.lite.DataType;
import org.tensorflow.lite.Interpreter;
import org.tensorflow.lite.support.common.FileUtil;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.MappedByteBuffer;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * DisplayDetector: Detector de dígitos en displays usando TensorFlow Lite.
 * Modelo basado en YOLOv8n con preprocesamiento ADAPTATIVO SECUENCIAL:
 *
 * ESTRATEGIA AUTOMÁTICA:
 * 1. Intenta primero con IMAGEN ORIGINAL EN COLOR - sin preprocesamiento
 * 2. Si no detecta nada, reintenta con INVERSIÓN - para LEDs brillantes
 *
 * Esto asegura detección robusta sin necesidad de análisis previo complejo.
 */
public class DisplayDetector {
    private static final String TAG = "DisplayDetector";
    private static final String MODEL_PATH = "detectorDisplay.tflite";
    private static final String LABELS_PATH = "labelsDisplay.txt";

    private static final float CONF_THRESHOLD = 0.55f;
    private static final float NMS_IOU_THRESHOLD = 0.5f;
    private static final int INPUT_SIZE = 640;

    // DEBUG: Activar para ver imagen preprocesada
    public static final boolean DEBUG_SHOW_PREPROCESSED = true;

    private final Interpreter interpreter;
    private final List<String> labels;
    private final int numClasses;
    private final ExecutorService executor = Executors.newSingleThreadExecutor();

    private PreprocessDebugCallback debugCallback;

    public interface DetectionCallback {
        void onDetectionComplete(@NonNull List<DetectionResult> results);
        void onDetectionError(@NonNull Exception error);
    }

    public interface PreprocessDebugCallback {
        void onPreprocessedImage(@NonNull Bitmap preprocessed);
    }

    public DisplayDetector(@NonNull Context context) throws IOException {
        Log.d(TAG, "Inicializando DisplayDetector...");
        MappedByteBuffer model = FileUtil.loadMappedFile(context, MODEL_PATH);
        Interpreter.Options options = new Interpreter.Options();
        int threads = Math.min(2, Runtime.getRuntime().availableProcessors());
        options.setNumThreads(threads);
        options.setUseNNAPI(false);

        interpreter = new Interpreter(model, options);
        Log.d(TAG, "✅ Modelo cargado: " + MODEL_PATH + " | threads=" + threads);

        labels = FileUtil.loadLabels(context, LABELS_PATH);
        numClasses = labels.size();
        Log.d(TAG, "✅ Labels cargadas (" + numClasses + "): " + labels);
    }

    public void setDebugCallback(PreprocessDebugCallback callback) {
        this.debugCallback = callback;
    }

    public void detect(@NonNull Bitmap bitmap, @NonNull DetectionCallback callback) {
        executor.execute(() -> {
            try {
                List<DetectionResult> out = detectInternal(bitmap);
                callback.onDetectionComplete(out);
            } catch (Exception e) {
                Log.e(TAG, "Error en detección", e);
                callback.onDetectionError(e);
            }
        });
    }

    private List<DetectionResult> detectInternal(@NonNull Bitmap bitmap) {
        long t0 = System.currentTimeMillis();

        // ====================================================================
        // ESTRATEGIA ADAPTATIVA SECUENCIAL
        // ====================================================================

        Log.d(TAG, "🔍 Estrategia 1: IMAGEN ORIGINAL (sin preprocesamiento)");

        // INTENTO 1: Imagen original tal como llega, en color
        long tPreprocess1 = System.currentTimeMillis();
        Log.d(TAG, "⏱️ Preprocesamiento estrategia 1: " + (tPreprocess1 - t0) + " ms (sin preprocesamiento)");

        // DEBUG: Mostrar imagen original
        if (DEBUG_SHOW_PREPROCESSED && debugCallback != null) {
            debugCallback.onPreprocessedImage(bitmap);
        }

        // Ejecutar inferencia con imagen original
        List<DetectionResult> results1 = runInference(bitmap, bitmap.getWidth(), bitmap.getHeight());
        long tInference1 = System.currentTimeMillis();
        Log.d(TAG, "⏱️ Inferencia estrategia 1: " + (tInference1 - tPreprocess1) + " ms");
        Log.d(TAG, "📊 Detecciones estrategia 1: " + results1.size());

        // Si encontramos detecciones, retornar inmediatamente
        if (!results1.isEmpty()) {
            long dt = System.currentTimeMillis() - t0;
            Log.d(TAG, "✅ ÉXITO con estrategia 1 (imagen original) | Total: " + dt + " ms | detecciones=" + results1.size());
            return results1;
        }

        // ====================================================================
        // Si estrategia 1 falló (0 detecciones), intentar estrategia 2
        // ====================================================================

        Log.d(TAG, "⚠️ Estrategia 1 sin resultados, intentando estrategia 2...");
        Log.d(TAG, "🔍 Estrategia 2: INVERSIÓN (LED/segmentos brillantes)");

        // INTENTO 2: Inversión de imagen (para LEDs brillantes sobre fondo oscuro)
        Bitmap preprocessed2 = invertImagePreprocessing(bitmap);
        long tPreprocess2 = System.currentTimeMillis();
        Log.d(TAG, "⏱️ Preprocesamiento estrategia 2: " + (tPreprocess2 - tInference1) + " ms");

        // DEBUG: Mostrar segunda estrategia
        if (DEBUG_SHOW_PREPROCESSED && debugCallback != null) {
            debugCallback.onPreprocessedImage(preprocessed2);
        }

        // Ejecutar inferencia con estrategia 2
        List<DetectionResult> results2 = runInference(preprocessed2, bitmap.getWidth(), bitmap.getHeight());
        long tInference2 = System.currentTimeMillis();
        Log.d(TAG, "⏱️ Inferencia estrategia 2: " + (tInference2 - tPreprocess2) + " ms");
        Log.d(TAG, "📊 Detecciones estrategia 2: " + results2.size());

        long dt = System.currentTimeMillis() - t0;

        if (!results2.isEmpty()) {
            Log.d(TAG, "✅ ÉXITO con estrategia 2 | Total: " + dt + " ms | detecciones=" + results2.size());
            return results2;
        } else {
            Log.d(TAG, "⚠️ Ninguna estrategia detectó dígitos | Total: " + dt + " ms");
            return results2; // Retornar lista vacía
        }
    }

    /**
     * Ejecuta la inferencia TFLite sobre una imagen preprocesada.
     * Método extraído para reutilizar en ambas estrategias.
     */
    private List<DetectionResult> runInference(@NonNull Bitmap preprocessedBitmap, int origW, int origH) {
        ByteBuffer input = preprocess(preprocessedBitmap);

        int[] outShape = interpreter.getOutputTensor(0).shape();
        if (outShape.length != 3)
            throw new IllegalStateException("Output TFLite inesperado: " + Arrays.toString(outShape));

        float[][][] raw = new float[outShape[0]][outShape[1]][outShape[2]];
        interpreter.run(input, raw);

        int channels = 4 + numClasses;
        boolean layoutCFirst;
        if (outShape[1] == channels) layoutCFirst = true;
        else if (outShape[2] == channels) layoutCFirst = false;
        else layoutCFirst = (outShape[1] > outShape[2] && outShape[1] >= channels);

        List<DetectionResult> preNms = layoutCFirst
                ? parsePreds_CFirst(raw[0], origW, origH)
                : parsePreds_PFirst(raw[0], origW, origH);

        return nmsClassAgnostic(preNms, NMS_IOU_THRESHOLD);
    }

    /**
     * ESTRATEGIA 2: Inversión de imagen para displays LED.
     * Convierte segmentos brillantes en oscuros para mejor detección.
     */
    private Bitmap invertImagePreprocessing(@NonNull Bitmap src) {
        int width = src.getWidth();
        int height = src.getHeight();
        int[] pixels = new int[width * height];
        src.getPixels(pixels, 0, width, 0, 0, width, height);

        for (int i = 0; i < pixels.length; i++) {
            int pixel = pixels[i];
            int r = (pixel >> 16) & 0xFF;
            int g = (pixel >> 8) & 0xFF;
            int b = pixel & 0xFF;

            int gray = (int)(0.299 * r + 0.587 * g + 0.114 * b);
            int inverted = 255 - gray;

            pixels[i] = (0xFF << 24) | (inverted << 16) | (inverted << 8) | inverted;
        }

        Bitmap result = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888);
        result.setPixels(pixels, 0, width, 0, 0, width, height);

        return result;
    }

    private ByteBuffer preprocess(@NonNull Bitmap src) {
        Bitmap resized = Bitmap.createScaledBitmap(src, INPUT_SIZE, INPUT_SIZE, true);
        ByteBuffer buf = ByteBuffer.allocateDirect(1 * INPUT_SIZE * INPUT_SIZE * 3 * 4);
        buf.order(ByteOrder.nativeOrder());
        buf.rewind();

        int[] pixels = new int[INPUT_SIZE * INPUT_SIZE];
        resized.getPixels(pixels, 0, INPUT_SIZE, 0, 0, INPUT_SIZE, INPUT_SIZE);

        for (int p : pixels) {
            buf.putFloat(((p >> 16) & 0xFF) / 255.0f);
            buf.putFloat(((p >> 8) & 0xFF) / 255.0f);
            buf.putFloat((p & 0xFF) / 255.0f);
        }
        buf.rewind();
        return buf;
    }

    private List<DetectionResult> parsePreds_CFirst(float[][] preds, int origW, int origH) {
        List<DetectionResult> out = new ArrayList<>();
        int N = preds[0].length;
        for (int i = 0; i < N; i++) {
            float cx = preds[0][i], cy = preds[1][i], w = preds[2][i], h = preds[3][i];
            int best = -1; float bestScore = -1f;
            for (int c = 0; c < numClasses; c++) {
                float s = preds[4 + c][i];
                if (s > bestScore) { bestScore = s; best = c; }
            }
            if (bestScore < CONF_THRESHOLD || best < 0) continue;
            Log.d(TAG, "✓ Dígito: " + labels.get(best) + " | conf=" + String.format("%.2f", bestScore));
            addBox(out, cx, cy, w, h, best, bestScore, origW, origH);
        }
        return out;
    }

    private List<DetectionResult> parsePreds_PFirst(float[][] preds, int origW, int origH) {
        List<DetectionResult> out = new ArrayList<>();
        int N = preds.length;
        for (int i = 0; i < N; i++) {
            float cx = preds[i][0], cy = preds[i][1], w = preds[i][2], h = preds[i][3];
            int best = -1; float bestScore = -1f;
            for (int c = 0; c < numClasses; c++) {
                float s = preds[i][4 + c];
                if (s > bestScore) { bestScore = s; best = c; }
            }
            if (bestScore < CONF_THRESHOLD || best < 0) continue;
            Log.d(TAG, "✓ Dígito: " + labels.get(best) + " | conf=" + String.format("%.2f", bestScore));
            addBox(out, cx, cy, w, h, best, bestScore, origW, origH);
        }
        return out;
    }

    private void addBox(List<DetectionResult> list, float cx, float cy, float w, float h,
                        int classId, float score, int origW, int origH) {
        float left = cx - w/2f, top = cy - h/2f, right = cx + w/2f, bottom = cy + h/2f;
        float scaleX = (float) origW / INPUT_SIZE, scaleY = (float) origH / INPUT_SIZE;
        RectF box = new RectF(
                clamp(left*scaleX, 0, origW),
                clamp(top*scaleY, 0, origH),
                clamp(right*scaleX, 0, origW),
                clamp(bottom*scaleY, 0, origH)
        );
        if (box.width() <= 0 || box.height() <= 0) return;
        String label = (classId >= 0 && classId < labels.size()) ? labels.get(classId) : ("digit_" + classId);
        list.add(new DetectionResult(label, score, box));
    }

    private float clamp(float v, float lo, float hi) { return Math.max(lo, Math.min(hi, v)); }

    private List<DetectionResult> nmsClassAgnostic(List<DetectionResult> dets, float iouTh) {
        if (dets.isEmpty()) return dets;
        Collections.sort(dets, (a,b) -> Float.compare(b.confidence, a.confidence));
        List<DetectionResult> kept = new ArrayList<>();
        boolean[] removed = new boolean[dets.size()];
        for (int i = 0; i < dets.size(); i++) {
            if (removed[i]) continue;
            DetectionResult di = dets.get(i);
            kept.add(di);
            for (int j = i+1; j < dets.size(); j++) {
                if (removed[j]) continue;
                if (iou(di.boundingBox, dets.get(j).boundingBox) > iouTh) removed[j]=true;
            }
        }
        return kept;
    }

    private float iou(RectF a, RectF b) {
        float ix1 = Math.max(a.left, b.left), iy1 = Math.max(a.top, b.top);
        float ix2 = Math.min(a.right, b.right), iy2 = Math.min(a.bottom, b.bottom);
        float iw = Math.max(0, ix2-ix1), ih = Math.max(0, iy2-iy1);
        float inter = iw*ih;
        float areaA = Math.max(0,a.width())*Math.max(0,a.height());
        float areaB = Math.max(0,b.width())*Math.max(0,b.height());
        float union = areaA+areaB-inter;
        return union>0? inter/union:0f;
    }

    public void close() {
        try { interpreter.close(); } catch (Throwable ignore) {}
        executor.shutdown();
    }

    public static class DetectionResult {
        private final String label;
        private final float confidence;
        private final RectF boundingBox;

        public DetectionResult(String label, float confidence, RectF boundingBox) {
            this.label = label;
            this.confidence = confidence;
            this.boundingBox = boundingBox;
        }

        public String getLabel() { return label; }
        public float getConfidence() { return confidence; }
        public RectF getBoundingBox() { return boundingBox; }

        @NonNull
        @Override
        public String toString() {
            return "DetectionResult{" + "label='" + label + '\'' +
                    ", confidence=" + confidence + ", box=" + boundingBox + '}';
        }
    }
}