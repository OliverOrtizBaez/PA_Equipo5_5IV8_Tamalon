package com.tamalon.app.network;

import android.content.Context;
import android.content.Intent;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;

import androidx.annotation.NonNull;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import com.tamalon.app.BuildConfig;
import com.tamalon.app.ui.LoginActivity;

import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

import okhttp3.OkHttpClient;
import okhttp3.WebSocket;
import okhttp3.WebSocketListener;

/**
 * Cliente WebSocket singleton para la gestión de revocación de sesión en tiempo real.
 */
public class SesionSocket {
    private static final String TAG = "SesionSocket";
    private static volatile SesionSocket instance;

    private final Context context;
    private final OkHttpClient client;
    private final Handler mainHandler;
    private final Gson gson;
    private final AtomicBoolean revocationHandled = new AtomicBoolean(false);

    private WebSocket webSocket;
    private boolean isConnecting = false;
    private boolean isIntentionalClose = false;
    private int retryAttempt = 0;
    private Runnable reconnectRunnable;

    private static final long[] RETRY_DELAYS = {1000L, 2000L, 4000L, 8000L, 16000L, 30000L};

    private SesionSocket(Context context) {
        this.context = context.getApplicationContext();
        this.mainHandler = new Handler(Looper.getMainLooper());
        this.gson = new Gson();

        // OkHttpClient propio para el WebSocket: readTimeout(0), pingInterval(30 s), SIN interceptores.
        this.client = new OkHttpClient.Builder()
                .readTimeout(0, TimeUnit.MILLISECONDS)
                .pingInterval(30, TimeUnit.SECONDS)
                .connectTimeout(30, TimeUnit.SECONDS)
                .build();
    }

    public static SesionSocket getInstance(Context context) {
        if (instance == null) {
            synchronized (SesionSocket.class) {
                if (instance == null) {
                    instance = new SesionSocket(context);
                }
            }
        }
        return instance;
    }

    public synchronized void conectar() {
        TokenStore tokenStore = new TokenStore(context);
        if (!tokenStore.isSessionActive()) {
            Log.d(TAG, "No hay sesión activa, no se conecta el WebSocket.");
            return;
        }

        if (webSocket != null || isConnecting) {
            Log.d(TAG, "WebSocket ya conectado o conectando.");
            return;
        }

        isIntentionalClose = false;
        isConnecting = true;

        String baseUrl = BuildConfig.API_BASE_URL;
        if (baseUrl.startsWith("https://")) {
            baseUrl = baseUrl.replace("https://", "wss://");
        } else if (baseUrl.startsWith("http://")) {
            baseUrl = baseUrl.replace("http://", "ws://");
        } else {
            baseUrl = "wss://" + baseUrl;
        }
        if (!baseUrl.endsWith("/")) {
            baseUrl += "/";
        }
        String wsUrl = baseUrl + "ws/auth";

        okhttp3.Request request = new okhttp3.Request.Builder()
                .url(wsUrl)
                .build();

        Log.d(TAG, "Conectando al WebSocket de autenticación...");
        webSocket = client.newWebSocket(request, new WebSocketListener() {
            @Override
            public void onOpen(@NonNull WebSocket ws, @NonNull okhttp3.Response response) {
                isConnecting = false;
                retryAttempt = 0;
                Log.d(TAG, "WebSocket abierto. Enviando auth...");

                TokenStore store = new TokenStore(context);
                String token = store.getToken();
                if (token == null || token.isEmpty()) {
                    ws.close(4001, "Token ausente");
                    return;
                }

                JsonObject authObj = new JsonObject();
                authObj.addProperty("type", "auth");
                authObj.addProperty("token", token);
                ws.send(authObj.toString());
            }

            @Override
            public void onMessage(@NonNull WebSocket ws, @NonNull String text) {
                try {
                    JsonObject json = gson.fromJson(text, JsonObject.class);
                    if (json != null && json.has("type")) {
                        String type = json.get("type").getAsString();
                        Log.d(TAG, "Mensaje WebSocket recibido, tipo: " + type);

                        switch (type) {
                            case "auth_ok":
                                retryAttempt = 0;
                                break;
                            case "auth_error":
                                int status = json.has("status") ? json.get("status").getAsInt() : 500;
                                String mensajeErr = json.has("mensaje") ? json.get("mensaje").getAsString() : "Error de autenticación";
                                if (status == 401 || status == 403) {
                                    handleSessionRevocation(mensajeErr);
                                }
                                break;
                            case "session_revoked":
                                String mensajeRev = json.has("mensaje") ? json.get("mensaje").getAsString() : "Sesión revocada";
                                handleSessionRevocation(mensajeRev);
                                break;
                            case "ping":
                                JsonObject pong = new JsonObject();
                                pong.addProperty("type", "pong");
                                ws.send(pong.toString());
                                break;
                            default:
                                break;
                        }
                    }
                } catch (Exception e) {
                    Log.e(TAG, "Error parseando mensaje WebSocket", e);
                }
            }

            @Override
            public void onClosed(@NonNull WebSocket ws, int code, @NonNull String reason) {
                isConnecting = false;
                webSocket = null;
                Log.d(TAG, "WebSocket cerrado. Código: " + code + ", Razón: " + reason);
                checkReconnect(code);
            }

            @Override
            public void onFailure(@NonNull WebSocket ws, @NonNull Throwable t, okhttp3.Response response) {
                isConnecting = false;
                webSocket = null;
                Log.e(TAG, "WebSocket fallo: " + t.getMessage());
                checkReconnect(0);
            }
        });
    }

