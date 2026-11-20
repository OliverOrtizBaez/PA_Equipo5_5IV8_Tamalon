package com.tamalon.app.network;

import android.content.Context;
import android.util.Log;
import com.google.gson.Gson;
import com.google.gson.annotations.SerializedName;
import java.io.IOException;
import retrofit2.Response;

public class ApiErrorHelper {

    private static final String TAG = "ApiErrorHelper";

    private static class ErrorResponse {
        @SerializedName("mensaje")
        public String mensaje;
    }

    public static String parseError(Context context, Throwable t, Response<?> response) {
        if (t instanceof IOException) {
            return "Sin conexión a internet. Verifica tu conexión e intenta de nuevo.";
        }

        if (response != null) {
            int code = response.code();
            String errorBodyStr = null;
            try {
                if (response.errorBody() != null) {
                    errorBodyStr = response.errorBody().string();
                }
            } catch (Exception e) {
                Log.e(TAG, "Error reading errorBody", e);
            }

            Log.d(TAG, "API Error Code: " + code + ", Body: " + errorBodyStr);

            if (code == 401 || code == 403) {
                String serverMsg = extractMensaje(errorBodyStr);
                if (code == 403 && serverMsg != null && serverMsg.toLowerCase().contains("suspendido")) {
                    TokenStore store = new TokenStore(context);
                    store.clearSession();
                    return serverMsg;
                }
                if (code == 401) {
                    TokenStore store = new TokenStore(context);
                    store.clearSession();
                    return serverMsg != null ? serverMsg : "Sesión expirada o credenciales incorrectas.";
                }
                if (serverMsg != null && !serverMsg.isEmpty()) {
                    return serverMsg;
                }
            }

            String serverMsg = extractMensaje(errorBodyStr);
            if (serverMsg != null && !serverMsg.isEmpty()) {
                return serverMsg;
            }
        }

        return "Ocurrió un error inesperado. Intenta de nuevo en unos minutos.";
    }

    private static String extractMensaje(String errorBodyStr) {
        if (errorBodyStr == null || errorBodyStr.isEmpty()) return null;
        try {
            Gson gson = new Gson();
            ErrorResponse err = gson.fromJson(errorBodyStr, ErrorResponse.class);
            if (err != null && err.mensaje != null) {
                return err.mensaje;
            }
        } catch (Exception e) {
            // not json
        }
        return null;
    }
}
