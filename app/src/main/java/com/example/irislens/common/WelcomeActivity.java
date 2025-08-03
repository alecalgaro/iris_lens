package com.example.irislens.common;

import android.os.Bundle;
import android.os.Handler;
import android.view.MotionEvent;

import com.example.irislens.R;

public class WelcomeActivity extends BaseSwipeActivity {

    private TextToSpeechManager ttsManager;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_welcome);
        currentFunctionalityIndex = Functionalities.WELCOME; // indice para el swipe

        ttsManager = new TextToSpeechManager(this);
        // Espera breve para asegurar que TTS este listo
        new Handler().postDelayed(() -> {
            ttsManager.speak("Iris Lens le da la bienvenida. " +
                    "Deslice el dedo hacia la izquierda o derecha para cambiar de funcionalidad. " +
                    "Use doble toque para detener la voz.");
        }, 500);
    }

    @Override
    protected void onDoubleTapDetected() {
        if (ttsManager != null) {
            ttsManager.stop();
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (ttsManager != null) {
            ttsManager.shutdown();
        }
    }
}
