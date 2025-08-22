package com.example.irislens.money.model;

import android.content.Context;
import android.graphics.Bitmap;
import android.util.Log;

import org.tensorflow.lite.DataType;
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
import java.util.ArrayList;
import java.util.List;

public class MoneyDetector {
    private final ObjectDetector objectDetector;
    private final List<String> labels;

    public MoneyDetector(Context context) throws IOException {
        // Cargar archivos labels.txt con etiquetas
        labels = loadLabels(context, "labels.txt");
        Log.d("MoneyDetector", "Labels cargadas: " + labels.size() + " -> " + labels);

        // Configurar detector
        ObjectDetector.ObjectDetectorOptions options =
                ObjectDetector.ObjectDetectorOptions.builder()
                        .setMaxResults(5)
                        .setScoreThreshold(0.4f) // umbral
                        .build();

        // Cargar modelo TFLite
        objectDetector = ObjectDetector.createFromFileAndOptions(
                context,
                "detector.tflite",
                options
        );
        Log.d("MoneyDetector", "Modelo .tflite cargado correctamente");
    }

    /**
     * Carga las etiquetas desde un archivo de texto en los assets.
     *
     * @param context Contexto de la aplicacion.
     * @param fileName Nombre del archivo de etiquetas.
     * @return Lista de etiquetas.
     * @throws IOException Si ocurre un error al leer el archivo.
     */
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

    /**
     * Detecta objetos en una imagen.
     *
     * @param bitmap Imagen a procesar.
     * @return Lista de detecciones con sus categorias y scores.
     */
    public List<Detection> detect(Bitmap bitmap) {
        // Asegurarse de que el bitmap sea mutable y tenga el formato correcto
        bitmap = bitmap.copy(Bitmap.Config.ARGB_8888, true);

        // Convertir Bitmap a TensorImage
        TensorImage image = new TensorImage(DataType.FLOAT32);
        image.load(bitmap);

        // Crear un procesador de imagen para redimensionar
        ImageProcessor processor = new ImageProcessor.Builder()
                .add(new ResizeOp(320, 320, ResizeOp.ResizeMethod.BILINEAR))
                //.add(new NormalizeOp(0f, 255f)) // No se debe normalizar porque los datos ya llegan normalizados (si descomentamos esta linea, baja el score de deteccion)
                .build();

        // Aplicar el procesador a la imagen
        TensorImage processedImage = processor.process(image);

        // Detectar objetos en la imagen procesada
        List<Detection> rawDetections = objectDetector.detect(processedImage);
        List<Detection> filteredDetections = new ArrayList<>();
        // Encontrar el score maximo entre las detecciones
        float maxScore = 0f;
        for (Detection d : rawDetections) {
            if (!d.getCategories().isEmpty()) {
                Category category = d.getCategories().get(0);
                float score = category.getScore();
                if (score > maxScore) maxScore = score;

                // Logear todos los scores, incluso si son bajos (solo para depuracion)
                Log.d("MoneyDetector", "Frame Score: " + category.getLabel() + " -> " + score);

                // Filtrar solo para detecciones que superen el umbral
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