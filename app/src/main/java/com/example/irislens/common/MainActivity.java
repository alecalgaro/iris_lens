package com.example.irislens.common;

import android.os.Bundle;
import androidx.appcompat.app.AppCompatActivity;

/**
 * Actividad principal.
 * Redirige automáticamente a la funcionalidad de reconocimiento de medicamentos
 */
public class MainActivity extends AppCompatActivity {
    /**
     * Lanza la primera funcionalidad y cierra esta pantalla
     *
     * @param savedInstanceState Estado guardado
     */
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        // Para agregar la pantalla de bienvenida:
        // setContentView(R.layout.activity_main);
        // Para redireccinar directamente al modo 0
        Functionalities.launch(this, Functionalities.MEDICINE); // empieza con medicamentos
        finish();
    }
}