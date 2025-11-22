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

        // Inicializar el TTS
        AppVoiceManager.getInstance(this).initializeTTS();

        // Llama al metodo que espera a que el TTS este inicializado
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
                    welcome_message(); // Vuelve a consultar en 300 ms
                }
            }
        }, 300);
    }

    @Override
    protected void onDoubleTapDetected() {
        // Detener voz si esta hablando
        if (voiceManager != null) {
            voiceManager.stopAndClear(tvWelcome);
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
    }
}