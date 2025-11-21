package com.example.irislens.common;

import android.content.Context;
import android.os.VibrationEffect;
import android.os.Vibrator;
import android.util.Log;
import android.view.View;
import android.view.accessibility.AccessibilityEvent;
import android.view.accessibility.AccessibilityManager;

/**
 * Helper para gestionar interacciones con servicios de accesibilidad como TalkBack
 */
public class AccessibilityHelper {
    private static final String TAG = "ACCESSIBILITY_DEBUG";

    private final Context context;
    private final AccessibilityManager accessibilityManager;
    private final Vibrator vibrator;

    public AccessibilityHelper(Context context) {
        this.context = context;
        this.accessibilityManager = (AccessibilityManager)
                context.getSystemService(Context.ACCESSIBILITY_SERVICE);
        this.vibrator = (Vibrator) context.getSystemService(Context.VIBRATOR_SERVICE);

        Log.d(TAG, "═════════════════════════════════════════");
        Log.d(TAG, "📱 CREANDO AccessibilityHelper");
        Log.d(TAG, "Context: " + context.getClass().getSimpleName());
        boolean talkBackActive = isTalkBackEnabled();
        Log.d(TAG, "🔍 TalkBack activo al crear: " + talkBackActive);
        Log.d(TAG, "═════════════════════════════════════════");
    }

    /**
     * Verifica si TalkBack está activo
     */
    public boolean isTalkBackEnabled() {
        if (accessibilityManager == null) {
            Log.e(TAG, "❌ AccessibilityManager es NULL");
            return false;
        }

        boolean isEnabled = accessibilityManager.isEnabled();
        boolean isTouchExploration = accessibilityManager.isTouchExplorationEnabled();
        boolean result = isEnabled && isTouchExploration;

        Log.d(TAG, "─────────────────────────────────────────");
        Log.d(TAG, "🔍 VERIFICANDO TALKBACK:");
        Log.d(TAG, "  • accessibilityManager.isEnabled(): " + isEnabled);
        Log.d(TAG, "  • isTouchExplorationEnabled(): " + isTouchExploration);
        Log.d(TAG, "  • RESULTADO FINAL: " + result);
        Log.d(TAG, "─────────────────────────────────────────");

        return result;
    }

    /**
     * Configura el contenido para que TalkBack lo lea automáticamente
     * y fuerza el foco de accesibilidad
     * @param view Vista que contiene el contenido
     * @param announcement Texto a anunciar
     */
    public void announceForAccessibility(View view, String announcement) {
        Log.d(TAG, "═════════════════════════════════════════");
        Log.d(TAG, "📢 LLAMADA A announceForAccessibility() - LECTURA AUTOMÁTICA");
        Log.d(TAG, "Texto: \"" + announcement + "\"");

        if (view == null) {
            Log.e(TAG, "❌ View es NULL - ABORTANDO");
            Log.d(TAG, "═════════════════════════════════════════");
            return;
        }

        if (announcement == null || announcement.trim().isEmpty()) {
            Log.w(TAG, "⚠️ Announcement está vacío - ABORTANDO");
            Log.d(TAG, "═════════════════════════════════════════");
            return;
        }

        Log.d(TAG, "✅ Configurando contentDescription");
        view.setContentDescription(announcement);

        Log.d(TAG, "✅ Marcando como importante para accesibilidad");
        view.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_YES);

        Log.d(TAG, "✅ Enviando evento de accesibilidad (LECTURA AUTOMÁTICA)");
        view.sendAccessibilityEvent(AccessibilityEvent.TYPE_VIEW_FOCUSED);

        Log.d(TAG, "✅ Forzando foco en la vista");
        view.requestFocus();

        Log.d(TAG, "═════════════════════════════════════════");
    }

    /**
     * Limpia el contentDescription de una vista
     */
    public void clearAccessibilityDescription(View view) {
        Log.d(TAG, "🧹 Limpiando descripción de accesibilidad");
        if (view != null) {
            view.setContentDescription("");
            view.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_AUTO);
        }
    }
}