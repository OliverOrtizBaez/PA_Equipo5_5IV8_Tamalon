package com.tamalon.app.network;

import android.content.Context;
import android.content.Intent;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.tamalon.app.BuildConfig;
import com.tamalon.app.ui.LoginActivity;

import java.util.Arrays;
import java.util.List;
import java.util.Random;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import okhttp3.WebSocket;
import okhttp3.WebSocketListener;

/**
 * Singleton que gestiona la conexión WebSocket para notificaciones de revocación de sesión en tiempo real.
 */
public class SesionSocket {

    private static final String TAG = "SesionSocket";

    private static final List<Integer> CODIGOS_SIN_REINTENTO = Arrays.asList(4000, 4001, 4401, 4408, 4429);
    private static final long DELAY_REINTENTO_INICIAL_MS = 1000L;
    private static final long DELAY_REINTENTO_MAX_MS = 30000L;

    private static volatile SesionSocket INSTANCE;

    private final Context context;
    private final OkHttpClient client;
    private final Handler mainHandler;
    private final Random random;
    private final AtomicBoolean revocadoManiobrado;

    private WebSocket webSocket;
    private boolean conectado = false;
    private boolean conectando = false;
    private boolean cierreIntencional = false;
    private int contadorReintentos = 0;
    private Runnable pendingReconnectRunnable;

    private SesionSocket(Context context) {
        this.context = context.getApplicationContext();
        this.mainHandler = new Handler(Looper.getMainLooper());
        this.random = new Random();
        this.revocadoManiobrado = new AtomicBoolean(false);

        // Cliente OkHttpClient dedicado exclusivamente al WebSocket (sin interceptores de log ni auth)
        this.client = new OkHttpClient.Builder()
                .readTimeout(0, TimeUnit.MILLISECONDS)
                .pingInterval(30, TimeUnit.SECONDS)
                .build();
    }

    public static SesionSocket getInstance(Context context) {
        if (INSTANCE == null) {
            synchronized (SesionSocket.class) {
                if (INSTANCE == null) {
                    INSTANCE = new SesionSocket(context);
                }
            }
        }
        return INSTANCE;
    }

    public static String obtenerWsUrl() {
        String baseUrl = BuildConfig.API_BASE_URL;
        if (baseUrl.startsWith("https://")) {
            baseUrl = "wss://" + baseUrl.substring(8);
        } else if (baseUrl.startsWith("http://")) {
            baseUrl = "ws://" + baseUrl.substring(7);
        } else if (!baseUrl.startsWith("wss://") && !baseUrl.startsWith("ws://")) {
            baseUrl = "wss://" + baseUrl;
        }

        if (baseUrl.endsWith("/")) {
            return baseUrl + "ws/auth";
        } else {
            return baseUrl + "/ws/auth";
        }
    }

    public synchronized void conectar() {
        TokenStore tokenStore = new TokenStore(context);
        if (!tokenStore.isSessionActive()) {
            return;
        }

        if (conectado || conectando) {
            return;
        }

        cierreIntencional = false;
        conectando = true;
        revocadoManiobrado.set(false);
        cancelarReintentoPendiente();

        String wsUrl = obtenerWsUrl();
        Request request = new Request.Builder()
                .url(wsUrl)
                .build();

        webSocket = client.newWebSocket(request, new SesionWebSocketListener());
    }

    public synchronized void desconectar() {
        cierreIntencional = true;
        conectando = false;
        conectado = false;
        cancelarReintentoPendiente();

        if (webSocket != null) {
            webSocket.close(1000, "cierre intencional");
            webSocket = null;
        }
    }

    public synchronized boolean isConectado() {
        return conectado;
    }

    private synchronized void cancelarReintentoPendiente() {
        if (pendingReconnectRunnable != null) {
            mainHandler.removeCallbacks(pendingReconnectRunnable);
            pendingReconnectRunnable = null;
        }
    }

