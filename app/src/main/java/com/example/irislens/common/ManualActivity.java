package com.example.irislens.common;

import android.accessibilityservice.AccessibilityServiceInfo;
import android.os.Bundle;
import android.os.Handler;
import android.view.View;
import android.view.accessibility.AccessibilityManager;
import android.widget.ScrollView;
import android.widget.TextView;

import com.example.irislens.R;

public class ManualActivity extends BaseSwipeActivity {

    private Handler handler = new Handler();

    private TextView[] sections;
    private int currentSection = 0;

    private ScrollView scrollView;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_manual);

        currentFunctionalityIndex = Functionalities.MANUAL;

        scrollView = findViewById(R.id.manualScroll);

        sections = new TextView[]{
                findViewById(R.id.section_welcome),
                findViewById(R.id.section_intro),
                findViewById(R.id.section_navigation),
                findViewById(R.id.section_interaction),
                findViewById(R.id.section_function),
                findViewById(R.id.section_support)
        };

        AppVoiceManager.getInstance(this).initializeTTS();

        if (voiceManager != null) {
            voiceManager.stop();
        }

        setupTouchReading();

        if (isTalkBackEnabled()) {
            startTalkBackReading();
        } else {
            readSectionsSequentially();
        }
    }

    private void startTalkBackReading() {

        handler.postDelayed(() -> readNextSectionTalkBack(), 800);

    }

    private void readNextSectionTalkBack() {

        if (currentSection >= sections.length) {
            return;
        }

        TextView section = sections[currentSection];

        highlightSection(section);

        section.requestFocus();

        section.announceForAccessibility(section.getText());

        int delay = Math.max(6000, section.getText().length() * 90);

        handler.postDelayed(() -> {

            currentSection++;
            readNextSectionTalkBack();

        }, delay);
    }

    private boolean isTalkBackEnabled() {

        AccessibilityManager am =
                (AccessibilityManager) getSystemService(ACCESSIBILITY_SERVICE);

        if (am == null || !am.isEnabled()) {
            return false;
        }

        for (AccessibilityServiceInfo service :
                am.getEnabledAccessibilityServiceList(
                        AccessibilityServiceInfo.FEEDBACK_SPOKEN)) {

            if (service.getId().contains("talkback")) {
                return true;
            }
        }

        return false;
    }

    private void readSectionsSequentially() {

        handler.postDelayed(() -> {

            if (voiceManager != null && voiceManager.isTTSInitialized()) {
                readNextSection();
            } else {
                readSectionsSequentially();
            }

        }, 600);
    }

    private void readNextSection() {

        if (currentSection >= sections.length) {
            return;
        }

        TextView section = sections[currentSection];

        highlightSection(section);

        String text = section.getText().toString();

        if (voiceManager != null) {
            voiceManager.speak(text);
        }

        waitUntilSpeechFinishes();
    }

    private void waitUntilSpeechFinishes() {

        handler.postDelayed(() -> {

            if (voiceManager != null && voiceManager.isSpeaking()) {

                waitUntilSpeechFinishes(); // sigue esperando

            } else {

                currentSection++;
                readNextSection();

            }

        }, 300);
    }

    private void highlightSection(TextView section) {

        for (TextView tv : sections) {
            tv.setBackgroundColor(0x00000000);
        }

        section.setBackgroundColor(0x33FFFFFF);

        section.sendAccessibilityEvent(
                android.view.accessibility.AccessibilityEvent.TYPE_VIEW_FOCUSED
        );

        scrollView.post(() ->
                scrollView.smoothScrollTo(0, section.getTop())
        );
    }

    private void setupTouchReading() {

        for (int i = 0; i < sections.length; i++) {

            int index = i;
            TextView tv = sections[i];

            tv.setFocusable(true);
            tv.setFocusableInTouchMode(true);

            // Detecta cuando TalkBack enfoca el elemento (1 toque)
            tv.setAccessibilityDelegate(new View.AccessibilityDelegate() {
                @Override
                public void sendAccessibilityEvent(View host, int eventType) {
                    super.sendAccessibilityEvent(host, eventType);

                    if (eventType ==
                            android.view.accessibility.AccessibilityEvent.TYPE_VIEW_ACCESSIBILITY_FOCUSED) {

                        currentSection = index;
                        highlightSection(tv);
                    }
                }
            });

            // Acción cuando el usuario hace doble toque
            tv.setOnClickListener(v -> {

                if (voiceManager != null) {

                    voiceManager.stop();

                    currentSection = index;

                    highlightSection(tv);

                    voiceManager.speak(tv.getText().toString());
                }

            });

        }
    }

    @Override
    protected void onDoubleTapDetected() {

        if (voiceManager != null) {
            voiceManager.stop();
        }

    }

    @Override
    protected void onDestroy() {
        handler.removeCallbacksAndMessages(null);
        super.onDestroy();
    }
}