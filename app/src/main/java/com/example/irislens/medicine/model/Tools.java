package com.example.irislens.medicine.model;

import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.util.Log;

import androidx.core.util.Pair;

import org.apache.commons.text.similarity.LevenshteinDistance;

import java.util.ArrayList;
import java.util.List;

public class Tools {
    private static final double SIMILARITY_THRESHOLD = 0.8;

    /**
     * Limpia el texto de caracteres no deseados y palabras cortas
     *
     * @param text El texto a limpiar
     * @return El texto limpio
     */
    public static String cleanupText(String text) {
        // Reemplaza cualquier caracter que no sea una letra o un numero con un espacio vacio
        String cleanedText = text.replaceAll("[^\\p{L}\\p{N}]", " ");

        // Divide el texto en palabras
        String[] words = cleanedText.split("\\s+");

        StringBuilder result = new StringBuilder();
        for (String word : words) {
            // Solo agrega palabras que tengan mas de 3 caracteres
            if (word.length() > 3) {
                result.append(word);
                result.append(" ");
            }
        }

        // Devuelve el texto limpio
        Log.d("Tools", "Texto limpio: " + result.toString().trim());
        return result.toString().trim();
    }

    /**
     * Busca similitudes entre una cadena de texto y las palabras en los JSON de medicamentos y principios activos
     *
     * @param stringTesseract La cadena de texto a comparar
     * @param db La base de datos SQLite donde se almacenan los medicamentos y principios activos
     * @return Una lista de pares donde cada par contiene el nombre del medicamento o principio activo y su descripcion
     */
    public static List<Pair<String, String>> searchSimilarity(String stringTesseract, SQLiteDatabase db) {
        List<Pair<String, String>> matches = new ArrayList<>();
        // Convierte la cadena de texto a minusculas para la comparacion
        String lowerCaseString = stringTesseract.toLowerCase();
        LevenshteinDistance levenshteinDistance = new LevenshteinDistance();

        // Primero busca en la base de datos de medicamentos
        Cursor cursor = db.query("medicamento", new String[]{"nombre", "descripcion"}, null, null, null, null, null);
        while (cursor.moveToNext()) {
            String nombre = cursor.getString(cursor.getColumnIndexOrThrow("nombre"));
            String descripcion = cursor.getString(cursor.getColumnIndexOrThrow("descripcion"));
            if (containsAllWords(lowerCaseString, nombre.toLowerCase())) {
                matches.add(new Pair<>(nombre, descripcion));
            }
        }
        cursor.close();

        // Obtener todas las palabras del texto detectado
        String[] words = lowerCaseString.split("\\s+");
        List<String> ngrams = new ArrayList<>();

        // Generar n-gramas de 1 a 3 palabras para contemplar principios activos cuyos nombres
        // sean combinaciones de palabras
        for (int n = 1; n <= 3; n++) {
            for (int i = 0; i <= words.length - n; i++) {
                StringBuilder sb = new StringBuilder();
                for (int j = 0; j < n; j++) {
                    if (j > 0) sb.append(" ");
                    sb.append(words[i + j]);
                }
                ngrams.add(sb.toString());
            }
        }

        // Si no hay coincidencias en medicamentos, buscar en principios activos comparando con n-gramas
        if (matches.isEmpty()) {
            cursor = db.query("principio_activo", new String[]{"nombre"}, null, null, null, null, null);
            while (cursor.moveToNext()) {
                String nombreDroga = cursor.getString(cursor.getColumnIndexOrThrow("nombre")).toLowerCase();
                for (String ngram : ngrams) {
                    double similarity = 1.0 - ((double) levenshteinDistance.apply(ngram, nombreDroga) / Math.max(ngram.length(), nombreDroga.length()));
                    if (similarity >= SIMILARITY_THRESHOLD) {
                        Pair<String, String> match = new Pair<>(cursor.getString(cursor.getColumnIndexOrThrow("nombre")), cursor.getString(cursor.getColumnIndexOrThrow("nombre")));
                        if (!matches.contains(match)) {
                            matches.add(match);
                        }
                        break;
                    }
                }
            }
            cursor.close();
        }

        return matches;
    }

    /**
     * Verifica si una cadena de texto contiene todas las palabras de otra cadena
     *
     * @param string La cadena de texto a verificar
     * @param key    La cadena de texto con las palabras a buscar
     * @return true si la cadena contiene todas las palabras, false en caso contrario
     */
    public static boolean containsAllWords(String string, String key) {
        String[] words = key.split("\\s+");
        for (String word : words) {
            if (!string.contains(word)) {
                return false;
            }
        }
        return true;
    }
}