package com.example.irislens.medicine.model;

import android.database.sqlite.SQLiteDatabase;
import android.content.Context;

public class DatabaseManager {
    private final MedicineDbHelper dbHelper;

    // Constructor para inicializar las bases de datos de medicamentos y drogas
    public DatabaseManager(Context context) {
        dbHelper = new MedicineDbHelper(context);
    }

    public SQLiteDatabase getReadableDatabase() {
        return dbHelper.getReadableDatabase();
    }
}