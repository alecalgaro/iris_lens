package com.example.irislens.common;

import android.Manifest;
import android.app.Activity;
import android.content.Intent;
import android.content.pm.PackageManager;

import androidx.annotation.NonNull;

public class PermissionManager {

    private static final int CAMERA_PERMISSION_REQUEST = 101;

    /**
     * Solicita permiso de la camara
     *
     * @param activity La actividad actual para solicitar el permiso
     */
    public void getPermissions(Activity activity) {
        if(activity.checkSelfPermission(Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED)
            activity.requestPermissions(new String[]{Manifest.permission.CAMERA}, CAMERA_PERMISSION_REQUEST);
    }

    /**
     * Maneja los resultados de la solicitud de permisos
     *
     * @param requestCode El codigo de solicitud
     * @param permissions Los permisos solicitados
     * @param grantResults Los resultados de la solicitud
     * @param activity La actividad actual
     */
    public void handlePermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults, Activity activity) {
        if (requestCode == CAMERA_PERMISSION_REQUEST) {
            if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                // Si se concedio el permiso de la camara, reinicia la actividad
                Intent intent = activity.getIntent();
                activity.finish();
                activity.startActivity(intent);
            } else {
                // Si no se concedio el permiso de la camara, se vuelve a solicitar
                getPermissions(activity);
            }
        }
    }
}

