package com.tamalon.app.ui;

import android.Manifest;
import android.content.Intent;
import android.content.res.ColorStateList;
import android.content.pm.PackageManager;
import android.location.Location;
import android.location.LocationListener;
import android.location.LocationManager;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.provider.Settings;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.text.TextWatcher;
import android.text.Editable;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.RippleDrawable;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import com.tamalon.app.BuildConfig;
import com.tamalon.app.R;
import com.tamalon.app.config.AppConfig;
import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import com.tamalon.app.data.local.AppDatabase;
import com.tamalon.app.model.Ruta;
import com.tamalon.app.network.*;

import org.osmdroid.config.Configuration;
import org.osmdroid.tileprovider.tilesource.TileSourceFactory;
import org.osmdroid.util.BoundingBox;
import org.osmdroid.util.GeoPoint;
import org.osmdroid.views.MapView;
import org.osmdroid.views.overlay.CopyrightOverlay;
import org.osmdroid.views.overlay.Marker;
import org.osmdroid.views.overlay.Polygon;
import org.osmdroid.views.overlay.Polyline;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

/** Mapa nativo OpenStreetMap conectado al backend REST. */
public class MapaActivity extends AppCompatActivity {
    private static final String TAG = "MapaActivity";
    private static final int PERM_LOCATION = 1001;
    private static final long RADIO_METROS = 500L;
    private static final double LIMITE_MAPA_METROS = 800.0;

    private static boolean loggedUbicacionMia = false;
    private static boolean loggedFueraDeRuta = false;
    private static boolean loggedCercanos = false;

    private FrameLayout mapContainer;
    private LinearLayout overlayLoading, overlayPermission;
    private TextView tvMapEstado;
    private Button btnZoomIn, btnZoomOut, btnToggleRuta, btnGrantPermission;
    private LinearLayout categoryFiltersContainer;
    private final String[] categoriasAntojitos = {"Tamales", "Atole", "Quesadillas", "Pambazos", "Sopes", "Champurrado"};
    private EditText mapSearchInput;
    private volatile String textoBusquedaMapa = "";
    private final Set<String> filtrosAntojitos = new HashSet<>();
    private MapView mapView;
    private Marker marcadorUsuario;
    private Polygon circuloBusqueda;
    private LocationManager locationManager;
    private LocationListener locationListener;
    private Location ultimaUbicacion;
    private boolean mapaInicializado = false;
    private boolean centroInicialEstablecido = false;
    private boolean enRuta = false;
    private View cardVendedor;
    private ImageView cardVendorAvatar;
    private View vendorRouteCard;
    private View vendorRouteDot;
    private TextView vendorRouteStateText;
    private TextView vendorRouteExplanation;
    private Button vendorQuickCatalogs, vendorQuickRoutes;
    private TextView cardVendorName, cardVendorDistance, cardVendorDesc;
    private Button cardBtnCatalog, cardBtnProfile;
    private View cardBtnClose;
    private int usuarioId = -1;
    private String rol = "cliente";
    private String plan = "gratuito";
    private final Map<Integer, Marker> marcadoresVendedores = new HashMap<>();
    private Polyline rutaVendedorOverlay;
    private final List<Marker> paradasRutaOverlay = new ArrayList<>();
    private final Handler handler = new Handler(Looper.getMainLooper());
    private final java.util.concurrent.ExecutorService executor = java.util.concurrent.Executors.newSingleThreadExecutor();
    private ApiService apiService;
    private long ultimaActualizacionServidorMs = 0;

    private final Runnable refrescarVendedoresRunnable = new Runnable() {
        @Override public void run() {
            refrescarVendedoresCercanos();
            handler.postDelayed(this, AppConfig.INTERVALO_ACTUALIZACION_MS);
        }
    };

