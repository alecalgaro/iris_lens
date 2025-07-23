package com.example.irislens.common;

import android.content.Context;
import android.content.Intent;
import android.util.Log;

import java.util.Arrays;
import java.util.List;

import com.example.irislens.medicine.view.MedicineRecognitionActivity;
import com.example.irislens.money.view.MoneyRecognitionActivity;
import com.example.irislens.display.view.DisplayRecognitionActivity;

/**
 * Clase que gestiona las funcionalidades disponibles de la aplicación.
 * Permite lanzar actividades, obtener nombres descriptivos y navegar entre funcionalidades
 */
public class Functionalities {

    // Índice para la funcionalidad de reconocimiento de medicamentos, billetes y displays
    public static final int MEDICINE = 0;
    public static final int MONEY = 1;
    public static final int DISPLAY = 2;

    // Tag para log
    private static final String TAG = "Functionalities";

    // Lista de nombres correspondientes a las funcionalidades
    private static final String[] NAMES = {
            "Reconocimiento de medicamentos",
            "Reconocimiento de billetes",
            "Reconocimiento de displays"
    };

    // Lista de clases correspondientes a las funcionalidades
    public static final List<Class<?>> FUNCTIONALITIES = Arrays.asList(
            MedicineRecognitionActivity.class,
            MoneyRecognitionActivity.class,
            DisplayRecognitionActivity.class
    );

    /**
     * Devuelve el índice de la siguiente funcionalidad, de forma circular
     *
     * @param current Índice actual
     * @return Índice siguiente
     */
    public static int getNextIndex(int current) {
        return (current + 1) % FUNCTIONALITIES.size();
    }

    /**
     * Devuelve el índice de la funcionalidad anterior, de forma circular
     *
     * @param current Índice actual
     * @return Índice anterior
     */
    public static int getPreviousIndex(int current) {
        return (current - 1 + FUNCTIONALITIES.size()) % FUNCTIONALITIES.size();
    }

    /**
     * Lanza la actividad correspondiente al índice indicado
     *
     * @param context Contexto actual desde el cual se lanza
     * @param index Índice de la funcionalidad a lanzar
     */
    public static void launch(Context context, int index) {
        if (index < 0 || index >= FUNCTIONALITIES.size()) {
            Log.e(TAG, "Índice fuera de rango: " + index);
            return;
        }

        Class<?> activityClass = FUNCTIONALITIES.get(index);
        Log.d(TAG, "Lanzando actividad: " + activityClass.getSimpleName());

        try {
            Intent intent = new Intent(context, activityClass);
            context.startActivity(intent);
        } catch (Exception e) {
            Log.e(TAG, "Error al lanzar la actividad: " + activityClass.getSimpleName(), e);
        }
    }

    /**
     * Devuelve el nombre descriptivo de una funcionalidad según su índice
     *
     * @param index Índice de la funcionalidad
     * @return Nombre descriptivo
     */
    public static String getName(int index) {
        if (index >= 0 && index < NAMES.length) {
            return NAMES[index];
        }
        return "Funcionalidad";
    }
}
