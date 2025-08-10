package com.example.irislens.money.presenter;

import android.app.Activity;
import android.graphics.Bitmap;
import android.media.MediaPlayer;
import android.os.Handler;
import android.os.Looper;
import android.widget.TextView;

import com.example.irislens.R;
import com.example.irislens.common.ImageProcessor;
import com.example.irislens.common.TextToSpeechManager;
import com.example.irislens.money.model.RoboflowMoneyDetector;

import org.opencv.core.Mat;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class MoneyRecognitionPresenter {

    private final Activity activity;
    private final TextView tvResult;
    private final TextToSpeechManager ttsManager;
    private final ExecutorService executor;
    private int frameCount = 0;
    private volatile boolean isProcessing = false;

    public MoneyRecognitionPresenter(Activity activity, TextView tvResult) {
        this.activity = activity;
        this.tvResult = tvResult;
        this.ttsManager = new TextToSpeechManager(activity);
        this.executor = Executors.newSingleThreadExecutor();
    }

    public Mat rotateImage(Mat image) {
        return ImageProcessor.rotateImage(image);
    }

    public void processCameraFrame(Mat image) {
        if (isProcessing) return;

        frameCount++;
        if (frameCount == 10) {
            MediaPlayer mediaPlayer = MediaPlayer.create(activity, R.raw.captura);
            mediaPlayer.start();

            Bitmap bitmap = ImageProcessor.convertToBitmap(image);
            isProcessing = true;
            Handler handler = new Handler(Looper.getMainLooper());

            RoboflowMoneyDetector.detect(activity, bitmap, new RoboflowMoneyDetector.Callback() {
                @Override
                public void onResult(String result) {
                    handler.post(() -> {
                        tvResult.setText(result);
                        ttsManager.speak(result);
                        isProcessing = false;
                    });
                }

                @Override
                public void onError(String error) {
                    handler.post(() -> {
                        tvResult.setText("Error: " + error);
                        ttsManager.speak("Ocurrió un error al detectar el billete");
                        isProcessing = false;
                    });
                }
            });

            frameCount = 0;
        }
    }

    public void onDoubleTap() {
        if (ttsManager.isSpeaking()) {
            ttsManager.stop();
            tvResult.setText("");
        }
    }

    public void onDestroy() {
        ttsManager.shutdown();
        executor.shutdown();
    }
}
