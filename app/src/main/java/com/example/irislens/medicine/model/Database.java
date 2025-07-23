package com.example.irislens.medicine.model;

import org.json.JSONException;
import org.json.JSONObject;

public class Database {

    // Inicializar la base de datos de medicamentos
    public static JSONObject initializeMedicinesDB() {
        JSONObject medicinesDB = new JSONObject();
        try {
            medicinesDB.put("Decidex compuesto", "Decidex Compuesto. Contiene clorfenamina, pseudoefedrina y paracetamol. Se utiliza en tratamiento sintomático del cuadro gripal que se acompañe de fiebre o dolor y congestión nasal, sinusal u ocular.");
            medicinesDB.put("Esoprazol", "Esoprazol. Contiene Esomeprazol. Indicado para fastritis agudas y crónicas.");
            medicinesDB.put("Macril", "Macril. Contiene Betametasona, Gentamicina y Miconazol. Indicado para dermatopatías inflamatorias complicadas por infección bacteriana, micótica o mixta.");
            medicinesDB.put("Ernex", "Ernex. Contiene Bencidamina. Indicado para el tratamiento de inflamaciones de la garganta y de la boca.");
            medicinesDB.put("Buscapina", "Buscapina.");
            medicinesDB.put("Adermicina", "Adermicina.");
            medicinesDB.put("dermaglós", "dermaglós.");
        } catch (JSONException e) {
            e.printStackTrace();
        }
        return medicinesDB;
    }

    // Inicializar la base de datos de drogas
    public static JSONObject initializeDrugsDB() {
        JSONObject drugsDB = new JSONObject();
        try {
            drugsDB.put("Clorfenamina", "Clorfenamina");
            drugsDB.put("Pseudoefedrina", "Pseudoefedrina");
            drugsDB.put("Paracetamol", "Paracetamol");
            drugsDB.put("Esomeprazol", "Esomeprasol");
            drugsDB.put("Betametasona", "Betametasona");
            drugsDB.put("Gentamicina", "Gentamicina");
            drugsDB.put("Miconazol", "Miconazol");
            drugsDB.put("Bencidamina", "Bencidamina");
            drugsDB.put("hidrófila", "Hidrófila");
            drugsDB.put("estéril", "estéril");
        } catch (JSONException e) {
            e.printStackTrace();
        }
        return drugsDB;
    }
}