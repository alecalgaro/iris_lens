package com.example.irislens.common;

import android.os.Bundle;
import android.os.Handler;
import android.widget.TextView;

import com.example.irislens.R;

public class WelcomeActivity extends BaseSwipeActivity {

    private TextView tvWelcome;
    private boolean mensajeBienvenidaReproducido = false;
    private Handler handler = new Handler();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_welcome);
        currentFunctionalityIndex = Functionalities.WELCOME;
        tvWelcome = findViewById(R.id.tvWelcome);

        AppVoiceManager.getInstance(this).initializeTTS();

        // Detener cualquier audio residual de la actividad anterior
        if (voiceManager != null) {
            voiceManager.stop();
        }

        welcome_message();
    }

    /**
     * Metodo que espera a que el TTS este inicializado para reproducir el mensaje de bienvenida.
     */
    private void welcome_message() {
        handler.postDelayed(() -> {
            if (!mensajeBienvenidaReproducido) {
                if (voiceManager != null && voiceManager.isTTSInitialized()) {
                    voiceManager.speak("Iris Lens le da la bienvenida. " +
                            "Deslice hacia la izquierda o derecha para cambiar de funcionalidad. " +
                            "Use doble toque para detener la voz.");
                    mensajeBienvenidaReproducido = true;
                } else {
                    welcome_message();
                }
            }
        }, 600);
    }

    @Override
    protected void onDoubleTapDetected() {
        // Detener voz si esta hablando
        if (voiceManager != null) {
            voiceManager.stop();
        }
    }

    @Override
    protected void onDestroy() {
        handler.removeCallbacksAndMessages(null); // Cancelar todos los callbacks pendientes
        super.onDestroy();
    }
}