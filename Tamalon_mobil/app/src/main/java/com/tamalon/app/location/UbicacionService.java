package com.tamalon.app.location;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.location.Location;
import android.location.LocationListener;
import android.location.LocationManager;
import android.os.Build;
import android.os.IBinder;
import android.os.Looper;
import android.util.Log;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.app.NotificationCompat;
import com.tamalon.app.R;
import com.tamalon.app.network.ApiClient;
import com.tamalon.app.network.ApiService;
import com.tamalon.app.network.JornadaResumenResponse;
import com.tamalon.app.network.UbicacionMiaRequest;
import com.tamalon.app.network.UbicacionMiaResponse;
import java.util.ArrayList;
import java.util.List;
import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

public class UbicacionService extends Service {
    private static final String TAG = "UbicacionService";
    private static final String CHANNEL_ID = "ubicacion_channel";
    private static final int NOTIF_ID = 1001;
    public static final String ACTION_STOP_JORNADA = "com.tamalon.app.ACTION_STOP_JORNADA";

    private LocationManager locationManager;
    private LocationListener locationListener;
    private ApiService apiService;
    private final List<UbicacionPoint> offlineQueue = new ArrayList<>();
    private final List<UbicacionMiaRequest> batchBuffer = new ArrayList<>();
    private Location lastKnownLocation;
    private long lastSendTime = 0;
    private android.os.Handler heartbeatHandler;
    private Runnable heartbeatRunnable;

    public static class UbicacionPoint {
        public double lat;
        public double lng;
        public long timestamp;
        public UbicacionPoint(double lat, double lng, long timestamp) {
            this.lat = lat;
            this.lng = lng;
            this.timestamp = timestamp;
        }
    }

    @Override
    public void onCreate() {
        super.onCreate();
        apiService = ApiClient.getApiService(this);
        createNotificationChannel();
        startForeground(NOTIF_ID, buildNotification());

        locationManager = (LocationManager) getSystemService(Context.LOCATION_SERVICE);
        setupLocationUpdates();
        setupHeartbeat();
    }

    private Notification buildNotification() {
        Intent stopIntent = new Intent(this, UbicacionService.class);
        stopIntent.setAction(ACTION_STOP_JORNADA);
        PendingIntent stopPendingIntent = PendingIntent.getService(this, 0, stopIntent, PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_UPDATE_CURRENT);

        NotificationCompat.Builder builder = new NotificationCompat.Builder(this, CHANNEL_ID)
                .setContentTitle("Tamalón")
                .setContentText("Compartiendo tu ubicación")
                .setSmallIcon(R.drawable.ic_navigation)
                .setOngoing(true)
                .addAction(R.drawable.ic_clock, "Terminar recorrido", stopPendingIntent);
        return builder.build();
    }

