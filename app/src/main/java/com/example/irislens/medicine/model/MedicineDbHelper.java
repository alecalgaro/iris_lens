package com.example.irislens.medicine.model;

import android.content.Context;
import android.content.res.AssetManager;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;

public class MedicineDbHelper extends SQLiteOpenHelper {
    private static final String DATABASE_NAME = "medicamentos_db";
    private static final int DATABASE_VERSION = 2; // subido de 1 a 2 para agregar firestore_id
    private final Context mContext;

    public MedicineDbHelper(Context context) {
        super(context, DATABASE_NAME, null, DATABASE_VERSION);
        this.mContext = context;
    }

    @Override
    public void onCreate(SQLiteDatabase db) {
        // firestore_id: identificador estable de Firestore, permite detectar cambios de nombre
        String SQL_CREATE_MEDICINE =
                "CREATE TABLE " + MedicineContract.MedicineEntry.TABLE_NAME + " (" +
                        MedicineContract.MedicineEntry._ID + " INTEGER PRIMARY KEY AUTOINCREMENT, " +
                        MedicineContract.MedicineEntry.COLUMN_FIRESTORE_ID + " TEXT UNIQUE, " +
                        MedicineContract.MedicineEntry.COLUMN_NAME + " TEXT NOT NULL, " +
                        MedicineContract.MedicineEntry.COLUMN_DESCRIPTION + " TEXT);";

        String SQL_CREATE_DRUG =
                "CREATE TABLE " + MedicineContract.ActiveIngredient.TABLE_NAME + " (" +
                        MedicineContract.ActiveIngredient._ID + " INTEGER PRIMARY KEY AUTOINCREMENT, " +
                        MedicineContract.ActiveIngredient.COLUMN_FIRESTORE_ID + " TEXT UNIQUE, " +
                        MedicineContract.ActiveIngredient.COLUMN_NAME + " TEXT NOT NULL);";

        db.execSQL(SQL_CREATE_MEDICINE);
        db.execSQL(SQL_CREATE_DRUG);

        // Cargar medicamentos desde "medicamentos.json" (sin firestore_id, seed local)
        try {
            AssetManager assetManager = mContext.getAssets();
            InputStream is = assetManager.open("medicamentos.json");
            BufferedReader reader = new BufferedReader(new InputStreamReader(is));
            StringBuilder jsonBuilder = new StringBuilder();
            String line;
            while ((line = reader.readLine()) != null) jsonBuilder.append(line);
            reader.close();
            is.close();

            JSONArray medicamentosArray = new JSONArray(jsonBuilder.toString());
            for (int i = 0; i < medicamentosArray.length(); i++) {
                JSONObject medicamento = medicamentosArray.getJSONObject(i);
                String nombre = medicamento.getString("nombre");
                String descripcion = medicamento.getString("descripcion");
                db.execSQL("INSERT INTO " + MedicineContract.MedicineEntry.TABLE_NAME +
                                " (" + MedicineContract.MedicineEntry.COLUMN_NAME + ", " +
                                MedicineContract.MedicineEntry.COLUMN_DESCRIPTION + ") VALUES (?, ?)",
                        new Object[]{nombre, descripcion});
            }
        } catch (IOException | JSONException e) {
            e.printStackTrace();
        }

        // Cargar principios activos desde "principios_activos.json" (sin firestore_id, seed local)
        try {
            AssetManager assetManager = mContext.getAssets();
            InputStream is = assetManager.open("principios_activos.json");
            BufferedReader reader = new BufferedReader(new InputStreamReader(is));
            StringBuilder jsonBuilder = new StringBuilder();
            String line;
            while ((line = reader.readLine()) != null) jsonBuilder.append(line);
            reader.close();
            is.close();

            JSONArray principiosArray = new JSONArray(jsonBuilder.toString());
            for (int i = 0; i < principiosArray.length(); i++) {
                JSONObject principio = principiosArray.getJSONObject(i);
                String nombre = principio.getString("nombre");
                db.execSQL("INSERT INTO " + MedicineContract.ActiveIngredient.TABLE_NAME +
                                " (" + MedicineContract.ActiveIngredient.COLUMN_NAME + ") VALUES (?)",
                        new Object[]{nombre});
            }
        } catch (IOException | JSONException e) {
            e.printStackTrace();
        }
    }

    @Override
    public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) {
        db.execSQL("DROP TABLE IF EXISTS " + MedicineContract.MedicineEntry.TABLE_NAME);
        db.execSQL("DROP TABLE IF EXISTS " + MedicineContract.ActiveIngredient.TABLE_NAME);
        onCreate(db);
    }
}