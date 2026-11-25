package com.tamalon.app;

import android.app.Activity;
import android.app.Application;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.tamalon.app.network.SesionSocket;
import com.tamalon.app.network.TokenStore;

/**
 * Clase Application principal de Tamalon para gestionar el ciclo de vida
 * de la app y conectar/desconectar el socket de sesión en primer/segundo plano.
 */
public class TamalonApp extends Application {

    private int startedActivityCount = 0;
    private final Handler handler = new Handler(Looper.getMainLooper());
    private Runnable disconnectRunnable;

    @Override
    public void onCreate() {
        super.onCreate();
        registerActivityLifecycleCallbacks(new ActivityLifecycleCallbacks() {
            @Override
            public void onActivityStarted(@NonNull Activity activity) {
                startedActivityCount++;
                if (startedActivityCount == 1) {
                    // App pasa a primer plano
                    if (disconnectRunnable != null) {
                        handler.removeCallbacks(disconnectRunnable);
                        disconnectRunnable = null;
                    }
                    TokenStore tokenStore = new TokenStore(getApplicationContext());
                    if (tokenStore.isSessionActive()) {
                        SesionSocket.getInstance(getApplicationContext()).conectar();
                    }
                }
            }

            @Override
            public void onActivityStopped(@NonNull Activity activity) {
                startedActivityCount = Math.max(0, startedActivityCount - 1);
                if (startedActivityCount == 0) {
                    // App pasa a segundo plano (esperar ~700 ms para evitar desconexiones en rotaciones de pantalla)
                    if (disconnectRunnable != null) {
                        handler.removeCallbacks(disconnectRunnable);
                    }
                    disconnectRunnable = () -> {
                        if (startedActivityCount == 0) {
                            SesionSocket.getInstance(getApplicationContext()).desconectar();
                        }
                    };
                    handler.postDelayed(disconnectRunnable, 700L);
                }
            }

            @Override public void onActivityCreated(@NonNull Activity activity, @Nullable Bundle savedInstanceState) {}
            @Override public void onActivityResumed(@NonNull Activity activity) {}
            @Override public void onActivityPaused(@NonNull Activity activity) {}
            @Override public void onActivitySaveInstanceState(@NonNull Activity activity, @NonNull Bundle outState) {}
            @Override public void onActivityDestroyed(@NonNull Activity activity) {}
        });
    }
}
