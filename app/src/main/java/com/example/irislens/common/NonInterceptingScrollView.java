package com.example.irislens.common;

import android.content.Context;
import android.util.AttributeSet;
import android.view.MotionEvent;
import android.widget.ScrollView;

/**
 * ScrollView personalizado que no intercepta eventos de toque.
 * Útil para permitir que el swipe sea detectado por la actividad
 */
public class NonInterceptingScrollView extends ScrollView {

    public NonInterceptingScrollView(Context context) {
        super(context);
    }

    public NonInterceptingScrollView(Context context, AttributeSet attrs) {
        super(context, attrs);
    }

    /**
     * Siempre devuelve false para no interceptar eventos de toque.
     *
     * @param ev Evento táctil.
     * @return false
     */
    @Override
    public boolean onInterceptTouchEvent(MotionEvent ev) {
        // No intercepta, deja que la actividad lo maneje
        return false;
    }
}