    private synchronized void programarReintento() {
        if (cierreIntencional) {
            return;
        }

        TokenStore tokenStore = new TokenStore(context);
        if (!tokenStore.isSessionActive()) {
            return;
        }

        cancelarReintentoPendiente();

        long delay = (long) (DELAY_REINTENTO_INICIAL_MS * Math.pow(2, contadorReintentos));
        if (delay > DELAY_REINTENTO_MAX_MS) {
            delay = DELAY_REINTENTO_MAX_MS;
        }
        long jitter = random.nextInt(1000);
        long delayTotal = delay + jitter;

        contadorReintentos++;

        Log.d(TAG, "Programando reintento WebSocket en " + delayTotal + " ms (intento #" + contadorReintentos + ")");

        pendingReconnectRunnable = () -> {
            synchronized (SesionSocket.this) {
                pendingReconnectRunnable = null;
            }
            conectar();
        };

        mainHandler.postDelayed(pendingReconnectRunnable, delayTotal);
    }

    private void manejarSesionRevocada(String mensaje) {
        if (!revocadoManiobrado.compareAndSet(false, true)) {
            return;
        }

        mainHandler.post(() -> {
            desconectar();
            TokenStore tokenStore = new TokenStore(context);
            tokenStore.clearSession();

            String mensajeFinal = (mensaje != null && !mensaje.isEmpty())
                    ? mensaje
                    : "Tu sesión ha sido revocada.";

            Intent intent = new Intent(context, LoginActivity.class);
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
            intent.putExtra("mensaje_sesion", mensajeFinal);
            context.startActivity(intent);
        });
    }

    private class SesionWebSocketListener extends WebSocketListener {

        @Override
        public void onOpen(@NonNull WebSocket ws, @NonNull Response response) {
            Log.d(TAG, "WebSocket abierto (onOpen)");
            synchronized (SesionSocket.this) {
                conectando = false;
                conectado = true;
            }

            TokenStore tokenStore = new TokenStore(context);
            String token = tokenStore.getToken();

            if (token == null || token.isEmpty()) {
                Log.w(TAG, "Sin token disponible al abrir WebSocket");
                ws.close(1000, "Sin token");
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
                JsonObject json = JsonParser.parseString(text).getAsJsonObject();
                String type = json.has("type") ? json.get("type").getAsString() : "";
                Log.d(TAG, "WebSocket recibido type=" + type);

                switch (type) {
                    case "auth_ok":
                        synchronized (SesionSocket.this) {
                            contadorReintentos = 0;
                        }
                        break;

                    case "auth_error":
                        int status = json.has("status") ? json.get("status").getAsInt() : 0;
                        String msgErr = json.has("mensaje") ? json.get("mensaje").getAsString() : "Sesión no válida";
                        if (status == 401 || status == 403) {
                            Log.w(TAG, "WebSocket auth_error status " + status);
                            manejarSesionRevocada(msgErr);
                        } else {
                            Log.w(TAG, "WebSocket auth_error status " + status);
                        }
                        break;

                    case "session_revoked":
                        String msgRev = json.has("mensaje") ? json.get("mensaje").getAsString() : "Sesión revocada";
                        Log.w(TAG, "WebSocket session_revoked");
                        manejarSesionRevocada(msgRev);
                        break;

                    case "ping":
                        JsonObject pongObj = new JsonObject();
                        pongObj.addProperty("type", "pong");
                        ws.send(pongObj.toString());
                        break;

                    default:
                        Log.d(TAG, "WebSocket type no reconocido: " + type);
                        break;
                }
            } catch (Exception e) {
                Log.e(TAG, "Error procesando mensaje WebSocket", e);
            }
        }

        @Override
        public void onClosing(@NonNull WebSocket ws, int code, @NonNull String reason) {
            Log.d(TAG, "WebSocket onClosing, code=" + code);
            synchronized (SesionSocket.this) {
                conectado = false;
                conectando = false;
            }
        }

        @Override
        public void onClosed(@NonNull WebSocket ws, int code, @NonNull String reason) {
            Log.d(TAG, "WebSocket onClosed, code=" + code);
            synchronized (SesionSocket.this) {
                conectado = false;
                conectando = false;
            }

            if (!CODIGOS_SIN_REINTENTO.contains(code)) {
                programarReintento();
            }
        }

        @Override
        public void onFailure(@NonNull WebSocket ws, @NonNull Throwable t, @Nullable Response response) {
            Log.e(TAG, "WebSocket onFailure", t);
            synchronized (SesionSocket.this) {
                conectado = false;
                conectando = false;
            }

            int code = (response != null) ? response.code() : -1;
            if (!CODIGOS_SIN_REINTENTO.contains(code)) {
                programarReintento();
            }
        }
    }
}
