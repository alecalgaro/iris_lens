package com.example.irislens.common;

import android.Manifest;
import android.content.Context;
import android.content.pm.PackageManager;
import android.speech.tts.TextToSpeech;
import android.util.Log;
import android.widget.Toast;

import java.util.Locale;

public class TextToSpeechManager {

    private TextToSpeech tts;
    private boolean isReady = false;

    // Constructor original con callback
    public TextToSpeechManager(Context context, Runnable onReady) {
        tts = new TextToSpeech(context, status -> {
            if (status == TextToSpeech.SUCCESS) {
                int result = tts.setLanguage(new Locale("es", "ES"));
                isReady = result != TextToSpeech.LANG_MISSING_DATA && result != TextToSpeech.LANG_NOT_SUPPORTED;
                if (!isReady) {
                    Log.e("TTS", "Idioma no soportado");
                } else {
                    if (context.checkSelfPermission(Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED) {
                        String textToSpeak = "Debe conceder el permiso para acceder a la cámara";
                        tts.speak(textToSpeak, TextToSpeech.QUEUE_FLUSH, null, null);
                    }
                }
                if (isReady && onReady != null) onReady.run();
            } else {
                Toast.makeText(context, "Debe activar un motor de Text-to-Speech para continuar", Toast.LENGTH_LONG).show();
                Log.e("TTS", "Inicialización fallida");
            }
        });
    }

    // Constructor adicional para compatibilidad en caso de que no se necesite callback
    public TextToSpeechManager(Context context) {
        this(context, null);
    }

    /**
     * Metodo para verificar si el TextToSpeech esta listo para usarse
     * @return true si esta listo, false en caso contrario
     */
    public boolean isReady() {
        return isReady;
    }

    /**
     * Metodo para reproducir un audio
     * @param text texto a reproducir
     */
    public void speak(String text) {
        if (isReady) {
            tts.speak(text, TextToSpeech.QUEUE_FLUSH, null, null);
        }
    }

    /**
     * Metodo para detener la reproduccion de un audio
     */
    public void stop(){
        tts.stop();
    }

    /**
     * Metodo para saber si se esta reproduciendo un audio
     */
    public boolean isSpeaking() {
        return tts.isSpeaking();
    }

    /**
     * Metodo para liberar los recursos del TextToSpeech
     */
    public void shutdown() {
        if (tts != null) {
            tts.stop();
            tts.shutdown();
        }
    }
}

