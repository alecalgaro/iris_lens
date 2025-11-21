// ════════════════════════════════════════════════════════════════
// 2. BaseSwipeActivity.java - ACTUALIZADO
// ════════════════════════════════════════════════════════════════
package com.example.irislens.common;

import android.os.Bundle;
import android.view.GestureDetector;
import android.view.MotionEvent;

import org.opencv.android.CameraActivity;

/**
 * Actividad base con gestor de voz integrado
 */
public abstract class BaseSwipeActivity extends CameraActivity {

    private static final int SWIPE_THRESHOLD = 100;
    private static final int SWIPE_VELOCITY_THRESHOLD = 100;

    protected GestureDetector gestureDetector;
    protected int currentFunctionalityIndex = 0;

    // ✅ Gestor de voz compartido por todas las actividades
    protected AppVoiceManager voiceManager;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        // ✅ Obtener gestor de voz global (se inicializa solo la primera vez)
        voiceManager = AppVoiceManager.getInstance(this);

        gestureDetector = new GestureDetector(this, new GestureDetector.SimpleOnGestureListener() {
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

            @Override
            public boolean onDoubleTap(MotionEvent e) {
                onDoubleTapDetected();
                return true;
            }
        });
    }

    @Override
    public boolean dispatchTouchEvent(MotionEvent ev) {
        boolean handled = gestureDetector.onTouchEvent(ev);
        if (handled) return true;
        return super.dispatchTouchEvent(ev);
    }

    protected void onSwipeLeft() {
        goToNext();
    }

    protected void onSwipeRight() {
        goToPrevious();
    }

    private void goToNext() {
        int nextIndex = Functionalities.getNextIndex(currentFunctionalityIndex);
        Functionalities.launch(this, nextIndex);
        finish();
    }

    private void goToPrevious() {
        int prevIndex = Functionalities.getPreviousIndex(currentFunctionalityIndex);
        Functionalities.launch(this, prevIndex);
        finish();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
    }

    protected void onDoubleTapDetected() {
        // Las actividades hijas pueden sobrescribir esto
    }
}