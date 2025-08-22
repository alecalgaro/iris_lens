package com.example.irislens.money.model;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.RectF;
import android.util.Log;

import org.tensorflow.lite.DataType;
import org.tensorflow.lite.support.common.ops.NormalizeOp;
import org.tensorflow.lite.support.image.ImageProcessor;
import org.tensorflow.lite.support.image.ops.ResizeOp;
import org.tensorflow.lite.support.label.Category;
import org.tensorflow.lite.task.vision.detector.Detection;
import org.tensorflow.lite.task.vision.detector.ObjectDetector;
import org.tensorflow.lite.support.image.TensorImage;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.ArrayList; // 👈 Import necesario
import java.util.List;

public class MoneyDetector {
    private final ObjectDetector objectDetector;
    private final List<String> labels;

    public MoneyDetector(Context context) throws IOException {
        // Cargar labels.txt
        labels = loadLabels(context, "labels.txt");
        Log.d("MoneyDetector", "Labels cargadas: " + labels.size() + " -> " + labels);

        // Configurar detector
        ObjectDetector.ObjectDetectorOptions options =
                ObjectDetector.ObjectDetectorOptions.builder()
                        .setMaxResults(5)
                        .setScoreThreshold(0.4f) // umbral
                        .build();

        objectDetector = ObjectDetector.createFromFileAndOptions(
                context,
                "detector.tflite",
                options
        );
        Log.d("MoneyDetector", "Modelo cargado correctamente");
    }

    private List<String> loadLabels(Context context, String fileName) throws IOException {
        List<String> labels = new ArrayList<>();
        try (InputStream is = context.getAssets().open(fileName);
             BufferedReader reader = new BufferedReader(new InputStreamReader(is))) {
            String line;
            while ((line = reader.readLine()) != null) {
                labels.add(line);
            }
        }
        return labels;
    }

    public List<Detection> detect(Bitmap bitmap) {
        // ✅ Asegurar formato correcto del bitmap
        bitmap = bitmap.copy(Bitmap.Config.ARGB_8888, true);

        // ✅ Preprocesamiento alineado con Colab
        TensorImage image = new TensorImage(DataType.FLOAT32);
        image.load(bitmap);

        ImageProcessor processor = new ImageProcessor.Builder()
                .add(new ResizeOp(320, 320, ResizeOp.ResizeMethod.BILINEAR))
                //.add(new NormalizeOp(0f, 255f)) // No se debe normalizar porque los datos ya llegan normalizados (si descomentamos esta línea, baja el score de detección)
                .build();

        TensorImage processedImage = processor.process(image);

        // ✅ Detección
        List<Detection> rawDetections = objectDetector.detect(processedImage);
        List<Detection> filteredDetections = new ArrayList<>();

        float maxScore = 0f;
        for (Detection d : rawDetections) {
            if (!d.getCategories().isEmpty()) {
                Category category = d.getCategories().get(0);
                float score = category.getScore();
                if (score > maxScore) maxScore = score;

                // Logear **todos los scores**, incluso si son bajos
                Log.d("MoneyDetector", "Frame Score: " + category.getLabel() + " -> " + score);

                // Filtrar solo para detecciones “válidas”
                if (score >= 0.4f) {
                    filteredDetections.add(d);
                }
            }
        }

        Log.d("MoneyDetector", "Detectados " + filteredDetections.size() + " objetos válidos. Score máximo: " + maxScore);

        return filteredDetections;
    }

    public List<String> getLabels() {
        return labels;
    }
}
