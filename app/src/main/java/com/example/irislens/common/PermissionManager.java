package com.example.irislens.common;

import android.Manifest;
import android.app.Activity;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.provider.Settings;

import androidx.annotation.NonNull;

public class PermissionManager {

    private static final int CAMERA_PERMISSION_REQUEST = 101;

    private static final String PREFS = "permission_prefs";
    private static final String KEY_PERMISSION_REQUESTED = "camera_requested";

    /**
     * Solicita permiso de cámara
     */
    public void getPermissions(Activity activity) {

        // Ya concedido
        if (activity.checkSelfPermission(Manifest.permission.CAMERA)
                == PackageManager.PERMISSION_GRANTED) {

            return;
        }

        SharedPreferences prefs = activity.getSharedPreferences(
                PREFS,
                Activity.MODE_PRIVATE
        );

        boolean alreadyRequested =
                prefs.getBoolean(KEY_PERMISSION_REQUESTED, false);

        /**
         * Android ya bloqueó el popup
         */
        if (alreadyRequested
                && !activity.shouldShowRequestPermissionRationale(
                Manifest.permission.CAMERA)) {

            Intent intent = new Intent(
                    Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                    Uri.fromParts(
                            "package",
                            activity.getPackageName(),
                            null
                    )
            );

            activity.startActivity(intent);

            return;
        }

        // Marcar como solicitado
        prefs.edit()
                .putBoolean(KEY_PERMISSION_REQUESTED, true)
                .apply();

        // Mostrar popup normal
        activity.requestPermissions(
                new String[]{Manifest.permission.CAMERA},
                CAMERA_PERMISSION_REQUEST
        );
    }

    /**
     * Resultado del permiso
     */
    public void handlePermissionsResult(
            int requestCode,
            @NonNull String[] permissions,
            @NonNull int[] grantResults,
            Activity activity
    ) {

        if (requestCode != CAMERA_PERMISSION_REQUEST) {
            return;
        }

        // Permiso concedido
        if (grantResults.length > 0
                && grantResults[0] == PackageManager.PERMISSION_GRANTED) {

            return;
        }

        // Permiso rechazado
        activity.finishAffinity();
    }
}