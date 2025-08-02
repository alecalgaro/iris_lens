package com.example.irislens.money.view;

import android.os.Bundle;
import com.example.irislens.R;
import com.example.irislens.common.BaseSwipeActivity;
import com.example.irislens.common.Functionalities;
import android.os.Handler;
import android.os.Looper;
import com.example.irislens.common.TextToSpeechManager;

/**
 * Actividad que realiza el reconocimiento de billetes
 */
public class MoneyRecognitionActivity extends BaseSwipeActivity {

    private TextToSpeechManager ttsManager;

    /**
     * Configura la interfaz de reconocimiento de billetes
     *
     * @param savedInstanceState Estado guardado
     */
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        currentFunctionalityIndex = Functionalities.MONEY;
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_money_recognition);

        /*
        ttsManager = new TextToSpeechManager(this);
        new Handler().postDelayed(() -> {
            ttsManager.speak("Reconocimiento de billetes (próximamamente).");
        }, 500);
        */
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (ttsManager != null) {
            ttsManager.shutdown();
        }
    }

}
