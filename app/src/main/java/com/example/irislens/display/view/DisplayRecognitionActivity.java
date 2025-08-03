package com.example.irislens.display.view;

import android.os.Bundle;
import com.example.irislens.R;
import com.example.irislens.common.BaseSwipeActivity;
import com.example.irislens.common.Functionalities;
import android.os.Handler;
import android.os.Looper;
import com.example.irislens.common.TextToSpeechManager;


/**
 * Actividad que realiza el reconocimiento de displays
 */
public class DisplayRecognitionActivity extends BaseSwipeActivity {

    // Encargado de sintetizar voz a partir de texto
    private TextToSpeechManager ttsManager;

    /**
     * Configura la interfaz de reconocimiento de displays
     *
     * @param savedInstanceState Estado guardado
     */
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_display_recognition);
        currentFunctionalityIndex = Functionalities.DISPLAY; // indice para el swipe de esta funcionalidad

        // Mensaje de voz sobre la funcionalidad
        ttsManager = new TextToSpeechManager(this);
        // Espera breve para asegurar que TTS este listo
        new Handler().postDelayed(() -> {
            ttsManager.speak("Reconocimiento de displays (próximamente)");
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
