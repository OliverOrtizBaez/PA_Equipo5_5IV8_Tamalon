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
 * Clase Application principal que gestiona el ciclo de vida global de la aplicación
 * y controla la conexión/desconexión del WebSocket de sesión según el estado en primer plano.
 */
public class TamalonApp extends Application {

    private int startedActivityCount = 0;
    private Handler mainHandler;
    private Runnable disconnectRunnable;

    @Override
    public void onCreate() {
        super.onCreate();
        mainHandler = new Handler(Looper.getMainLooper());
        disconnectRunnable = () -> {
            if (startedActivityCount == 0) {
                SesionSocket.getInstance(TamalonApp.this).desconectar();
            }
        };

        registerActivityLifecycleCallbacks(new ActivityLifecycleCallbacks() {
            @Override
            public void onActivityCreated(@NonNull Activity activity, @Nullable Bundle savedInstanceState) {}

            @Override
            public void onActivityStarted(@NonNull Activity activity) {
                startedActivityCount++;
                mainHandler.removeCallbacks(disconnectRunnable);
                if (startedActivityCount == 1) {
                    TokenStore tokenStore = new TokenStore(TamalonApp.this);
                    if (tokenStore.isSessionActive()) {
                        SesionSocket.getInstance(TamalonApp.this).conectar();
                    }
                }
            }

            @Override
            public void onActivityResumed(@NonNull Activity activity) {}

            @Override
            public void onActivityPaused(@NonNull Activity activity) {}

            @Override
            public void onActivityStopped(@NonNull Activity activity) {
                if (startedActivityCount > 0) {
                    startedActivityCount--;
                }
                if (startedActivityCount == 0) {
                    mainHandler.postDelayed(disconnectRunnable, 700);
                }
            }

            @Override
            public void onActivitySaveInstanceState(@NonNull Activity activity, @NonNull Bundle outState) {}

            @Override
            public void onActivityDestroyed(@NonNull Activity activity) {}
        });
    }
}
