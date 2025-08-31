package com.example.irislens.money.model;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.RectF;
import android.os.Build;
import android.util.Log;

import androidx.annotation.NonNull;

import org.tensorflow.lite.Interpreter;
import org.tensorflow.lite.support.common.ops.NormalizeOp;
import org.tensorflow.lite.support.image.ImageProcessor;
import org.tensorflow.lite.support.image.TensorImage;
import org.tensorflow.lite.support.image.ops.ResizeOp;
import org.tensorflow.lite.support.tensorbuffer.TensorBuffer;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.MappedByteBuffer;
import java.nio.channels.FileChannel;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class MoneyDetector {
    private static final String TAG = "MoneyDetector";
    private static final String MODEL_PATH = "detector.tflite";
    private static final String LABELS_PATH = "labels.txt";

    // Thresholds dinámicos (podés ajustarlos en runtime si querés)
    private static final float CONFIDENCE_THRESHOLD = 0.96f;
    private static final float NMS_THRESHOLD = 0.7f;

    private static final int INPUT_SIZE = 640;

    private Interpreter interpreter;
    private List<String> labels;
    private ImageProcessor imageProcessor;
    private TensorImage inputImageBuffer;
    private TensorBuffer outputBuffer;
    private ExecutorService executorService;

    public interface DetectionCallback {
        void onDetectionComplete(@NonNull List<DetectionResult> results);
        void onDetectionError(@NonNull Exception error);
    }

    public MoneyDetector(Context context) throws IOException {
        Log.d(TAG, "Inicializando MoneyDetector para Android 6+...");

        // 1. Verificar compatibilidad básica
        if (Build.VERSION.SDK_INT < 23) {
            throw new RuntimeException("Requiere Android 6.0+ (API 23)");
        }

        labels = loadLabelsFromAssets(context, LABELS_PATH);
        Log.d(TAG, "Labels cargadas: " + labels.size());

        MappedByteBuffer modelBuffer = loadModelFromAssets(context, MODEL_PATH);
        Log.d(TAG, "Modelo cargado: " + modelBuffer.capacity() + " bytes");

        // 2. Configuración ULTRA CONSERVADORA para Android 6
        Interpreter.Options options = new Interpreter.Options();

        // Threads mínimos para estabilidad
        int numThreads = Math.min(2, Runtime.getRuntime().availableProcessors());
        options.setNumThreads(numThreads);

        // ⚠️ CRÍTICO: TODO DESHABILITADO para Android 6
        options.setUseNNAPI(false);
        options.setUseXNNPACK(false);  // ❌ NUNCA habilitar en Android 6
        options.setAllowFp16PrecisionForFp32(false);

        Log.d(TAG, "Configuración ultra-segura: " + numThreads + " threads, sin aceleración");

        try {
            interpreter = new Interpreter(modelBuffer, options);
            Log.d(TAG, "Intérprete inicializado correctamente");
        } catch (Exception e) {
            Log.e(TAG, "Error creando intérprete", e);
            throw new IOException("TensorFlow Lite falló: " + e.getMessage());
        }

        // 3. Procesamiento de imagen - QUITAR normalización para YOLOv8
        imageProcessor = new ImageProcessor.Builder()
                .add(new ResizeOp(INPUT_SIZE, INPUT_SIZE, ResizeOp.ResizeMethod.BILINEAR))
                .add(new NormalizeOp(0f, 255f)) // YOLOv8 espera valores normalizados [0,1]. Se divide por 255
                .build();

        try {
            inputImageBuffer = new TensorImage(org.tensorflow.lite.DataType.FLOAT32);
            int[] outputShape = interpreter.getOutputTensor(0).shape();
            Log.d(TAG, "Output tensor shape: " + java.util.Arrays.toString(outputShape));
            outputBuffer = TensorBuffer.createFixedSize(outputShape, org.tensorflow.lite.DataType.FLOAT32);
        } catch (Exception e) {
            Log.e(TAG, "Error configurando tensors", e);
            throw new IOException("Error tensors: " + e.getMessage());
        }

        executorService = Executors.newSingleThreadExecutor();
        Log.d(TAG, "MoneyDetector inicializado para Android 6");
    }

    public List<DetectionResult> detect(Bitmap bitmap) {
        Log.d(TAG, "Detectando en bitmap: " + bitmap.getWidth() + "x" + bitmap.getHeight());

        inputImageBuffer.load(bitmap);
        inputImageBuffer = imageProcessor.process(inputImageBuffer);

        long startTime = System.currentTimeMillis();
        interpreter.run(inputImageBuffer.getBuffer(), outputBuffer.getBuffer());
        long inferenceTime = System.currentTimeMillis() - startTime;
        Log.d(TAG, "Inferencia completada en " + inferenceTime + " ms");

        return postProcessOutput(outputBuffer.getFloatArray(), bitmap.getWidth(), bitmap.getHeight());
    }

    private List<DetectionResult> postProcessOutput(float[] output, int imageWidth, int imageHeight) {
        List<DetectionResult> detections = new ArrayList<>();
        int numClasses = labels.size();

        // YOLOv8 formato: [1, numClasses+4, numPredictions] aplanado
        // Primero detectar el formato correcto
        int totalFeatures = numClasses + 4; // cx, cy, w, h + classes
        int numPredictions = output.length / totalFeatures;

        Log.d(TAG, "Formato detectado - Features: " + totalFeatures + ", Predicciones: " + numPredictions);

        for (int i = 0; i < numPredictions; i++) {
            int offset = i * totalFeatures;

            // Verificar bounds
            if (offset + totalFeatures > output.length) {
                continue;
            }

            // Coordenadas del bounding box (formato YOLOv8: cx, cy, w, h normalizadas)
            float centerX = output[offset];
            float centerY = output[offset + 1];
            float width = output[offset + 2];
            float height = output[offset + 3];

            // Encontrar clase con mayor probabilidad
            float maxScore = 0;
            int maxIndex = 0;
            for (int c = 0; c < numClasses; c++) {
                if (offset + 4 + c < output.length) {
                    float score = output[offset + 4 + c];
                    if (score > maxScore) {
                        maxScore = score;
                        maxIndex = c;
                    }
                }
            }

            // Filtrar por confianza
            if (maxScore < CONFIDENCE_THRESHOLD) continue;

            // Convertir coordenadas normalizadas [0,1] a píxeles
            float scaleX = (float) imageWidth / INPUT_SIZE;
            float scaleY = (float) imageHeight / INPUT_SIZE;

            float left = (centerX - width / 2) * scaleX;
            float top = (centerY - height / 2) * scaleY;
            float right = (centerX + width / 2) * scaleX;
            float bottom = (centerY + height / 2) * scaleY;

            // Clamp a límites de imagen
            RectF box = new RectF(
                    Math.max(0, left),
                    Math.max(0, top),
                    Math.min(imageWidth, right),
                    Math.min(imageHeight, bottom)
            );

            String className = (maxIndex < labels.size()) ? labels.get(maxIndex) : "clase_" + maxIndex;
            detections.add(new DetectionResult(className, maxScore, box));
        }

        List<DetectionResult> finalResults = applyNMS(detections);
        Log.d(TAG, "Detecciones finales tras NMS: " + finalResults.size());
        return finalResults;
    }

    private List<DetectionResult> applyNMS(List<DetectionResult> detections) {
        // Ordenar detecciones por confianza descendente
        Collections.sort(detections, new Comparator<DetectionResult>() {
            @Override
            public int compare(DetectionResult a, DetectionResult b) {
                return Float.compare(b.getConfidence(), a.getConfidence());
            }
        });

        List<DetectionResult> filtered = new ArrayList<>();
        boolean[] suppressed = new boolean[detections.size()];

        for (int i = 0; i < detections.size(); i++) {
            if (suppressed[i]) continue;
            DetectionResult d1 = detections.get(i);
            filtered.add(d1);

            for (int j = i + 1; j < detections.size(); j++) {
                if (suppressed[j]) continue;
                DetectionResult d2 = detections.get(j);

                float iou = calculateIOU(d1.getBoundingBox(), d2.getBoundingBox());
                if (iou > NMS_THRESHOLD) {
                    suppressed[j] = true;
                }
            }
        }

        return filtered;
    }

    private float calculateIOU(RectF a, RectF b) {
        float intersectionLeft = Math.max(a.left, b.left);
        float intersectionTop = Math.max(a.top, b.top);
        float intersectionRight = Math.min(a.right, b.right);
        float intersectionBottom = Math.min(a.bottom, b.bottom);

        float intersectionArea = Math.max(0, intersectionRight - intersectionLeft) *
                Math.max(0, intersectionBottom - intersectionTop);
        float areaA = (a.right - a.left) * (a.bottom - a.top);
        float areaB = (b.right - b.left) * (b.bottom - b.top);
        float union = areaA + areaB - intersectionArea;

        return union <= 0 ? 0 : intersectionArea / union;
    }

    private List<String> loadLabelsFromAssets(Context context, String fileName) throws IOException {
        List<String> labels = new ArrayList<>();
        InputStream is = context.getAssets().open(fileName);
        BufferedReader reader = new BufferedReader(new InputStreamReader(is));
        String line;
        while ((line = reader.readLine()) != null) {
            labels.add(line);
        }
        reader.close();
        return labels;
    }

    public void detect(Bitmap bitmap, @NonNull DetectionCallback callback) {
        if (bitmap == null || bitmap.isRecycled()) {
            callback.onDetectionError(new IllegalArgumentException("Bitmap inválido"));
            return;
        }

        executorService.execute(() -> {
            try {
                // Preprocesar imagen
                inputImageBuffer.load(bitmap);
                inputImageBuffer = imageProcessor.process(inputImageBuffer);

                // Ejecutar inferencia
                long startTime = System.currentTimeMillis();
                interpreter.run(inputImageBuffer.getBuffer(), outputBuffer.getBuffer());
                //long inferenceTime = System.currentTimeMillis() - startTime;
                //Log.d(TAG, "Inferencia completada en " + inferenceTime + "ms");

                // Postprocesar salida
                List<DetectionResult> results = postProcessOutput(
                        outputBuffer.getFloatArray(),
                        bitmap.getWidth(),
                        bitmap.getHeight()
                );
                callback.onDetectionComplete(results);

            } catch (Exception e) {
                Log.e(TAG, "Error en detección", e);
                callback.onDetectionError(e);
            }
        });
    }

    private MappedByteBuffer loadModelFromAssets(Context context, String modelPath) throws IOException {
        // Crear archivo temporal en storage interno
        File tempFile = new File(context.getFilesDir(), modelPath);

        // Copiar desde assets solo si no existe
        if (!tempFile.exists()) {
            try (InputStream is = context.getAssets().open(modelPath);
                 FileOutputStream fos = new FileOutputStream(tempFile)) {

                byte[] buffer = new byte[4096];
                int bytesRead;
                while ((bytesRead = is.read(buffer)) != -1) {
                    fos.write(buffer, 0, bytesRead);
                }
            }
        }

        // Ahora usar FileInputStream real para crear MappedByteBuffer
        try (FileInputStream fis = new FileInputStream(tempFile);
             FileChannel fileChannel = fis.getChannel()) {
            return fileChannel.map(FileChannel.MapMode.READ_ONLY, 0, fileChannel.size());
        }
    }

    public void close() {
        Log.d(TAG, "Liberando recursos");
        if (interpreter != null) {
            interpreter.close();
        }
        if (executorService != null) {
            executorService.shutdown();
        }
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

        @Override
        public String toString() {
            return String.format("DetectionResult{label='%s', conf=%.2f, box=[%.1f,%.1f,%.1f,%.1f]}",
                    label, confidence, boundingBox.left, boundingBox.top, boundingBox.right, boundingBox.bottom);
        }
    }
}