    private void createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel channel = new NotificationChannel(CHANNEL_ID, "Ubicación en vivo", NotificationManager.IMPORTANCE_LOW);
            NotificationManager manager = getSystemService(NotificationManager.class);
            if (manager != null) {
                manager.createNotificationChannel(channel);
            }
        }
    }

    private void setupLocationUpdates() {
        if (locationManager == null) return;
        locationListener = new LocationListener() {
            @Override
            public void onLocationChanged(@NonNull Location location) {
                processNewLocation(location);
            }
            @Override public void onProviderEnabled(@NonNull String provider) {}
            @Override public void onProviderDisabled(@NonNull String provider) {}
        };

        try {
            String provider = LocationManager.GPS_PROVIDER;
            if (!locationManager.isProviderEnabled(provider)) {
                provider = LocationManager.NETWORK_PROVIDER;
            }
            if (locationManager.isProviderEnabled(provider)) {
                locationManager.requestLocationUpdates(provider, 10_000L, 10f, locationListener, Looper.getMainLooper());
            }
        } catch (SecurityException e) {
            Log.e(TAG, "Security exception requesting location updates", e);
        }
    }

    private void processNewLocation(Location location) {
        if (location == null) return;
        long age = System.currentTimeMillis() - location.getTime();
        if (location.getAccuracy() > 50f || age > 30_000L) {
            return;
        }
        lastKnownLocation = location;
        addPointAndSend(location.getLatitude(), location.getLongitude());
    }

    private void addPointAndSend(double lat, double lng) {
        batchBuffer.add(new UbicacionMiaRequest(lat, lng));
        if (batchBuffer.size() >= 20) {
            flushBatch();
        } else {
            long now = System.currentTimeMillis();
            if (now - lastSendTime > 10_000L) {
                flushBatch();
            }
        }
    }

    private void flushBatch() {
        if (batchBuffer.isEmpty()) return;
        List<UbicacionMiaRequest> toSend = new ArrayList<>(batchBuffer);
        batchBuffer.clear();
        UbicacionMiaRequest latest = toSend.get(toSend.size() - 1);
        lastSendTime = System.currentTimeMillis();

        apiService.actualizarUbicacionMia(latest).enqueue(new Callback<UbicacionMiaResponse>() {
            @Override
            public void onResponse(@NonNull Call<UbicacionMiaResponse> call, @NonNull Response<UbicacionMiaResponse> response) {
                if (!response.isSuccessful()) {
                    for (UbicacionMiaRequest req : toSend) {
                        if (offlineQueue.size() < 200) {
                            offlineQueue.add(new UbicacionPoint(req.latitud, req.longitud, System.currentTimeMillis()));
                        }
                    }
                } else {
                    flushOfflineQueue();
                }
            }
            @Override
            public void onFailure(@NonNull Call<UbicacionMiaResponse> call, @NonNull Throwable t) {
                for (UbicacionMiaRequest req : toSend) {
                    if (offlineQueue.size() < 200) {
                        offlineQueue.add(new UbicacionPoint(req.latitud, req.longitud, System.currentTimeMillis()));
                    }
                }
            }
        });
    }

    private void flushOfflineQueue() {
        if (offlineQueue.isEmpty()) return;
        UbicacionPoint pt = offlineQueue.remove(0);
        apiService.actualizarUbicacionMia(new UbicacionMiaRequest(pt.lat, pt.lng)).enqueue(new Callback<UbicacionMiaResponse>() {
            @Override public void onResponse(@NonNull Call<UbicacionMiaResponse> call, @NonNull Response<UbicacionMiaResponse> response) {
                if (response.isSuccessful() && !offlineQueue.isEmpty()) {
                    flushOfflineQueue();
                }
            }
            @Override public void onFailure(@NonNull Call<UbicacionMiaResponse> call, @NonNull Throwable t) {}
        });
    }

    private void setupHeartbeat() {
        heartbeatHandler = new android.os.Handler(Looper.getMainLooper());
        heartbeatRunnable = new Runnable() {
            @Override
            public void run() {
                if (lastKnownLocation != null) {
                    apiService.actualizarUbicacionMia(new UbicacionMiaRequest(lastKnownLocation.getLatitude(), lastKnownLocation.getLongitude())).enqueue(new Callback<UbicacionMiaResponse>() {
                        @Override public void onResponse(@NonNull Call<UbicacionMiaResponse> call, @NonNull Response<UbicacionMiaResponse> response) {}
                        @Override public void onFailure(@NonNull Call<UbicacionMiaResponse> call, @NonNull Throwable t) {}
                    });
                }
                heartbeatHandler.postDelayed(this, 30_000L);
            }
        };
        heartbeatHandler.postDelayed(heartbeatRunnable, 30_000L);
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        if (intent != null && ACTION_STOP_JORNADA.equals(intent.getAction())) {
            apiService.finalizarJornada().enqueue(new Callback<JornadaResumenResponse>() {
                @Override public void onResponse(@NonNull Call<JornadaResumenResponse> call, @NonNull Response<JornadaResumenResponse> response) {}
                @Override public void onFailure(@NonNull Call<JornadaResumenResponse> call, @NonNull Throwable t) {}
            });
            stopSelf();
        }
        return START_STICKY;
    }

    @Override
    public void onDestroy() {
        if (locationManager != null && locationListener != null) {
            locationManager.removeUpdates(locationListener);
        }
        if (heartbeatHandler != null && heartbeatRunnable != null) {
            heartbeatHandler.removeCallbacks(heartbeatRunnable);
        }
        super.onDestroy();
    }

    @Nullable
    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }
}
