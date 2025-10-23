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
 * Modelo basado en YOLOv8n con preprocesamiento optimizado para displays LED de colores.
 */
public class DisplayDetector {
    private static final String TAG = "DisplayDetector";
    private static final String MODEL_PATH = "detectorDisplay.tflite";
    private static final String LABELS_PATH = "labelsDisplay.txt";

    private static final float CONF_THRESHOLD = 0.55f; // Umbral más bajo para testing
    private static final float NMS_IOU_THRESHOLD = 0.5f;
    private static final int INPUT_SIZE = 640;

    // ============ CONFIGURACIÓN DEBUG ============
    // DEBUG: Activar para ver imagen preprocesada en pantalla
    public static final boolean DEBUG_SHOW_PREPROCESSED = false;  // Cambiar a false para desactivar

    // AUTO: Selección automática de estrategia de preprocesamiento
    public static final boolean AUTO_STRATEGY = true;  // Mantener en true para modo inteligente
    // ============================================

    private final Interpreter interpreter;
    private final List<String> labels;
    private final int numClasses;
    private final ExecutorService executor = Executors.newSingleThreadExecutor();

    // Callback para previsualización de debug
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

        // PREPROCESAR con selección automática de estrategia
        Bitmap preprocessedBitmap;
        int selectedStrategy;

        if (AUTO_STRATEGY) {
            // Analizar la imagen para decidir la estrategia
            selectedStrategy = selectBestStrategy(bitmap);
            Log.d(TAG, "🤖 Modo AUTO: Estrategia seleccionada = " + selectedStrategy);
        } else {
            selectedStrategy = 1; // Fallback manual si se desactiva AUTO
        }

        // Aplicar la estrategia seleccionada
        switch (selectedStrategy) {
            case 2:
                preprocessedBitmap = invertImagePreprocessing(bitmap);
                break;
            case 1:
            default:
                preprocessedBitmap = normalizeDisplayColors(bitmap);
                break;
        }

        long tPreprocess = System.currentTimeMillis();
        Log.d(TAG, "⏱️ Preprocesamiento (estrategia " + selectedStrategy + "): " + (tPreprocess - t0) + " ms");

        // DEBUG: Enviar imagen preprocesada para visualización
        if (DEBUG_SHOW_PREPROCESSED && debugCallback != null) {
            debugCallback.onPreprocessedImage(preprocessedBitmap);
        }

        ByteBuffer input = preprocess(preprocessedBitmap);

        int[] outShape = interpreter.getOutputTensor(0).shape();
        if (outShape.length != 3)
            throw new IllegalStateException("Output TFLite inesperado: " + Arrays.toString(outShape));

        float[][][] raw = new float[outShape[0]][outShape[1]][outShape[2]];
        interpreter.run(input, raw);
        long tInference = System.currentTimeMillis();
        Log.d(TAG, "⏱️ Inferencia TFLite: " + (tInference - tPreprocess) + " ms");

        int channels = 4 + numClasses;
        boolean layoutCFirst;
        if (outShape[1] == channels) layoutCFirst = true;
        else if (outShape[2] == channels) layoutCFirst = false;
        else layoutCFirst = (outShape[1] > outShape[2] && outShape[1] >= channels);

        List<DetectionResult> preNms = layoutCFirst
                ? parsePreds_CFirst(raw[0], bitmap.getWidth(), bitmap.getHeight())
                : parsePreds_PFirst(raw[0], bitmap.getWidth(), bitmap.getHeight());

        List<DetectionResult> finalDetections = nmsClassAgnostic(preNms, NMS_IOU_THRESHOLD);

        long dt = System.currentTimeMillis() - t0;
        Log.d(TAG, "⏱️ TOTAL: " + dt + " ms | detecciones=" + finalDetections.size());

