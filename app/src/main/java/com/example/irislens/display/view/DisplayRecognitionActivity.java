package com.example.irislens.display.view;

import android.os.Bundle;
import com.example.irislens.R;
import com.example.irislens.common.BaseSwipeActivity;
import com.example.irislens.common.Functionalities;

/**
 * Actividad que realiza el reconocimiento de displays
 */
public class DisplayRecognitionActivity extends BaseSwipeActivity {

    /**
     * Configura la interfaz de reconocimiento de displays
     *
     * @param savedInstanceState Estado guardado
     */
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        currentFunctionalityIndex = Functionalities.DISPLAY;
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_display_recognition);
    }
}
