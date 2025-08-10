package com.example.irislens.medicine.model;

import android.provider.BaseColumns;

/**
 * Clase que define la estructura de la base de datos de medicamentos.
 * Contiene las constantes que representan los nombres de las tablas y columnas
 * de la base de datos.
 * */
public final class MedicineContract {
    private MedicineContract() {}

    public static class MedicineEntry implements BaseColumns {
        public static final String TABLE_NAME = "medicamento";
        public static final String COLUMN_NAME = "nombre";
        public static final String COLUMN_DESCRIPTION = "descripcion";
    }

    public static class ActiveIngredient implements BaseColumns {
        public static final String TABLE_NAME = "principio_activo";
        public static final String COLUMN_NAME = "nombre";
    }
}