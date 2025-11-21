// ════════════════════════════════════════════════════════════════
// AppVoiceManager.java - CON LIMPIEZA GARANTIZADA
// ════════════════════════════════════════════════════════════════
package com.example.irislens.common;

import android.content.Context;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.widget.TextView;

import java.lang.ref.WeakReference;

/**
 * Gestor global de voz con limpieza automática garantizada
 */
public class AppVoiceManager {
    private static final String TAG = "AppVoiceManager";
    private static AppVoiceManager instance;

    private final Context applicationContext;
    private final AccessibilityHelper accessibilityHelper;
    private final Handler mainHandler;
    private TextToSpeechManager ttsManager;
    private boolean isTalkBackActive;
    private boolean isInitialized = false;

    // ✅ Referencia débil para evitar memory leaks
    private WeakReference<TextView> currentTextView;
    private Runnable pendingClearTask;

    private AppVoiceManager(Context context) {
        this.applicationContext = context.getApplicationContext();
        this.accessibilityHelper = new AccessibilityHelper(applicationContext);
        this.mainHandler = new Handler(Looper.getMainLooper());
        this.isTalkBackActive = accessibilityHelper.isTalkBackEnabled();

        Log.d(TAG, "═════════════════════════════════════════");
        Log.d(TAG, "🎤 AppVoiceManager inicializado");
        Log.d(TAG, "TalkBack: " + (isTalkBackActive ? "ACTIVO" : "INACTIVO"));
        Log.d(TAG, "═════════════════════════════════════════");
    }

    public static synchronized AppVoiceManager getInstance(Context context) {
        if (instance == null) {
            instance = new AppVoiceManager(context);
        }
        return instance;
    }

    /**
     * ✅ Habla y mantiene el texto visible hasta terminar
     * CON LIMPIEZA GARANTIZADA
     */
    public void speakAndShow(TextView textView, String message) {
        if (textView == null || message == null) return;

        Log.d(TAG, "═══════════════════════════════════════════════════");
        Log.d(TAG, "📝 speakAndShow() llamado");
        Log.d(TAG, "   Mensaje: \"" + message + "\"");

        // ✅ Cancelar cualquier limpieza pendiente
        cancelPendingClear();

        // ✅ Mostrar texto inmediatamente
        textView.setText(message);
        currentTextView = new WeakReference<>(textView);
        Log.d(TAG, "✅ Texto mostrado en pantalla");

        if (isTalkBackActive) {
            Log.d(TAG, "📢 TalkBack activo - texto permanece visible");
            Log.d(TAG, "═══════════════════════════════════════════════════");
            return;
        }

        // ✅ Inicializar TTS si es necesario
        if (!isInitialized) {
            Log.d(TAG, "🔄 Inicializando TTS...");
            initializeTTS();
        }

        // ✅ Calcular duración ANTES de hablar
        final int duration = calculateSpeechDuration(message);
        Log.d(TAG, "⏱️ Duración estimada: " + duration + "ms");

        // ✅ Verificar si TTS está listo AHORA
        if (ttsManager != null && ttsManager.isReady()) {
            Log.d(TAG, "✅ TTS listo INMEDIATAMENTE, hablando...");
            speakAndScheduleClear(textView, message, duration);
        } else {
            Log.d(TAG, "⏳ TTS no está listo, esperando...");

            // ✅ Verificar cada 200ms hasta que esté listo (máximo 3 segundos)
            checkTTSReadyAndSpeak(textView, message, duration, 0);
        }

        Log.d(TAG, "═══════════════════════════════════════════════════");
    }

    /**
     * ✅ Verifica recursivamente si TTS está listo
     */
    private void checkTTSReadyAndSpeak(TextView textView, String message, int duration, int attempts) {
        if (attempts > 15) { // 15 intentos x 200ms = 3 segundos máximo
            Log.e(TAG, "❌ TTS no se inicializó después de 3 segundos");
            // Limpiar después de 5 segundos como fallback
            scheduleClear(textView, 5000);
            return;
        }

        mainHandler.postDelayed(() -> {
            if (ttsManager != null && ttsManager.isReady()) {
                Log.d(TAG, "✅ TTS listo después de " + (attempts * 200) + "ms");
                speakAndScheduleClear(textView, message, duration);
            } else {
                Log.d(TAG, "⏳ TTS aún no listo, intento " + (attempts + 1) + "/15");
                checkTTSReadyAndSpeak(textView, message, duration, attempts + 1);
            }
        }, 200);
    }

