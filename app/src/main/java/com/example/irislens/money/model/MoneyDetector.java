package com.example.irislens.money.model;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.RectF;
import android.util.Log;

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
        Log.d("MoneyDetector", "Labels cargadas: " + labels.size());

        // Cargar modelo TFLite
        ObjectDetector.ObjectDetectorOptions options =
                ObjectDetector.ObjectDetectorOptions.builder()
                        .setMaxResults(3)
                        .setScoreThreshold(0.5f)
                        .build();


        objectDetector = ObjectDetector.createFromFileAndOptions(
                context,
                "model.tflite",
                options
        );
        Log.d("MoneyDetector", "Modelo cargado correctamente");
    }


    // ✅ Versión sin streams, 100% compatible
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
        TensorImage image = TensorImage.fromBitmap(bitmap);
        return objectDetector.detect(image);
    }
    /*
    public List<Detection> detect(Bitmap bitmap) {
        return new ArrayList<>(); // Vacío, para testear el flujo
    }
    */


    public List<String> getLabels() {
        return labels;
    }
}
