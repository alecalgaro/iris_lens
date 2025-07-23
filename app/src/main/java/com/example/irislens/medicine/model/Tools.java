package com.example.irislens.medicine.model;

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
        //String cleanedText = text.replaceAll("[^a-zA-Z0-9]", " ");
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
        return result.toString().trim();
    }

    /**
     * Busca similitudes entre una cadena de texto y las palabras en los JSON de medicamentos y principios activos
     *
     * @param stringTesseract La cadena de texto a comparar
     * @param medicines       El JSON de medicamentos
     * @param drugs           El JSON de drogas
     * @return Una lista de pares de palabras que coinciden
     */
    public static List<Pair<String, String>> searchSimilarity(String stringTesseract, JSONObject medicines, JSONObject drugs) {
        List<Pair<String, String>> matches = new ArrayList<>();

        // Convierte la cadena de texto a minúsculas para la comparacion
        String lowerCaseString = stringTesseract.toLowerCase();

        LevenshteinDistance levenshteinDistance = new LevenshteinDistance();

        // Primero busca en la base de datos de medicamentos
        Iterator<String> keys = medicines.keys();
        while (keys.hasNext()) {
            String key = keys.next();
            try {
                String value = medicines.getString(key);
                if (containsAllWords(lowerCaseString, key.toLowerCase())) {
                    Pair<String, String> potentialMatch = new Pair<>(key, value);
                    matches.add(potentialMatch);
                }
            } catch (JSONException e) {
                e.printStackTrace();
            }
        }

        // Si no encuentra una coincidencia en los medicamentos, busca en los principios activos
        if (matches.isEmpty()) {
            // Se divide la cadena que devuelve tesseract en palabras individuales
            String[] words = stringTesseract.split("\\s+");

            // Compara cada palabra con las palabras en la base de datos de principios activos
            for (String word : words) {
                keys = drugs.keys();
                while (keys.hasNext()) {
                    String key = keys.next();
                    try {
                        String value = drugs.getString(key);
                        // Calcula la distancia de Levenshtein y lo convierte a un valor de similitud
                        double similarity = 1.0 - ((double) levenshteinDistance.apply(word.toLowerCase(), key.toLowerCase()) / Math.max(word.length(), key.length()));
                        if (similarity >= SIMILARITY_THRESHOLD) {
                            Pair<String, String> potentialMatch = new Pair<>(key, value);
                            // Verificar si la coincidencia ya esta en la lista para no agregar duplicados
                            if (!matches.contains(potentialMatch)) {
                                matches.add(potentialMatch);
                            }
                        }
                    } catch (JSONException e) {
                        e.printStackTrace();
                    }
                }
            }
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