    /**
     * ✅ Habla y programa la limpieza
     */
    private void speakAndScheduleClear(TextView textView, String message, int duration) {
        Log.d(TAG, "🗣️ Comenzando a hablar: \"" + message + "\"");
        ttsManager.speak(message);

        // ✅ Programar limpieza
        scheduleClear(textView, duration);
    }

    /**
     * ✅ Programa la limpieza del texto
     */
    private void scheduleClear(TextView textView, int delay) {
        Log.d(TAG, "⏰ Programando limpieza en " + delay + "ms");

        pendingClearTask = new Runnable() {
            @Override
            public void run() {
                TextView tv = currentTextView != null ? currentTextView.get() : null;
                if (tv != null) {
                    Log.d(TAG, "🧹 LIMPIANDO TEXTO AHORA");
                    tv.setText("");
                    currentTextView = null;
                } else {
                    Log.w(TAG, "⚠️ TextView ya no existe, no se puede limpiar");
                }
                pendingClearTask = null;
            }
        };

        mainHandler.postDelayed(pendingClearTask, delay);
        Log.d(TAG, "✅ Limpieza programada exitosamente");
    }

    /**
     * ✅ Cancela limpieza pendiente
     */
    private void cancelPendingClear() {
        if (pendingClearTask != null) {
            mainHandler.removeCallbacks(pendingClearTask);
            pendingClearTask = null;
            Log.d(TAG, "🚫 Limpieza pendiente cancelada");
        }
    }

    /**
     * Inicializa el TTS
     */
    private void initializeTTS() {
        if (isInitialized) return;

        Log.d(TAG, "✅ Creando TextToSpeechManager");
        ttsManager = new TextToSpeechManager(applicationContext);
        isInitialized = true;
    }

    /**
     * ✅ Versión simple: solo habla
     */
    public void speak(String message) {
        if (isTalkBackActive) {
            Log.d(TAG, "🚫 Mensaje bloqueado (TalkBack activo)");
            return;
        }

        if (!isInitialized) {
            initializeTTS();
        }

        if (ttsManager != null && ttsManager.isReady()) {
            Log.d(TAG, "🗣️ Hablando: \"" + message + "\"");
            ttsManager.speak(message);
        } else {
            Log.w(TAG, "⚠️ TTS no está listo para speak()");
        }
    }

    /**
     * Calcula duración estimada del habla
     * TTS en español es más lento que el habla natural
     */
    private int calculateSpeechDuration(String text) {
        int wordCount = text.split("\\s+").length;

        // ✅ AJUSTADO: TTS español habla a ~120 palabras/minuto (2 palabras/segundo)
        // = 500ms por palabra (más lento que habla natural)
        int baseDuration = (int) ((wordCount / 2.0) * 1000);

        // ✅ Agregar 4 segundos de margen (era 3, ahora 4)
        int totalDuration = baseDuration + 4000;

        Log.d(TAG, "📊 Cálculo duración:");
        Log.d(TAG, "   Palabras: " + wordCount);
        Log.d(TAG, "   Base: " + baseDuration + "ms (120 palabras/min)");
        Log.d(TAG, "   Margen: 4000ms");
        Log.d(TAG, "   Total: " + totalDuration + "ms");

        return totalDuration;
    }

    /**
     * Detiene el TTS y limpia el TextView
     */
    public void stopAndClear(TextView textView) {
        Log.d(TAG, "⏹️ stopAndClear() llamado");

        cancelPendingClear();

        if (ttsManager != null) {
            ttsManager.stop();
        }

        if (textView != null) {
            textView.setText("");
        }

        currentTextView = null;
        Log.d(TAG, "✅ Voz detenida y texto limpiado");
    }

    public void stop() {
        cancelPendingClear();
        if (ttsManager != null) {
            ttsManager.stop();
        }
    }

    public boolean isSpeaking() {
        return ttsManager != null && ttsManager.isSpeaking();
    }

    public boolean isTalkBackActive() {
        return isTalkBackActive;
    }

    public void shutdown() {
        Log.d(TAG, "🔴 Shutdown iniciado");
        cancelPendingClear();

        if (ttsManager != null) {
            ttsManager.shutdown();
            ttsManager = null;
        }

        isInitialized = false;
        currentTextView = null;
        Log.d(TAG, "✅ Shutdown completado");
    }
}