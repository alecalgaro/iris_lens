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
    /**
     * Clase que extiende SQLiteOpenHelper para manejar la creacion y actualizacion
     * de la base de datos de medicamentos.
     * Utiliza MedicineContract para definir la estructura de las tablas y crea los datos iniciales
     * a partir de un archivo JSON almacenado en los assets de la aplicacion.
     */
    private static final String DATABASE_NAME = "medicamentos_db";
    private static final int DATABASE_VERSION = 1;
    private final Context mContext;

    public MedicineDbHelper(Context context) {
        super(context, DATABASE_NAME, null, DATABASE_VERSION);
        this.mContext = context;
    }

    @Override
    public void onCreate(SQLiteDatabase db) {
        String SQL_CREATE_MEDICINE =
                "CREATE TABLE " + MedicineContract.MedicineEntry.TABLE_NAME + " (" +
                        MedicineContract.MedicineEntry._ID + " INTEGER PRIMARY KEY AUTOINCREMENT, " +
                        MedicineContract.MedicineEntry.COLUMN_NAME + " TEXT NOT NULL UNIQUE, " +
                        MedicineContract.MedicineEntry.COLUMN_DESCRIPTION + " TEXT);";

        String SQL_CREATE_DRUG =
                "CREATE TABLE " + MedicineContract.DrugEntry.TABLE_NAME + " (" +
                        MedicineContract.DrugEntry._ID + " INTEGER PRIMARY KEY AUTOINCREMENT, " +
                        MedicineContract.DrugEntry.COLUMN_NAME + " TEXT NOT NULL UNIQUE);";

        db.execSQL(SQL_CREATE_MEDICINE);
        db.execSQL(SQL_CREATE_DRUG);

        // ---------- Creacion de datos iniciales en la BD a partir de archivos JSON ----------

        // Cargar medicamentos en la base de datos desde "medicamentos.json"
        try {
            AssetManager assetManager = mContext.getAssets();
            InputStream is = assetManager.open("medicamentos.json");
            BufferedReader reader = new BufferedReader(new InputStreamReader(is));
            StringBuilder jsonBuilder = new StringBuilder();
            String line;
            while ((line = reader.readLine()) != null) {
                jsonBuilder.append(line);
            }
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

        // Cargar principios activos en la base de datos desde "principios_activos.json"
        try {
            AssetManager assetManager = mContext.getAssets();
            InputStream is = assetManager.open("principios_activos.json");
            BufferedReader reader = new BufferedReader(new InputStreamReader(is));
            StringBuilder jsonBuilder = new StringBuilder();
            String line;
            while ((line = reader.readLine()) != null) {
                jsonBuilder.append(line);
            }
            reader.close();
            is.close();

            JSONArray principiosArray = new JSONArray(jsonBuilder.toString());
            for (int i = 0; i < principiosArray.length(); i++) {
                JSONObject principio = principiosArray.getJSONObject(i);
                String nombre = principio.getString("nombre");
                db.execSQL("INSERT INTO " + MedicineContract.DrugEntry.TABLE_NAME +
                                " (" + MedicineContract.DrugEntry.COLUMN_NAME + ") VALUES (?)",
                        new Object[]{nombre});
            }
        } catch (IOException | JSONException e) {
            e.printStackTrace();
        }

    }

    @Override
    public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) {
        db.execSQL("DROP TABLE IF EXISTS " + MedicineContract.MedicineEntry.TABLE_NAME);
        db.execSQL("DROP TABLE IF EXISTS " + MedicineContract.DrugEntry.TABLE_NAME);
        onCreate(db);
    }
}