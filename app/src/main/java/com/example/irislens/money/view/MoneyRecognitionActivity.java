package com.example.irislens.money.view;

import android.os.Bundle;
import android.widget.TextView;
import com.example.irislens.R;
import com.example.irislens.common.BaseSwipeActivity;
import com.example.irislens.common.Functionalities;

/**
 * Actividad que realiza el reconocimiento de billetes
 */
public class MoneyRecognitionActivity extends BaseSwipeActivity {

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

        // Captura swipe en toda la pantalla
        setupSwipeLayer();

        TextView tv = findViewById(R.id.tvResult);
        tv.setText("Reconocimiento de billetes (próximamente)");
    }
}
