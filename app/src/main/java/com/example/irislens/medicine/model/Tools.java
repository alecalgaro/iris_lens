package com.example.irislens.medicine.model;

import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.util.Log;
import android.widget.Toast;

import androidx.core.util.Pair;

import org.apache.commons.text.similarity.LevenshteinDistance;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.Iterator;
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
        String cleanedText = text.replaceAll("[^a-zA-Z0-9]", " ");

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

        // Si no encuentra una coincidencia en los medicamentos, busca en los principios activos
        if (matches.isEmpty()) {
            // Se divide la cadena que devuelve tesseract en palabras individuales
            String[] words = stringTesseract.split("\\s+");
            cursor = db.query("principio_activo", new String[]{"nombre"}, null, null, null, null, null);
            while (cursor.moveToNext()) {
                String nombreDroga = cursor.getString(cursor.getColumnIndexOrThrow("nombre"));
                // Compara cada palabra con las palabras en la base de datos de principios activos
                for (String word : words) {
                    // Calcula la distancia de Levenshtein y lo convierte a un valor de similitud
                    double similarity = 1.0 - ((double) levenshteinDistance.apply(word.toLowerCase(), nombreDroga.toLowerCase()) / Math.max(word.length(), nombreDroga.length()));
                    if (similarity >= SIMILARITY_THRESHOLD) {
                        Pair<String, String> potentialMatch = new Pair<>(nombreDroga, nombreDroga);
                        // Verificar si la coincidencia ya esta en la lista para no agregar duplicados
                        if (!matches.contains(potentialMatch)) {
                            matches.add(potentialMatch);
                        }
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