    @Override protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_map);
        usuarioId = getIntent().getIntExtra("usuario_id", -1);
        String rolRecibido = getIntent().getStringExtra("rol");
        String planRecibido = getIntent().getStringExtra("plan");
        if (rolRecibido != null) rol = rolRecibido;
        if (planRecibido != null) plan = planRecibido;

        apiService = ApiClient.getApiService(this);

        mapContainer = findViewById(R.id.map_container);
        overlayLoading = findViewById(R.id.map_overlay_loading);
        overlayPermission = findViewById(R.id.map_overlay_permission);
        tvMapEstado = findViewById(R.id.map_loading_text);
        btnZoomIn = findViewById(R.id.map_btn_zoom_in);
        btnZoomOut = findViewById(R.id.map_btn_zoom_out);
        btnToggleRuta = findViewById(R.id.map_btn_toggle_availability);
        btnGrantPermission = findViewById(R.id.map_btn_grant_permission);
        categoryFiltersContainer = findViewById(R.id.map_category_filters);
        mapSearchInput = findViewById(R.id.map_search_input);
        mapSearchInput.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) { }
            @Override public void onTextChanged(CharSequence s, int start, int before, int count) {
                textoBusquedaMapa = s.toString().trim().toLowerCase(java.util.Locale.ROOT);
                refrescarVendedoresCercanos();
            }
            @Override public void afterTextChanged(Editable s) { }
        });

        android.content.SharedPreferences filtrosPrefs = getSharedPreferences("tamalon_map_filters_" + usuarioId, MODE_PRIVATE);
        filtrosAntojitos.addAll(filtrosPrefs.getStringSet("categorias", new HashSet<>()));
        construirBotonesPreferencias();

        btnZoomIn.setOnClickListener(v -> { if (mapView != null) mapView.getController().zoomIn(); });
        btnZoomOut.setOnClickListener(v -> { if (mapView != null) mapView.getController().zoomOut(); });
        btnGrantPermission.setOnClickListener(v -> abrirConfiguracionPermisos());

        vendorRouteCard = findViewById(R.id.vendor_route_card);
        vendorRouteDot = findViewById(R.id.vendor_route_dot);
        vendorRouteStateText = findViewById(R.id.vendor_route_state_text);
        vendorRouteExplanation = findViewById(R.id.vendor_route_explanation);
        vendorQuickCatalogs = findViewById(R.id.vendor_quick_catalogs);
        vendorQuickRoutes = findViewById(R.id.vendor_quick_routes);

        View searchContainer = findViewById(R.id.map_search_container);
        View categoryScroll = findViewById(R.id.map_category_scroll);

        if ("vendedor".equalsIgnoreCase(rol)) {
            if (searchContainer != null) searchContainer.setVisibility(View.GONE);
            if (categoryScroll != null) categoryScroll.setVisibility(View.GONE);
            if (vendorRouteCard != null) vendorRouteCard.setVisibility(View.VISIBLE);
            btnToggleRuta.setOnClickListener(v -> toggleDisponibilidad());
            if (vendorQuickCatalogs != null) {
                vendorQuickCatalogs.setOnClickListener(v -> {
                    Intent i = new Intent(this, CatalogoActivity.class);
                    i.putExtra("usuario_id", usuarioId);
                    i.putExtra("rol", rol);
                    i.putExtra("plan", plan);
                    startActivity(i);
                });
            }
            if (vendorQuickRoutes != null) {
                vendorQuickRoutes.setOnClickListener(v -> {
                    Intent i = new Intent(this, RutasActivity.class);
                    i.putExtra("usuario_id", usuarioId);
                    i.putExtra("rol", rol);
                    i.putExtra("plan", plan);
                    startActivity(i);
                });
            }
            actualizarVistaEstadoRuta();
        } else {
            if (vendorRouteCard != null) vendorRouteCard.setVisibility(View.GONE);
        }

        View btnNotificaciones = findViewById(R.id.map_btn_notifications);
        if (btnNotificaciones != null) {
            btnNotificaciones.setOnClickListener(v ->
                    Toast.makeText(this, "Notificaciones: Tienes puestos favoritos en ruta cerca de ti", Toast.LENGTH_LONG).show());
        }

        cardVendedor = findViewById(R.id.map_selected_vendor_card);
        cardVendorAvatar = findViewById(R.id.map_card_avatar);
        cardVendorName = findViewById(R.id.map_card_vendor_name);
        cardVendorDistance = findViewById(R.id.map_card_vendor_distance);
        cardVendorDesc = findViewById(R.id.map_card_vendor_description);
        cardBtnCatalog = findViewById(R.id.map_card_btn_catalog);
        cardBtnProfile = findViewById(R.id.map_card_btn_profile);
        cardBtnClose = findViewById(R.id.map_card_btn_close);
        if (cardBtnClose != null) {
            cardBtnClose.setOnClickListener(v -> {
                if (cardVendedor != null) cardVendedor.setVisibility(View.GONE);
                limpiarRutaClienteEnMapa();
            });
        }

        BottomNavigation.configurar(this, findViewById(android.R.id.content), usuarioId, rol, plan);
        solicitarPermisoUbicacion();
    }

    private boolean tienePermisoUbicacion() {
        return ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED
                || ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED;
    }

    private void solicitarPermisoUbicacion() {
        if (tienePermisoUbicacion()) {
            overlayPermission.setVisibility(View.GONE);
            inicializarMapa();
        } else {
            ActivityCompat.requestPermissions(this,
                    new String[]{Manifest.permission.ACCESS_COARSE_LOCATION, Manifest.permission.ACCESS_FINE_LOCATION}, PERM_LOCATION);
        }
    }

    @Override public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == PERM_LOCATION) {
            if (tienePermisoUbicacion()) {
                overlayPermission.setVisibility(View.GONE);
                inicializarMapa();
            } else {
                overlayLoading.setVisibility(View.GONE);
                overlayPermission.setVisibility(View.VISIBLE);
            }
        }
    }

    @Override protected void onResume() {
        super.onResume();
        if (mapView != null) mapView.onResume();
        if (tienePermisoUbicacion()) {
            overlayPermission.setVisibility(View.GONE);
            if (!mapaInicializado) inicializarMapa();
            else iniciarActualizacionesUbicacion();
        }
    }

    @Override protected void onPause() {
        if (mapView != null) mapView.onPause();
        super.onPause();
    }

    @Override protected void onDestroy() {
        handler.removeCallbacks(refrescarVendedoresRunnable);
        detenerActualizacionesUbicacion();
        executor.shutdown();
        if (enRuta) {
            enviarFueraDeRutaServidor();
        }
        if (mapView != null) {
            mapView.onDetach();
            mapView = null;
        }
        super.onDestroy();
    }

    private void inicializarMapa() {
        if (!tienePermisoUbicacion()) return;
        if (!mapaInicializado) {
            Configuration.getInstance().setUserAgentValue(getPackageName());
            mapView = new MapView(this);
            mapView.setTileSource(TileSourceFactory.MAPNIK);
            mapView.setMultiTouchControls(true);
            mapView.setBuiltInZoomControls(false);
            mapView.setMinZoomLevel(17.0);
            mapView.setMaxZoomLevel(20.0);
            mapView.getController().setZoom(16.5);
            mapContainer.removeAllViews();
            mapContainer.addView(mapView, new FrameLayout.LayoutParams(
                    FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT));
            mapView.getOverlays().add(new CopyrightOverlay(this));
            mapaInicializado = true;
        }
        overlayLoading.setVisibility(View.VISIBLE);
        tvMapEstado.setText("Buscando tu ubicación...");
        iniciarActualizacionesUbicacion();
        handler.removeCallbacks(refrescarVendedoresRunnable);
        handler.post(refrescarVendedoresRunnable);
    }

    private void iniciarActualizacionesUbicacion() {
        if (!tienePermisoUbicacion() || locationManager == null && (locationManager = (LocationManager) getSystemService(LOCATION_SERVICE)) == null) return;
        if (locationListener == null) {
            locationListener = new LocationListener() {
                @Override public void onLocationChanged(@NonNull Location location) { procesarUbicacion(location); }
                @Override public void onProviderEnabled(@NonNull String provider) { }
                @Override public void onProviderDisabled(@NonNull String provider) { }
            };
        }
        try {
            String provider = null;
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED
                    && locationManager.isProviderEnabled(LocationManager.GPS_PROVIDER)) {
                provider = LocationManager.GPS_PROVIDER;
            } else if (locationManager.isProviderEnabled(LocationManager.NETWORK_PROVIDER)) {
                provider = LocationManager.NETWORK_PROVIDER;
            }
            if (provider == null) {
                overlayLoading.setVisibility(View.GONE);
                Toast.makeText(this, "Activa la ubicación del dispositivo para centrar el mapa", Toast.LENGTH_LONG).show();
                return;
            }
            Location ultima = locationManager.getLastKnownLocation(provider);
            if (ultima != null) procesarUbicacion(ultima);
            locationManager.removeUpdates(locationListener);
            locationManager.requestLocationUpdates(provider, AppConfig.INTERVALO_ACTUALIZACION_MS, 0f, locationListener, Looper.getMainLooper());
        } catch (SecurityException e) {
            overlayLoading.setVisibility(View.GONE);
            overlayPermission.setVisibility(View.VISIBLE);
        } catch (IllegalArgumentException e) {
            overlayLoading.setVisibility(View.GONE);
            Toast.makeText(this, "No hay un proveedor de ubicación disponible", Toast.LENGTH_LONG).show();
        }
    }

    private void detenerActualizacionesUbicacion() {
        if (locationManager != null && locationListener != null) {
            try { locationManager.removeUpdates(locationListener); } catch (SecurityException ignored) { }
        }
    }

    private void procesarUbicacion(Location location) {
        ultimaUbicacion = new Location(location);
        GeoPoint punto = new GeoPoint(location.getLatitude(), location.getLongitude());
        if (mapView == null) return;


        if (marcadorUsuario == null) {
            marcadorUsuario = new Marker(mapView);
            marcadorUsuario.setTitle("Tu ubicación");
            marcadorUsuario.setAnchor(
                    Marker.ANCHOR_CENTER,
                    Marker.ANCHOR_BOTTOM
            );

            mapView.getOverlays().add(marcadorUsuario);
        }

// Seleccionar icono según el tipo de usuario
        if ("vendedor".equalsIgnoreCase(rol)) {

            marcadorUsuario.setIcon(
                    ContextCompat.getDrawable(
                            this,
                            R.drawable.ic_mi_vendedor
                    )
            );

        } else {

            marcadorUsuario.setIcon(
                    ContextCompat.getDrawable(
                            this,
                            R.drawable.ic_mi_cliente
                    )
            );
        }

// Actualizar posición GPS
        marcadorUsuario.setPosition(punto);

        if (circuloBusqueda == null) {
            circuloBusqueda = new Polygon(mapView);
            circuloBusqueda.setFillColor(0x223D8B70);
            circuloBusqueda.setStrokeColor(0xAA3D8B70);
            circuloBusqueda.setStrokeWidth(2f);
            mapView.getOverlays().add(0, circuloBusqueda);
        }
        circuloBusqueda.setPoints(Polygon.pointsAsCircle(punto, RADIO_METROS));

        if (!centroInicialEstablecido) {
            mapView.getController().setZoom(15.5);
            mapView.getController().setCenter(punto);
            limitarMapaAlrededor(location.getLatitude(), location.getLongitude());
            centroInicialEstablecido = true;
            overlayLoading.setVisibility(View.GONE);
        }
        mapView.invalidate();

        if ("vendedor".equalsIgnoreCase(rol) && enRuta) {
            long ahora = System.currentTimeMillis();
            if (ahora - ultimaActualizacionServidorMs >= AppConfig.INTERVALO_ACTUALIZACION_MS) {
                ultimaActualizacionServidorMs = ahora;
                enviarUbicacionServidor(location.getLatitude(), location.getLongitude());
            }
        }
    }

    private void enviarUbicacionServidor(double lat, double lng) {
        UbicacionMiaRequest req = new UbicacionMiaRequest(lat, lng);
        apiService.actualizarUbicacionMia(req).enqueue(new Callback<UbicacionMiaResponse>() {
            @Override
            public void onResponse(@NonNull Call<UbicacionMiaResponse> call, @NonNull Response<UbicacionMiaResponse> response) {
                if (BuildConfig.DEBUG && !loggedUbicacionMia) {
                    loggedUbicacionMia = true;
                    Log.d(TAG, "PUT /api/ubicacion/mia HTTP " + response.code() + " msg=" + response.message());
                }
            }

            @Override
            public void onFailure(@NonNull Call<UbicacionMiaResponse> call, @NonNull Throwable t) {
                Log.e(TAG, "Error actualizando ubicación en el servidor", t);
            }
        });
    }

    private void enviarFueraDeRutaServidor() {
        apiService.fueraDeRuta().enqueue(new Callback<FueraDeRutaResponse>() {
            @Override
            public void onResponse(@NonNull Call<FueraDeRutaResponse> call, @NonNull Response<FueraDeRutaResponse> response) {
                if (BuildConfig.DEBUG && !loggedFueraDeRuta) {
                    loggedFueraDeRuta = true;
                    Log.d(TAG, "PUT /api/ubicacion/mia/fuera-de-ruta HTTP " + response.code());
                }
            }

            @Override
            public void onFailure(@NonNull Call<FueraDeRutaResponse> call, @NonNull Throwable t) {
                Log.e(TAG, "Error enviando fuera de ruta al servidor", t);
            }
        });
    }

    private void limitarMapaAlrededor(double latitud, double longitud) {
        if (mapView == null) return;
        double deltaLat = LIMITE_MAPA_METROS / 111320.0;
        double cosLat = Math.max(0.15, Math.cos(Math.toRadians(latitud)));
        double deltaLon = LIMITE_MAPA_METROS / (111320.0 * cosLat);
        BoundingBox limites = new BoundingBox(
                Math.min(85.0, latitud + deltaLat), Math.min(180.0, longitud + deltaLon),
                Math.max(-85.0, latitud - deltaLat), Math.max(-180.0, longitud - deltaLon));
        mapView.setScrollableAreaLimitDouble(limites);
    }

    private void refrescarVendedoresCercanos() {
        if (ultimaUbicacion == null || mapView == null || isFinishing()) return;
        double lat = ultimaUbicacion.getLatitude();
        double lng = ultimaUbicacion.getLongitude();

        String cat = filtrosAntojitos.isEmpty() ? null : filtrosAntojitos.iterator().next();
        String buscar = textoBusquedaMapa.isEmpty() ? null : textoBusquedaMapa;

        apiService.obtenerCercanos(lat, lng, AppConfig.RADIO_KM, null, buscar).enqueue(new Callback<CercanosResponse>() {
            @Override
            public void onResponse(@NonNull Call<CercanosResponse> call, @NonNull Response<CercanosResponse> response) {
                if (response.isSuccessful() && response.body() != null) {
                    if (BuildConfig.DEBUG && !loggedCercanos) {
                        loggedCercanos = true;
                        Log.d(TAG, "GET /api/ubicacion/cercanos HTTP " + response.code() + " vendedores=" + (response.body().vendedores != null ? response.body().vendedores.size() : 0));
                    }
                    dibujarVendedores(response.body().vendedores, lat, lng);
                }
            }

            @Override
            public void onFailure(@NonNull Call<CercanosResponse> call, @NonNull Throwable t) {
                Log.e(TAG, "Error obteniendo vendedores cercanos", t);
            }
        });
    }

    private void dibujarVendedores(List<VendedorCercanoDto> vendedores, double latCliente, double lonCliente) {
        if (mapView == null || isFinishing()) return;
        if (vendedores == null) vendedores = new ArrayList<>();


        Set<Integer> idsVisibles = new HashSet<>();

        for (VendedorCercanoDto vendedor : vendedores) {

            // No mostrar nuestra propia ubicación como vendedor cercano
            if (vendedor.idUsuario == usuarioId) continue;

            // Calcular la distancia real entre el usuario y el vendedor
            float[] distancia = new float[1];

            Location.distanceBetween(
                    latCliente,
                    lonCliente,
                    vendedor.latitud,
                    vendedor.longitud,
                    distancia
            );

            // Excluir vendedores que estén fuera de los 500 metros
            if (distancia[0] > RADIO_METROS) continue;

            // Registrar únicamente los vendedores dentro del radio
            idsVisibles.add(vendedor.idUsuario);

            GeoPoint punto = new GeoPoint(
                    vendedor.latitud,
                    vendedor.longitud
            );

            Marker marker = marcadoresVendedores.get(vendedor.idUsuario);
            if (marker == null) {
                marker = new Marker(mapView);
                marker.setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_BOTTOM);
                marker.setIcon(
                        ContextCompat.getDrawable(
                                this,
                                R.drawable.ic_vendedor_mapa
                        )
                );
                final VendedorCercanoDto vFinal = vendedor;
                marker.setOnMarkerClickListener((m, map) -> {
                    mostrarTarjetaVendedor(vFinal);
                    return true;
                });
                marcadoresVendedores.put(vendedor.idUsuario, marker);
                mapView.getOverlays().add(marker);
            }
            marker.setPosition(punto);
            marker.setTitle(vendedor.nombreCompleto == null ? "Vendedor cercano" : vendedor.nombreCompleto);
            marker.setSnippet(String.format(java.util.Locale.getDefault(), "A %.0f m de ti · En ruta", vendedor.distanciaM));
        }

        Set<Integer> quitar = new HashSet<>(marcadoresVendedores.keySet());
        quitar.removeAll(idsVisibles);
        for (Integer id : quitar) {
            Marker marker = marcadoresVendedores.remove(id);
            if (marker != null) mapView.getOverlays().remove(marker);
        }
        mapView.invalidate();
    }

    private void mostrarTarjetaVendedor(VendedorCercanoDto vendedor) {
        if (cardVendedor == null) return;
        if (cardVendorAvatar != null) {
            ImagenPerfil.mostrar(cardVendorAvatar, vendedor.fotoPerfil, this);
        }
        if (cardVendorName != null) {
            cardVendorName.setText(vendedor.nombreCompleto == null ? "Puesto de Antojitos" : vendedor.nombreCompleto);
        }
        if (cardVendorDistance != null) {
            cardVendorDistance.setText(String.format(java.util.Locale.getDefault(), "A %.0f m de ti · En ruta activa", vendedor.distanciaM));
        }
        if (cardVendorDesc != null) {
            cardVendorDesc.setText("premium".equalsIgnoreCase(plan)
                    ? "Plan Premium: ves la ubicación actual y el recorrido trazado del día."
                    : "Plan Gratuito: solo se muestra la ubicación actual del vendedor.");
        }
        actualizarRutaClienteEnMapa(vendedor);
        if (cardBtnCatalog != null) {
            cardBtnCatalog.setOnClickListener(v -> {
                Intent i = new Intent(this, CatalogoPublicoActivity.class);
                i.putExtra("vendedor_id", vendedor.idUsuario);
                i.putExtra("plan_cliente", plan);
                i.putExtra("vendedor_nombre", vendedor.nombreCompleto);
                startActivity(i);
            });
        }
        if (cardBtnProfile != null) {
            cardBtnProfile.setOnClickListener(v -> {
                Intent i = new Intent(this, VendedorDetailActivity.class);
                i.putExtra("vendedor_id", vendedor.idUsuario);
                i.putExtra("plan_cliente", plan);
                i.putExtra("vendedor_nombre", vendedor.nombreCompleto);
                i.putExtra("vendedor_foto", vendedor.fotoPerfil);
                startActivity(i);
            });
        }
        cardVendedor.setVisibility(View.VISIBLE);
    }

    private void limpiarRutaClienteEnMapa() {
        if (mapView == null) return;
        if (rutaVendedorOverlay != null) {
            mapView.getOverlays().remove(rutaVendedorOverlay);
            rutaVendedorOverlay = null;
        }
        for (Marker m : paradasRutaOverlay) {
            mapView.getOverlays().remove(m);
        }
        paradasRutaOverlay.clear();
        mapView.invalidate();
    }

    private void actualizarRutaClienteEnMapa(VendedorCercanoDto vendedor) {
        limpiarRutaClienteEnMapa();
        if (mapView == null || !"premium".equalsIgnoreCase(plan)) {
            return; // Cliente Gratuito (RF14): solo ubicación actual
        }
        apiService.obtenerRutasVendedor(vendedor.idUsuario).enqueue(new Callback<RutasResponse>() {
            @Override
            public void onResponse(@NonNull Call<RutasResponse> call, @NonNull Response<RutasResponse> response) {
                if (response.isSuccessful() && response.body() != null && response.body().rutas != null && !response.body().rutas.isEmpty()) {
                    RutaDto elegida = elegirRutaDelDiaDto(response.body().rutas);
                    List<GeoPoint> geoPoints = new ArrayList<>();
                    if (elegida != null && elegida.waypoints != null) {
                        for (WaypointDto w : elegida.waypoints) {
                            geoPoints.add(new GeoPoint(w.latitud, w.longitud));
                        }
                    }
                    if (geoPoints.size() >= 2) {
                        dibujarRutaEnMapa(geoPoints);
                        return;
                    }
                }
                cargarRutaLocalCliente(vendedor.idUsuario);
            }

            @Override
            public void onFailure(@NonNull Call<RutasResponse> call, @NonNull Throwable t) {
                cargarRutaLocalCliente(vendedor.idUsuario);
            }
        });
    }

    private String diaActualEsp() {
        int dow = java.util.Calendar.getInstance().get(java.util.Calendar.DAY_OF_WEEK);
        switch (dow) {
            case java.util.Calendar.MONDAY: return "Lunes";
            case java.util.Calendar.TUESDAY: return "Martes";
            case java.util.Calendar.WEDNESDAY: return "Miércoles";
            case java.util.Calendar.THURSDAY: return "Jueves";
            case java.util.Calendar.FRIDAY: return "Viernes";
            case java.util.Calendar.SATURDAY: return "Sábado";
            default: return "Domingo";
        }
    }

    private RutaDto elegirRutaDelDiaDto(List<RutaDto> rutas) {
        String hoy = diaActualEsp();
        for (RutaDto r : rutas) {
            if (r.diaSemana != null && r.diaSemana.equalsIgnoreCase(hoy)) return r;
        }
        return rutas.get(0);
    }

    private void cargarRutaLocalCliente(int vendedorId) {
        executor.execute(() -> {
            List<Ruta> rutas = AppDatabase.getInstance(this).rutaDao().obtenerPorVendedor(vendedorId);
            List<GeoPoint> geoPoints = new ArrayList<>();
            if (rutas != null && !rutas.isEmpty()) {
                String hoy = diaActualEsp();
                Ruta elegida = rutas.get(0);
                for (Ruta r : rutas) {
                    if (r.diaSemana.equalsIgnoreCase(hoy)) {
                        elegida = r;
                        break;
                    }
                }
                try {
                    java.lang.reflect.Type t = new TypeToken<List<RutasActivity.PuntoRuta>>() {}.getType();
                    List<RutasActivity.PuntoRuta> pts = new Gson().fromJson(elegida.puntosJson, t);
                    if (pts != null) {
                        for (RutasActivity.PuntoRuta p : pts) {
                            geoPoints.add(new GeoPoint(p.lat, p.lng));
                        }
                    }
                } catch (Exception ignored) { }
            }
            runOnUiThread(() -> dibujarRutaEnMapa(geoPoints));
        });
    }

    private void dibujarRutaEnMapa(List<GeoPoint> geoPoints) {
        if (mapView == null || isFinishing() || geoPoints == null || geoPoints.size() < 2) return;
        limpiarRutaClienteEnMapa();
        rutaVendedorOverlay = new Polyline(mapView);
        rutaVendedorOverlay.setPoints(geoPoints);
        rutaVendedorOverlay.getOutlinePaint().setColor(getColor(R.color.tamalon_field_text));
        rutaVendedorOverlay.getOutlinePaint().setStrokeWidth(7f);
        mapView.getOverlays().add(rutaVendedorOverlay);
        for (int i = 0; i < geoPoints.size(); i++) {
            Marker stop = new Marker(mapView);
            stop.setPosition(geoPoints.get(i));
            stop.setTitle("Parada " + (i + 1));
            stop.setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_BOTTOM);
            stop.setIcon(ContextCompat.getDrawable(this, R.drawable.ic_vendedor_mapa));
            paradasRutaOverlay.add(stop);
            mapView.getOverlays().add(stop);
        }
        mapView.invalidate();
    }

    private void construirBotonesPreferencias() {
        if (categoryFiltersContainer == null) return;
        categoryFiltersContainer.removeAllViews();
        for (String categoria : categoriasAntojitos) {
            Button chip = new Button(this);
            chip.setText(categoria);
            chip.setAllCaps(false);
            chip.setTextSize(11f);
            chip.setTypeface(null, Typeface.BOLD);
            chip.setMinHeight(0);
            chip.setMinimumHeight(0);
            chip.setMinWidth(0);
            chip.setMinimumWidth(0);
            chip.setPadding(dp(15), dp(5), dp(15), dp(5));
            LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.WRAP_CONTENT, dp(38));
            params.setMargins(dp(4), 0, dp(4), 0);
            chip.setLayoutParams(params);
            actualizarEstiloPreferencia(chip, categoria);
            chip.setOnClickListener(v -> {
                if (filtrosAntojitos.contains(categoria)) filtrosAntojitos.remove(categoria);
                else filtrosAntojitos.add(categoria);
                getSharedPreferences("tamalon_map_filters_" + usuarioId, MODE_PRIVATE).edit()
                        .putStringSet("categorias", new HashSet<>(filtrosAntojitos)).apply();
                actualizarEstiloPreferencia(chip, categoria);
                refrescarVendedoresCercanos();
            });
            categoryFiltersContainer.addView(chip);
        }
    }

    private void actualizarEstiloPreferencia(Button boton, String categoria) {
        boolean seleccionado = filtrosAntojitos.contains(categoria);
        int fondo = seleccionado ? getColor(R.color.tamalon_sand_strong) : getColor(R.color.tamalon_surface);
        int texto = getColor(R.color.tamalon_field_text);
        int borde = seleccionado ? getColor(R.color.tamalon_heading_muted) : getColor(R.color.tamalon_line);
        GradientDrawable forma = new GradientDrawable();
        forma.setColor(fondo);
        forma.setCornerRadius(dp(22));
        forma.setStroke(dp(1), borde);
        RippleDrawable fondoInteractivo = new RippleDrawable(
                ColorStateList.valueOf(getColor(R.color.tamalon_sand)), forma, null);
        boton.setBackground(fondoInteractivo);
        boton.setBackgroundTintList(null);
        boton.setTextColor(texto);
        boton.setElevation(seleccionado ? dp(2) : 0);
        boton.setAlpha(1f);
        boton.setContentDescription(categoria + (seleccionado ? ", seleccionado" : ", no seleccionado"));
    }

    private int dp(float valor) {
        return Math.round(valor * getResources().getDisplayMetrics().density);
    }

    private void actualizarVistaEstadoRuta() {
        if (vendorRouteStateText != null) {
            vendorRouteStateText.setText(enRuta ? "En ruta" : "Fuera de ruta");
        }
        if (btnToggleRuta != null) {
            btnToggleRuta.setText(enRuta ? "Desactivar ruta" : "Activar ruta");
        }
        if (vendorRouteDot != null) {
            vendorRouteDot.setBackgroundTintList(ColorStateList.valueOf(
                    enRuta ? 0xFF2E7D32 : 0xFF9E9E9E));
        }
        if (vendorRouteExplanation != null) {
            vendorRouteExplanation.setText(enRuta
                    ? "En ruta: estás ofreciendo tu ubicación en tiempo real a clientes cercanos."
                    : "Fuera de ruta: no estás ofreciendo tu ubicación en el mapa.");
        }
    }

    private void toggleDisponibilidad() {
        if (!tienePermisoUbicacion()) {
            Toast.makeText(this, "Activa el permiso de ubicación para iniciar tu ruta", Toast.LENGTH_LONG).show();
            solicitarPermisoUbicacion();
            return;
        }
        if (!enRuta && ultimaUbicacion == null) {
            Toast.makeText(this, "Espera a que el mapa encuentre tu ubicación y vuelve a intentarlo", Toast.LENGTH_LONG).show();
            return;
        }
        enRuta = !enRuta;
        actualizarVistaEstadoRuta();
        if (enRuta && ultimaUbicacion != null) {
            enviarUbicacionServidor(ultimaUbicacion.getLatitude(), ultimaUbicacion.getLongitude());
        } else if (!enRuta) {
            enviarFueraDeRutaServidor();
            refrescarVendedoresCercanos();
        }
    }

    private void abrirConfiguracionPermisos() {
        Intent intent = new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                Uri.fromParts("package", getPackageName(), null));
        startActivity(intent);
    }

    public void abrirPerfilVendedor(int vendedorId) {
        Intent intent = new Intent(this, VendedorDetailActivity.class);
        intent.putExtra("vendedor_id", vendedorId);
        intent.putExtra("plan_cliente", plan);
        startActivity(intent);
    }
}
