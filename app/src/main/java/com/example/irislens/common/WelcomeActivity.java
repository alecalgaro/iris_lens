package com.example.irislens.common;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.speech.tts.TextToSpeech;

import com.example.irislens.R;

public class WelcomeActivity extends Activity {

    private TextToSpeechManager ttsManager;
    private boolean alreadyNavigated = false; // Para evitar que se llame dos veces

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_welcome);

        //ttsManager = new TextToSpeechManager(this);

        //ttsManager.speak("Iris Lens te da la bienvenida. Desliza hacia la izquierda o derecha para cambiar de funcionalidad. Usa doble toque para detener la voz.");

        new Handler().postDelayed(() -> {
            if (!alreadyNavigated) {
                alreadyNavigated = true;
                //ttsManager.shutdown();
                Functionalities.launch(WelcomeActivity.this, Functionalities.MEDICINE);
                finish();
            }
        }, 4000);
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (ttsManager != null) {
            ttsManager.shutdown();
        }
    }
}
