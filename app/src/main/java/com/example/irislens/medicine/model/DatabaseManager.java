package com.example.irislens.medicine.model;

import org.json.JSONObject;

public class DatabaseManager {

    private JSONObject medicinesDB;
    private JSONObject drugsDB;

    // Constructor para inicializar las bases de datos de medicamentos y drogas
    public DatabaseManager() {
        // Base de datos de nombres de medicamentos
        medicinesDB = Database.initializeMedicinesDB();

        // Base de datos de nombres de drogas
        drugsDB = Database.initializeDrugsDB();
    }

    public JSONObject getMedicinesDB() {
        return medicinesDB;
    }

    public JSONObject getDrugsDB() {
        return drugsDB;
    }
}