    public synchronized void desconectar() {
        isConnecting = false;
        if (reconnectRunnable != null) {
            mainHandler.removeCallbacks(reconnectRunnable);
            reconnectRunnable = null;
        }
        isIntentionalClose = true;
        if (webSocket != null) {
            try {
                webSocket.close(1000, "Cierre intencional");
            } catch (Exception e) {
                // ignore
            }
            webSocket = null;
        }
        Log.d(TAG, "WebSocket desconectado intencionalmente.");
    }

    private void checkReconnect(int closeCode) {
        if (isIntentionalClose) {
            return;
        }

        if (closeCode == 4000 || closeCode == 4001 || closeCode == 4401 || closeCode == 4408 || closeCode == 4429) {
            Log.d(TAG, "Código de cierre definitivo (" + closeCode + "), no se reintenta.");
            return;
        }

        TokenStore tokenStore = new TokenStore(context);
        if (!tokenStore.isSessionActive()) {
            Log.d(TAG, "Sesión inactiva, no se reconecta.");
            return;
        }

        long baseDelay = RETRY_DELAYS[Math.min(retryAttempt, RETRY_DELAYS.length - 1)];
        long jitter = (long) (Math.random() * 1000);
        long delay = baseDelay + jitter;
        retryAttempt++;

        Log.d(TAG, "Programando reconexión WebSocket en " + delay + " ms (intento " + retryAttempt + ")");

        if (reconnectRunnable != null) {
            mainHandler.removeCallbacks(reconnectRunnable);
        }
        reconnectRunnable = this::conectar;
        mainHandler.postDelayed(reconnectRunnable, delay);
    }

    private void handleSessionRevocation(String mensaje) {
        if (revocationHandled.compareAndSet(false, true)) {
            Log.w(TAG, "Sesión revocada detectada via WebSocket.");
            desconectar();

            TokenStore tokenStore = new TokenStore(context);
            tokenStore.clearSession();

            mainHandler.post(() -> {
                Intent intent = new Intent(context, LoginActivity.class);
                intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
                intent.putExtra("mensaje_sesion", mensaje);
                context.startActivity(intent);
            });
        }
    }

    public void resetRevocationFlag() {
        revocationHandled.set(false);
    }
}
