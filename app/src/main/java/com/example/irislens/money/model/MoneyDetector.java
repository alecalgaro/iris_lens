package com.example.irislens.money.model;

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
import java.util.Comparator;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * MoneyDetector para modelos YOLOv8 TFLite.
 * - Soporta outputs [1, 84, N] y [1, N, 84]
 * - NMS clase-agnóstico
 * - Coordenadas en píxeles del bitmap original
 * - API asíncrona compatible con MoneyRecognitionPresenter
 */
public class MoneyDetector {

    private static final String TAG = "MoneyDetector";
    private static final String MODEL_PATH = "detector.tflite";
    private static final String LABELS_PATH = "labels.txt";

    private static final float CONF_THRESHOLD = 0.25f; // ajustar si no detecta
    private static final float NMS_IOU_THRESHOLD = 0.5f;
    private static final int INPUT_SIZE = 640;

    private final Interpreter interpreter;
    private final List<String> labels;
    private final int numClasses;
    private final ExecutorService executor = Executors.newSingleThreadExecutor();

    public interface DetectionCallback {
        void onDetectionComplete(@NonNull List<DetectionResult> results);
        void onDetectionError(@NonNull Exception error);
    }

    public MoneyDetector(@NonNull Context context) throws IOException {
        Log.d(TAG, "Inicializando MoneyDetector...");

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
        ByteBuffer input = preprocess(bitmap);

        int[] outShape = interpreter.getOutputTensor(0).shape();
        DataType outType = interpreter.getOutputTensor(0).dataType();
        //Log.d(TAG, "Output shape=" + Arrays.toString(outShape) + " dtype=" + outType);

        if (outShape.length != 3)
            throw new IllegalStateException("Output TFLite inesperado: " + Arrays.toString(outShape));

        float[][][] raw = new float[outShape[0]][outShape[1]][outShape[2]];
        interpreter.run(input, raw);

        // 🔹 Log crudo de primeras predicciones
        int debugLimit = Math.min(5, raw[0].length);
        for (int i = 0; i < debugLimit; i++) {
            StringBuilder sb = new StringBuilder();
            sb.append("RawPred[").append(i).append("] cx=").append(raw[0][0][i])
                    .append(" cy=").append(raw[0][1][i])
                    .append(" w=").append(raw[0][2][i])
                    .append(" h=").append(raw[0][3][i]);
            for (int c = 0; c < Math.min(3, numClasses); c++) {
                sb.append(" c").append(c).append("=").append(raw[0][4 + c][i]);
            }
            //Log.d(TAG, sb.toString());
        }

        int channels = 4 + numClasses;
        boolean layoutCFirst;
        if (outShape[1] == channels) layoutCFirst = true;
        else if (outShape[2] == channels) layoutCFirst = false;
        else layoutCFirst = (outShape[1] > outShape[2] && outShape[1] >= channels);

        List<DetectionResult> preNms = layoutCFirst
                ? parsePreds_CFirst(raw[0], bitmap.getWidth(), bitmap.getHeight())
                : parsePreds_PFirst(raw[0], bitmap.getWidth(), bitmap.getHeight());

        // 🔹 Log detecciones candidatas
        /*
        for (DetectionResult det : preNms) {
            Log.d(TAG, "CandidateDetection: " + det.getLabel() +
                    " score=" + det.getConfidence() +
                    " box=" + det.getBoundingBox());
        }
        */

        List<DetectionResult> finalDetections = nmsClassAgnostic(preNms, NMS_IOU_THRESHOLD);

        long dt = System.currentTimeMillis() - t0;
        Log.d(TAG, "⏱️ Inferencia+post: " + dt + " ms | detecciones finales=" + finalDetections.size());

        return finalDetections;
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
            Log.d(TAG, "parseCFirst: class=" + best + " (" + labels.get(best) + ") score=" + bestScore +
                    " cx=" + cx + " cy=" + cy + " w=" + w + " h=" + h);
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
            Log.d(TAG, "parsePFirst: class=" + best + " (" + labels.get(best) + ") score=" + bestScore +
                    " cx=" + cx + " cy=" + cy + " w=" + w + " h=" + h);
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
        if (box.width() <= 0 || box.height() <= 0) return; // 🔹 permitir cajas grandes
        String label = (classId >= 0 && classId < labels.size()) ? labels.get(classId) : ("class_" + classId);
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
            this.label = label; this.confidence = confidence; this.boundingBox = boundingBox;
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