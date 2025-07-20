package com.example.irislens.medicine.model;

import android.content.Context;
import android.graphics.Bitmap;
import android.widget.Toast;

import com.example.irislens.R;
import com.googlecode.tesseract.android.TessBaseAPI;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;

public class ReadImageText {

    private TessBaseAPI tess;
    private String folderTessDataName = "tessdata";
    private String pathDir;

    /**
     * Inicializa la API de Tesseract y copia los archivos necesarios a la carpeta de datos de la aplicacion.
     *
     * @param context El contexto de la aplicación
     */
    public ReadImageText(Context context) {
        tess = new TessBaseAPI();   // Instancia de la API de Tesseract
        pathDir = context.getFilesDir().toString(); // Directorio de la aplicacion

        // Carpeta tessdata
        File folder = new File(pathDir, folderTessDataName);

        // Crear la carpeta si no existe
        if (!folder.exists()) {
            folder.mkdir();
        }

        // Agregar los archivos de entrenamiento a la carpeta tessdata
        if (folder.exists()) {
            addFile("spa.traineddata", R.raw.spa, context);
        }

        // Inicializar tesseract
        tess.init(pathDir, "spa");
    }

    /**
     * Copia un archivo de la carpeta de activos a la carpeta de datos de la aplicacion
     *
     * @param name    El nombre del archivo
     * @param source  El recurso de origen
     * @param context El contexto de la aplicacion
     */
    private void addFile(String name, int source, Context context) {
        File file = new File(pathDir + "/" + folderTessDataName + "/" + name);
        if (!file.exists()) {
            try (InputStream inputStream = context.getResources().openRawResource(source);
                 OutputStream outputStream = new FileOutputStream(file)) {
                byte[] buffer = new byte[1024];
                int length;
                while ((length = inputStream.read(buffer)) > 0) {
                    outputStream.write(buffer, 0, length);
                }
                file.createNewFile();
            } catch (IOException e) {
                e.printStackTrace();
            }
        }

        // Verificar si el archivo se copio correctamente
        if (!file.exists()) {
            //Log.e(TAG, "Failed to copy tessdata file");
            Toast.makeText(context, "tessdata no existe", Toast.LENGTH_SHORT).show();
        }
    }

    /**
     * Procesa una imagen con tesseract para extraer el texto
     *
     * @param image La imagen a procesar
     * @return El texto extraido de la imagen
     */
    public String processImage(Bitmap image) {
        tess.setImage(image);
        return tess.getUTF8Text();
    }
}
