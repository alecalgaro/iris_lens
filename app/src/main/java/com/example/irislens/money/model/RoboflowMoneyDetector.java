package com.example.irislens.money.model;

import android.content.Context;
import android.graphics.Bitmap;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.util.HashMap;
import java.util.Map;

import okhttp3.MediaType;
import okhttp3.MultipartBody;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;

public class RoboflowMoneyDetector {

    private static final String API_URL = "https://detect.roboflow.com/guidobilletes/1";
    private static final String API_KEY = "RX0UkhdZY0FKA9CrXihG";

    public interface Callback {
        void onResult(String result);
        void onError(String error);
    }

    public static void detect(Context context, Bitmap bitmap, Callback callback) {
        new Thread(() -> {
            try {
                ByteArrayOutputStream byteArrayOutputStream = new ByteArrayOutputStream();
                bitmap.compress(Bitmap.CompressFormat.JPEG, 90, byteArrayOutputStream);
                byte[] imageData = byteArrayOutputStream.toByteArray();

                RequestBody requestBody = new MultipartBody.Builder()
                        .setType(MultipartBody.FORM)
                        .addFormDataPart("file", "image.jpg",
                                RequestBody.create(imageData, MediaType.parse("image/jpeg")))
                        .build();

                Request request = new Request.Builder()
                        .url(API_URL + "?api_key=" + API_KEY)
                        .post(requestBody)
                        .build();

                OkHttpClient client = new OkHttpClient();
                Response response = client.newCall(request).execute();

                if (response.isSuccessful()) {
                    String body = response.body().string();
                    JSONObject json = new JSONObject(body);
                    JSONArray predictions = json.getJSONArray("predictions");

                    if (predictions.length() == 0) {
                        callback.onResult("No se detectó billete.");
                        return;
                    }

                    Map<String, Integer> billeteCounts = new HashMap<>();
                    int total = 0;

                    for (int i = 0; i < predictions.length(); i++) {
                        String clase = predictions.getJSONObject(i).getString("class");

                        int count = billeteCounts.containsKey(clase) ? billeteCounts.get(clase) : 0;
                        billeteCounts.put(clase, count + 1);

                        try {
                            total += Integer.parseInt(clase);
                        } catch (NumberFormatException ignored) {}
                    }

                    StringBuilder sb = new StringBuilder();
                    sb.append("Billetes detectados:\n");

                    for (Map.Entry<String, Integer> entry : billeteCounts.entrySet()) {
                        sb.append(entry.getKey())
                                .append(" x ")
                                .append(entry.getValue())
                                .append("\n");
                    }

                    sb.append("\nEn total: ").append(total).append(" pesos");

                    callback.onResult(sb.toString());

                } else {
                    callback.onError("Error HTTP: " + response.code());
                }
            } catch (Exception e) {
                callback.onError("Error al procesar: " + e.getMessage());
            }
        }).start();
    }
}
