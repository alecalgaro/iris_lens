// ════════════════════════════════════════════════════════════════
// 3. WelcomeActivity.java - SIMPLIFICADO
// ════════════════════════════════════════════════════════════════
package com.example.irislens.common;

import android.os.Bundle;
import android.os.Handler;
import android.widget.TextView;

import com.example.irislens.R;

public class WelcomeActivity extends BaseSwipeActivity {

    private TextView tvWelcome;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_welcome);
        currentFunctionalityIndex = Functionalities.WELCOME;
        tvWelcome = findViewById(R.id.tvWelcome);

        // ✅ USO SUPER SIMPLE - Solo llamar a voiceManager.speak()
        // El gestor decide automáticamente si hablar o no según TalkBack
        new Handler().postDelayed(() -> {
            voiceManager.speak("Iris Lens le da la bienvenida. " +
                    "Deslice hacia la izquierda o derecha para cambiar de funcionalidad. " +
                    "Use doble toque para detener la voz.");
        }, 2000);
    }

    @Override
    protected void onDoubleTapDetected() {
        // ✅ Detener voz si está hablando
        if (voiceManager != null) {
            voiceManager.stopAndClear(tvWelcome);
        }
    }

    // ✅ NO necesitas shutdown aquí - el gestor global se encarga
    @Override
    protected void onDestroy() {
        super.onDestroy();
    }
}