        return finalDetections;
    }

    /**
     * SELECTOR INTELIGENTE: Analiza la imagen y decide automáticamente qué estrategia usar.
     *
     * Criterios de decisión:
     * - Estrategia 2 (inversión): LEDs muy brillantes sobre fondo oscuro
     * - Estrategia 1 (normalización): Displays normales o con contraste moderado
     *
     * @param bitmap Imagen a analizar
     * @return 1 o 2 según la estrategia recomendada
     */
    private int selectBestStrategy(@NonNull Bitmap bitmap) {
        int width = bitmap.getWidth();
        int height = bitmap.getHeight();

        // Muestrear la imagen (cada 4 píxeles para velocidad)
        int sampleStep = 4;
        int sampleCount = 0;

        int brightPixelCount = 0;      // Píxeles muy brillantes (>200)
        int veryBrightPixelCount = 0;  // Píxeles extremadamente brillantes (>230)
        int darkPixelCount = 0;         // Píxeles oscuros (<50)
        long totalBrightness = 0;

        for (int y = 0; y < height; y += sampleStep) {
            for (int x = 0; x < width; x += sampleStep) {
                int pixel = bitmap.getPixel(x, y);
                int r = (pixel >> 16) & 0xFF;
                int g = (pixel >> 8) & 0xFF;
                int b = pixel & 0xFF;

                // Calcular brillo máximo (importante para LEDs de color)
                int maxChannel = Math.max(r, Math.max(g, b));
                totalBrightness += maxChannel;

                if (maxChannel > 230) veryBrightPixelCount++;
                if (maxChannel > 200) brightPixelCount++;
                if (maxChannel < 50) darkPixelCount++;

                sampleCount++;
            }
        }

        float avgBrightness = (float) totalBrightness / sampleCount;
        float brightRatio = (float) brightPixelCount / sampleCount;
        float veryBrightRatio = (float) veryBrightPixelCount / sampleCount;
        float darkRatio = (float) darkPixelCount / sampleCount;

        Log.d(TAG, String.format("📊 Análisis AUTO: brillo_avg=%.1f | brillantes=%.1f%% | muy_brillantes=%.1f%% | oscuros=%.1f%%",
                avgBrightness, brightRatio * 100, veryBrightRatio * 100, darkRatio * 100));

        // CRITERIOS DE DECISIÓN:

        // Caso 1: LEDs MUY brillantes sobre fondo oscuro → INVERTIR
        // Características: Muchos píxeles muy brillantes + muchos píxeles oscuros
        if (veryBrightRatio > 0.10f && darkRatio > 0.30f) {
            Log.d(TAG, "✓ Detectado: LEDs muy brillantes sobre fondo oscuro → Estrategia 2 (Inversión)");
            return 2;
        }

        // Caso 2: Brillo promedio muy alto con contraste alto → INVERTIR
        // Características: Display muy brillante con zonas oscuras marcadas
        if (avgBrightness > 150 && brightRatio > 0.20f && darkRatio > 0.25f) {
            Log.d(TAG, "✓ Detectado: Display brillante con alto contraste → Estrategia 2 (Inversión)");
            return 2;
        }

        // Caso 3: LEDs saturados (blancos puros) → INVERTIR
        // Características: Muchos píxeles extremadamente brillantes
        if (veryBrightRatio > 0.15f) {
            Log.d(TAG, "✓ Detectado: LEDs saturados/blancos → Estrategia 2 (Inversión)");
            return 2;
        }

        // Caso DEFAULT: Display normal o moderado → NORMALIZACIÓN
        Log.d(TAG, "✓ Detectado: Display normal/moderado → Estrategia 1 (Normalización)");
        return 1;
    }

    /**
     * NUEVA ESTRATEGIA: Convertir a escala de grises sin umbralización agresiva.
     * Mantiene los detalles de los segmentos LED preservando la información estructural.
     */
    private Bitmap normalizeDisplayColors(@NonNull Bitmap src) {
        int width = src.getWidth();
        int height = src.getHeight();
        int[] pixels = new int[width * height];
        src.getPixels(pixels, 0, width, 0, 0, width, height);

        // Analizar el rango de brillo para aplicar normalización adaptativa
        int minBrightness = 255;
        int maxBrightness = 0;
        int[] grayscaleValues = new int[pixels.length];

        // PASO 1: Convertir a escala de grises usando luminancia
        for (int i = 0; i < pixels.length; i++) {
            int pixel = pixels[i];
            int r = (pixel >> 16) & 0xFF;
            int g = (pixel >> 8) & 0xFF;
            int b = pixel & 0xFF;

            // Usar pesos estándar de luminancia (favorece canal verde)
            int gray = (int)(0.299 * r + 0.587 * g + 0.114 * b);
            grayscaleValues[i] = gray;

            minBrightness = Math.min(minBrightness, gray);
            maxBrightness = Math.max(maxBrightness, gray);
        }

        int brightnessRange = maxBrightness - minBrightness;
        float avgBrightness = (minBrightness + maxBrightness) / 2.0f;

        Log.d(TAG, String.format("📊 Análisis: min=%d, max=%d, rango=%d, promedio=%.1f",
                minBrightness, maxBrightness, brightnessRange, avgBrightness));

        // PASO 2: Normalización lineal para expandir el contraste
        // Esto preserva los detalles mientras aumenta la diferencia entre LEDs y fondo
        for (int i = 0; i < pixels.length; i++) {
            int gray = grayscaleValues[i];
            int normalized;

            if (brightnessRange > 50) {
                // Hay suficiente contraste: aplicar expansión lineal
                normalized = (int)(((gray - minBrightness) * 255.0f) / brightnessRange);
            } else {
                // Poco contraste: aplicar curva suave para resaltar diferencias
                float normalized01 = (gray - minBrightness) / (float)Math.max(1, brightnessRange);
                // Aplicar función gamma para aumentar contraste
                normalized = (int)(Math.pow(normalized01, 0.7) * 255);
            }

            // Clamp
            normalized = Math.max(0, Math.min(255, normalized));

            pixels[i] = (0xFF << 24) | (normalized << 16) | (normalized << 8) | normalized;
        }

        Bitmap result = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888);
        result.setPixels(pixels, 0, width, 0, 0, width, height);

        Log.d(TAG, "✅ Normalización lineal aplicada (preserva detalles)");
        return result;
    }

    /**
     * ESTRATEGIA 2: Inversión de imagen para displays LED brillantes sobre fondo oscuro.
     * Esta estrategia invierte los colores: LEDs brillantes → oscuros, Fondo oscuro → claro
     */
    private Bitmap invertImagePreprocessing(@NonNull Bitmap src) {
        int width = src.getWidth();
        int height = src.getHeight();
        int[] pixels = new int[width * height];
        src.getPixels(pixels, 0, width, 0, 0, width, height);

        Log.d(TAG, "🔄 Aplicando inversión de imagen");

        for (int i = 0; i < pixels.length; i++) {
            int pixel = pixels[i];
            int r = (pixel >> 16) & 0xFF;
            int g = (pixel >> 8) & 0xFF;
            int b = pixel & 0xFF;

            // Convertir a escala de grises
            int gray = (int)(0.299 * r + 0.587 * g + 0.114 * b);

            // INVERTIR: 255 - valor
            int inverted = 255 - gray;

            pixels[i] = (0xFF << 24) | (inverted << 16) | (inverted << 8) | inverted;
        }

        Bitmap result = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888);
        result.setPixels(pixels, 0, width, 0, 0, width, height);

        Log.d(TAG, "✅ Inversión completada");
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