package com.example.irislens.common;

import android.os.Bundle;
import android.view.GestureDetector;
import android.view.MotionEvent;
import android.view.View;

import com.example.irislens.R;

import org.opencv.android.CameraActivity;

/**
 * Actividad base que detecta gestos de deslizamiento (swipe) hacia izquierda y derecha
 * para cambiar entre funcionalidades
 */
public abstract class BaseSwipeActivity extends CameraActivity {

    // Distancia minima en pixeles que debe recorrer el dedo horizontalmente
    // para que el gesto sea considerado un "swipe" (deslizamiento)
    private static final int SWIPE_THRESHOLD = 100;

    // Velocidad minima (en píxeles/segundo) que debe alcanzar el gesto para
    // ser reconocido como un "swipe"
    private static final int SWIPE_VELOCITY_THRESHOLD = 100;

    // Detecta gestos del usuario
    protected GestureDetector gestureDetector;

    // Indice actual de la funcionalidad (debe establecerse en cada actividad que herede de esta clase)
    protected int currentFunctionalityIndex = 0; // se sobrescribe por cada actividad

    /**
     * Inicializa el detector de gestos
     *
     * @param savedInstanceState Estado anterior, si existe
     */
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        // Inicializa el GestureDetector con soporte para swipe y doble tap
        gestureDetector = new GestureDetector(this, new GestureDetector.SimpleOnGestureListener() {

            // Detecta swipe horizontal
            @Override
            public boolean onFling(MotionEvent e1, MotionEvent e2, float velocityX, float velocityY) {
                float diffX = e2.getX() - e1.getX();
                float diffY = e2.getY() - e1.getY();
                if (Math.abs(diffX) > Math.abs(diffY)) {
                    if (Math.abs(diffX) > SWIPE_THRESHOLD && Math.abs(velocityX) > SWIPE_VELOCITY_THRESHOLD) {
                        if (diffX > 0) {
                            onSwipeRight();
                        } else {
                            onSwipeLeft();
                        }
                        return true;
                    }
                }
                return false;
            }

            // Detecta doble tap en pantalla (util, por ejemplo, para pausar un audio)
            @Override
            public boolean onDoubleTap(MotionEvent e) {
                onDoubleTapDetected();
                return true;
            }
        });
    }

    /**
     * Procesa eventos de toque, incluyendo gestos de swipe
     *
     * @param ev Evento tactil
     * @return true si el evento fue manejado
     */
    @Override
    public boolean dispatchTouchEvent(MotionEvent ev) {
        boolean handled = gestureDetector.onTouchEvent(ev);
        if (handled) return true; // el gesto fue procesado
        return super.dispatchTouchEvent(ev);
    }

    /**
     * Configura una capa tactil transparente que detecta gestos de deslizamiento (swipe).
     * Esta capa se coloca sobre la interfaz para capturar eventos tactiles y
     * delegarlos al detector de gestos.
     * Esta capa debe estar definida en el layout de la actividad
     */
    protected void setupSwipeLayer() {
        View touchLayer = findViewById(R.id.touch_layer);
        if (touchLayer != null) {
            touchLayer.setOnTouchListener((v, event) -> gestureDetector.onTouchEvent(event));
        }
    }

    /**
     * Maneja el gesto de swipe hacia la izquierda
     * Lanza la siguiente funcionalidad
     */
    protected void onSwipeLeft() {
        goToNext();
    }

    /**
     * Maneja el gesto de swipe hacia la derecha
     * Lanza la funcionalidad anterior
     */
    protected void onSwipeRight() {
        goToPrevious();
    }

    /**
     * Lanza la funcionalidad siguiente
     */
    private void goToNext() {
        int nextIndex = Functionalities.getNextIndex(currentFunctionalityIndex);
        Functionalities.launch(this, nextIndex);
        finish(); // Cierra la actividad actual
    }

    /**
     * Lanza la funcionalidad anterior
     */
    private void goToPrevious() {
        int prevIndex = Functionalities.getPreviousIndex(currentFunctionalityIndex);
        Functionalities.launch(this, prevIndex);
        finish(); // Cierra la actividad actual
    }

    /**
     * Finaliza esta actividad y libera recursos
     */
    @Override
    protected void onDestroy() {
        super.onDestroy();
    }

    protected void onDoubleTapDetected() {
        // No hace nada por defecto, se redefine en las
        // actividades hijas (porque tiene que acceder a cada presenter)
    }
}