package com.example.irislens.medicine.model;

import android.database.sqlite.SQLiteDatabase;
import android.content.Context;

/**
 * Clase para manejar la base de datos de medicamentos y principios activos.
 */
public class DatabaseManager {
    private final MedicineDbHelper dbHelper;

    public DatabaseManager(Context context) {
        dbHelper = new MedicineDbHelper(context);
    }

    public SQLiteDatabase getReadableDatabase() {
        return dbHelper.getReadableDatabase();
    }
}