package com.tamalon.app.ui;

import android.content.Intent;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import com.tamalon.app.R;
import com.tamalon.app.data.local.AppDatabase;
import com.tamalon.app.model.Ruta;
import com.tamalon.app.model.Usuario;
import com.tamalon.app.network.ApiClient;
import com.tamalon.app.network.ApiService;
import com.tamalon.app.network.CrearRutaRequest;
import com.tamalon.app.network.RutaDto;
import com.tamalon.app.network.RutasResponse;
import com.tamalon.app.network.WaypointDto;
import com.tamalon.app.service.AutenticacionService;
import com.tamalon.app.validation.ResultadoValidacion;
import com.tamalon.app.validation.ValidadorRuta;

import org.osmdroid.config.Configuration;
import org.osmdroid.events.MapEventsReceiver;
import org.osmdroid.tileprovider.tilesource.TileSourceFactory;
import org.osmdroid.util.GeoPoint;
import org.osmdroid.views.MapView;
import org.osmdroid.views.overlay.MapEventsOverlay;
import org.osmdroid.views.overlay.Marker;
import org.osmdroid.views.overlay.Polyline;

import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

/**
 * Gestión de rutas predeterminadas del Vendedor asociadas a días de la semana (RF13).
 * Sincroniza con las tablas `ruta` y `waypoint` (API REST + caché local Room).
 */
public class RutasActivity extends AppCompatActivity {

    private static final String[] DIAS_SEMANA = {
            "Lunes", "Martes", "Miércoles", "Jueves", "Viernes", "Sábado", "Domingo"
    };

    public static class PuntoRuta {
        public double lat;
        public double lng;
        public PuntoRuta() {}
        public PuntoRuta(double lat, double lng) {
            this.lat = lat;
            this.lng = lng;
        }
    }

    private final ExecutorService executor = Executors.newSingleThreadExecutor();
    private final Gson gson = new Gson();
    private AppDatabase db;
    private ApiService apiService;
    private int usuarioId = -1;
    private String rol = "vendedor", plan = "gratuito";

    private TextView tvCounter, tvFormTitle, tvPointsInfo, tvFormError;
    private Button btnAdd;
    private View premiumLock, formPanel, emptyState;
    private FrameLayout mapContainer;
    private EditText etName, etSchedule;
    private LinearLayout daysContainer;
    private RecyclerView recycler;
    private RutaAdapter adapter;

    private MapView routeMap;
    private final List<PuntoRuta> puntosEnEdicion = new ArrayList<>();
    private final List<Ruta> rutasActuales = new ArrayList<>();
    private final List<Button> dayButtons = new ArrayList<>();
    private String diaSeleccionado = "";
    private Ruta rutaEnEdicion = null;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_routes);

        db = AppDatabase.getInstance(this);
        apiService = ApiClient.getApiService(this);
        usuarioId = getIntent().getIntExtra("usuario_id", -1);
        if (getIntent().getStringExtra("rol") != null) rol = getIntent().getStringExtra("rol");
        if (getIntent().getStringExtra("plan") != null) plan = getIntent().getStringExtra("plan");

        tvCounter = findViewById(R.id.routes_counter_badge);
        btnAdd = findViewById(R.id.routes_btn_add);
        premiumLock = findViewById(R.id.routes_premium_lock);
        formPanel = findViewById(R.id.routes_form_panel);
        tvFormTitle = findViewById(R.id.routes_form_title);
        tvPointsInfo = findViewById(R.id.routes_points_info);
        mapContainer = findViewById(R.id.routes_map_container);
        etName = findViewById(R.id.routes_form_name);
        etSchedule = findViewById(R.id.routes_form_schedule);
        daysContainer = findViewById(R.id.routes_days_container);
        tvFormError = findViewById(R.id.routes_form_error);
        recycler = findViewById(R.id.routes_recycler);
        emptyState = findViewById(R.id.routes_empty_state);

        recycler.setLayoutManager(new LinearLayoutManager(this));
        adapter = new RutaAdapter();
        recycler.setAdapter(adapter);

        if (btnAdd != null) {
            btnAdd.setBackgroundTintList(null);
            btnAdd.setOnClickListener(v -> abrirFormularioNueva());
        }
        View btnUpgrade = findViewById(R.id.routes_btn_upgrade);
        if (btnUpgrade != null) {
            if (btnUpgrade instanceof Button) ((Button) btnUpgrade).setBackgroundTintList(null);
            btnUpgrade.setOnClickListener(v -> {
                Intent i = new Intent(this, PagoActivity.class);
                i.putExtra("usuario_id", usuarioId);
                i.putExtra("rol", rol);
                startActivity(i);
            });
        }
        View btnUndo = findViewById(R.id.routes_btn_undo_point);
        if (btnUndo != null) {
            if (btnUndo instanceof Button) ((Button) btnUndo).setBackgroundTintList(null);
            btnUndo.setOnClickListener(v -> {
                if (!puntosEnEdicion.isEmpty()) {
                    puntosEnEdicion.remove(puntosEnEdicion.size() - 1);
                    redibujarMapaTrazado();
                }
            });
        }
        View btnClear = findViewById(R.id.routes_btn_clear_points);
        if (btnClear != null) {
            if (btnClear instanceof Button) ((Button) btnClear).setBackgroundTintList(null);
            btnClear.setOnClickListener(v -> {
                puntosEnEdicion.clear();
                redibujarMapaTrazado();
            });
        }
        View btnCancel = findViewById(R.id.routes_form_cancel);
        if (btnCancel != null) {
            if (btnCancel instanceof Button) ((Button) btnCancel).setBackgroundTintList(null);
            btnCancel.setOnClickListener(v -> cerrarFormulario());
        }
        View btnSave = findViewById(R.id.routes_form_save);
        if (btnSave != null) {
            if (btnSave instanceof Button) ((Button) btnSave).setBackgroundTintList(null);
            btnSave.setOnClickListener(v -> guardarRuta());
        }

        BottomNavigation.configurar(this, findViewById(android.R.id.content), usuarioId, rol, plan);
        construirBotonesDias();

        new AutenticacionService(this).obtenerUsuarioActivo(new AutenticacionService.Callback<Usuario>() {
            @Override
            public void onExito(Usuario u) {
                runOnUiThread(() -> {
                    usuarioId = u.id;
                    if (u.rol != null) rol = u.rol;
                    if (u.plan != null) plan = u.plan;
                    BottomNavigation.configurar(RutasActivity.this, findViewById(android.R.id.content), usuarioId, rol, plan);
                    actualizarEstadoPlan();
                });
            }

            @Override
            public void onError(String m) {
                runOnUiThread(() -> actualizarEstadoPlan());
            }
        });
    }

    private boolean esPremium() {
        return "premium".equalsIgnoreCase(plan);
    }

    private void actualizarEstadoPlan() {
        boolean prem = esPremium();
        if (premiumLock != null) premiumLock.setVisibility(prem ? View.GONE : View.VISIBLE);
        if (btnAdd != null) btnAdd.setVisibility(prem ? View.VISIBLE : View.GONE);
        if (prem) {
            cargarRutas();
        } else {
            if (emptyState != null) emptyState.setVisibility(View.GONE);
            if (recycler != null) recycler.setVisibility(View.GONE);
        }
    }

    private void inicializarMapaTrazado() {
        if (routeMap != null || mapContainer == null) return;
        Configuration.getInstance().setUserAgentValue(getPackageName());
        routeMap = new MapView(this);
        routeMap.setTileSource(TileSourceFactory.MAPNIK);
        routeMap.setMultiTouchControls(true);
        routeMap.setBuiltInZoomControls(false);
        routeMap.getController().setZoom(15.5);
        routeMap.getController().setCenter(new GeoPoint(19.4326, -99.1332));

        MapEventsReceiver receiver = new MapEventsReceiver() {
            @Override
            public boolean singleTapConfirmedHelper(GeoPoint p) {
                if (puntosEnEdicion.size() >= ValidadorRuta.PUNTOS_MAX) {
                    Toast.makeText(RutasActivity.this, "Máximo " + ValidadorRuta.PUNTOS_MAX + " puntos por ruta", Toast.LENGTH_SHORT).show();
                    return true;
                }
                puntosEnEdicion.add(new PuntoRuta(p.getLatitude(), p.getLongitude()));
                redibujarMapaTrazado();
                return true;
            }

            @Override
            public boolean longPressHelper(GeoPoint p) {
                return false;
            }
        };
        routeMap.getOverlays().add(new MapEventsOverlay(receiver));
        routeMap.setOnTouchListener((v, event) -> {
            if (v.getParent() != null) {
                v.getParent().requestDisallowInterceptTouchEvent(true);
            }
            return false;
        });
        mapContainer.removeAllViews();
        mapContainer.addView(routeMap, new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT));
    }

    private void redibujarMapaTrazado() {
        if (tvPointsInfo != null) {
            tvPointsInfo.setText("Puntos trazados: " + puntosEnEdicion.size() + " (toca el mapa para agregar; mín. 2)");
        }
        if (routeMap == null) return;
        for (int i = routeMap.getOverlays().size() - 1; i >= 0; i--) {
            if (!(routeMap.getOverlays().get(i) instanceof MapEventsOverlay)) {
                routeMap.getOverlays().remove(i);
            }
        }
        if (puntosEnEdicion.size() >= 2) {
            Polyline line = new Polyline(routeMap);
            List<GeoPoint> geoPoints = new ArrayList<>();
            for (PuntoRuta pt : puntosEnEdicion) {
                geoPoints.add(new GeoPoint(pt.lat, pt.lng));
            }
            line.setPoints(geoPoints);
            line.getOutlinePaint().setColor(getColor(R.color.tamalon_field_text));
            line.getOutlinePaint().setStrokeWidth(8f);
            routeMap.getOverlays().add(line);
        }
        for (int i = 0; i < puntosEnEdicion.size(); i++) {
            PuntoRuta pt = puntosEnEdicion.get(i);
            Marker m = new Marker(routeMap);
            m.setPosition(new GeoPoint(pt.lat, pt.lng));
            m.setTitle("Punto " + (i + 1));
            m.setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_BOTTOM);
            m.setIcon(ContextCompat.getDrawable(this, R.drawable.ic_vendedor_mapa));
            routeMap.getOverlays().add(m);
        }
        if (!puntosEnEdicion.isEmpty()) {
            PuntoRuta ultimo = puntosEnEdicion.get(puntosEnEdicion.size() - 1);
            routeMap.getController().animateTo(new GeoPoint(ultimo.lat, ultimo.lng));
        }
        routeMap.invalidate();
    }

    private void construirBotonesDias() {
        if (daysContainer == null) return;
        daysContainer.removeAllViews();
        dayButtons.clear();
        LinearLayout fila = null;
        for (int i = 0; i < DIAS_SEMANA.length; i++) {
            if (i % 4 == 0) {
                fila = new LinearLayout(this);
                fila.setOrientation(LinearLayout.HORIZONTAL);
                LinearLayout.LayoutParams rLp = new LinearLayout.LayoutParams(
                        LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
                rLp.bottomMargin = dp(6);
                daysContainer.addView(fila, rLp);
            }
            final String dia = DIAS_SEMANA[i];
            Button btn = new Button(this);
            btn.setTag(dia);
            btn.setText(dia.substring(0, 3));
            btn.setAllCaps(false);
            btn.setTextSize(10f);
            btn.setMinHeight(0);
            btn.setMinimumHeight(0);
            btn.setBackgroundTintList(null);
            LinearLayout.LayoutParams bLp = new LinearLayout.LayoutParams(0, dp(34), 1f);
            if (i % 4 < 3) bLp.setMarginEnd(dp(6));
            btn.setLayoutParams(bLp);
            btn.setOnClickListener(v -> {
                diaSeleccionado = dia;
                actualizarBotonesDias();
            });
            dayButtons.add(btn);
            fila.addView(btn);
        }
        actualizarBotonesDias();
    }

    private void actualizarBotonesDias() {
        for (Button btn : dayButtons) {
            String d = (String) btn.getTag();
            boolean activa = d != null && d.equalsIgnoreCase(diaSeleccionado);
            GradientDrawable bg = new GradientDrawable();
            bg.setColor(getColor(activa ? R.color.tamalon_sand_strong : R.color.tamalon_surface));
            bg.setCornerRadius(dp(14));
            bg.setStroke(dp(1), getColor(activa ? R.color.tamalon_heading_muted : R.color.tamalon_line));
            btn.setBackground(bg);
            btn.setBackgroundTintList(null);
            btn.setTextColor(getColor(activa ? R.color.tamalon_ink : R.color.tamalon_field_text));
            btn.setTypeface(null, activa ? Typeface.BOLD : Typeface.NORMAL);
        }
    }

    private void abrirFormularioNueva() {
        if (rutasActuales.size() >= ValidadorRuta.RUTAS_MAX_PREMIUM) {
            Toast.makeText(this, "Límite alcanzado: máximo 3 rutas programadas", Toast.LENGTH_LONG).show();
            return;
        }
        rutaEnEdicion = null;
        if (tvFormTitle != null) tvFormTitle.setText("Trazar nueva ruta");
        if (etName != null) etName.setText("");
        if (etSchedule != null) etSchedule.setText("08:00 a 13:00");
        diaSeleccionado = "";
        puntosEnEdicion.clear();
        actualizarBotonesDias();
        if (tvFormError != null) tvFormError.setVisibility(View.GONE);
        if (formPanel != null) formPanel.setVisibility(View.VISIBLE);
        inicializarMapaTrazado();
        redibujarMapaTrazado();
    }

    private void abrirFormularioEditar(Ruta ruta) {
        rutaEnEdicion = ruta;
        if (tvFormTitle != null) tvFormTitle.setText("Editar ruta");
        if (etName != null) etName.setText(ruta.nombre);
        if (etSchedule != null) etSchedule.setText(ruta.horario);
        diaSeleccionado = ruta.diaSemana;
        puntosEnEdicion.clear();
        puntosEnEdicion.addAll(parsearPuntos(ruta.puntosJson));
        actualizarBotonesDias();
        if (tvFormError != null) tvFormError.setVisibility(View.GONE);
        if (formPanel != null) formPanel.setVisibility(View.VISIBLE);
        inicializarMapaTrazado();
        redibujarMapaTrazado();
    }

    private void cerrarFormulario() {
        rutaEnEdicion = null;
        if (formPanel != null) formPanel.setVisibility(View.GONE);
        if (tvFormError != null) tvFormError.setVisibility(View.GONE);
    }

    private void guardarRuta() {
        String nombre = etName != null ? etName.getText().toString().trim() : "";
        String horario = etSchedule != null ? etSchedule.getText().toString().trim() : "";
        if (nombre.isEmpty()) {
            mostrarErrorForm("Escribe un nombre para la ruta.");
            return;
        }
        ResultadoValidacion rDia = ValidadorRuta.validarDiaSeleccionado(diaSeleccionado);
        if (!rDia.esValido()) {
            mostrarErrorForm("Selecciona un día de la semana.");
            return;
        }
        boolean conflicto = false;
        for (Ruta r : rutasActuales) {
            if (r.diaSemana.equalsIgnoreCase(diaSeleccionado) && (rutaEnEdicion == null || r.id != rutaEnEdicion.id)) {
                conflicto = true;
                break;
            }
        }
        if (conflicto) {
            mostrarErrorForm("Ya existe una ruta asignada para el día " + diaSeleccionado + ".");
            return;
        }
        ResultadoValidacion rPts = ValidadorRuta.validarCantidadPuntos(puntosEnEdicion.size());
        if (!rPts.esValido()) {
            mostrarErrorForm(rPts.getMensajeErrorResId());
            return;
        }
        if (rutaEnEdicion == null && rutasActuales.size() >= ValidadorRuta.RUTAS_MAX_PREMIUM) {
            mostrarErrorForm("Límite alcanzado: máximo 3 rutas permitidas.");
            return;
        }

        String jsonPuntos = gson.toJson(puntosEnEdicion);
        List<WaypointDto> waypoints = new ArrayList<>();
        for (int i = 0; i < puntosEnEdicion.size(); i++) {
            PuntoRuta pt = puntosEnEdicion.get(i);
            waypoints.add(new WaypointDto(i + 1, pt.lat, pt.lng));
        }
        CrearRutaRequest req = new CrearRutaRequest(usuarioId, "Activa", diaSeleccionado, nombre, horario, waypoints);

        if (rutaEnEdicion != null) {
            if (rutaEnEdicion.remoteId > 0) {
                apiService.actualizarRuta(rutaEnEdicion.remoteId, req).enqueue(new Callback<RutaDto>() {
                    @Override public void onResponse(@NonNull Call<RutaDto> call, @NonNull Response<RutaDto> response) {
                        guardarRutaLocal(nombre, horario, jsonPuntos, rutaEnEdicion.remoteId);
                    }
                    @Override public void onFailure(@NonNull Call<RutaDto> call, @NonNull Throwable t) {
                        guardarRutaLocal(nombre, horario, jsonPuntos, rutaEnEdicion.remoteId);
                    }
                });
            } else {
                guardarRutaLocal(nombre, horario, jsonPuntos, 0);
            }
        } else {
            apiService.crearRuta(req).enqueue(new Callback<RutaDto>() {
                @Override public void onResponse(@NonNull Call<RutaDto> call, @NonNull Response<RutaDto> response) {
                    int rId = (response.isSuccessful() && response.body() != null) ? response.body().idRuta : 0;
                    guardarRutaLocal(nombre, horario, jsonPuntos, rId);
                }
                @Override public void onFailure(@NonNull Call<RutaDto> call, @NonNull Throwable t) {
                    guardarRutaLocal(nombre, horario, jsonPuntos, 0);
                }
            });
        }
    }

    private void guardarRutaLocal(String nombre, String horario, String jsonPuntos, int remoteId) {
        executor.execute(() -> {
            if (rutaEnEdicion != null) {
                rutaEnEdicion.nombre = nombre;
                rutaEnEdicion.diaSemana = diaSeleccionado;
                rutaEnEdicion.horario = horario;
                rutaEnEdicion.puntosJson = jsonPuntos;
                if (remoteId > 0) rutaEnEdicion.remoteId = remoteId;
                db.rutaDao().actualizar(rutaEnEdicion);
            } else {
                Ruta nueva = new Ruta(usuarioId, nombre, diaSeleccionado, horario, jsonPuntos);
                nueva.remoteId = remoteId;
                db.rutaDao().insertar(nueva);
            }
            runOnUiThread(() -> {
                cerrarFormulario();
                Toast.makeText(this, "Ruta guardada", Toast.LENGTH_SHORT).show();
                cargarRutasDesdeRoom();
            });
        });
    }

    private void mostrarErrorForm(String msg) {
        if (tvFormError != null) {
            tvFormError.setText(msg);
            tvFormError.setVisibility(View.VISIBLE);
        }
    }

    private void cargarRutas() {
        if (usuarioId < 1) return;
        cargarRutasDesdeRoom();
        apiService.obtenerMisRutas().enqueue(new Callback<RutasResponse>() {
            @Override
            public void onResponse(@NonNull Call<RutasResponse> call, @NonNull Response<RutasResponse> response) {
                if (response.isSuccessful() && response.body() != null && response.body().rutas != null) {
                    sincronizarRutasConRoom(response.body().rutas);
                }
            }
            @Override
            public void onFailure(@NonNull Call<RutasResponse> call, @NonNull Throwable t) { }
        });
    }

    private void sincronizarRutasConRoom(List<RutaDto> apiList) {
        executor.execute(() -> {
            Set<Integer> vistos = new HashSet<>();
            for (RutaDto dto : apiList) {
                if (dto.idRuta <= 0) continue;
                vistos.add(dto.idRuta);
                List<PuntoRuta> pts = new ArrayList<>();
                if (dto.waypoints != null) {
                    for (WaypointDto w : dto.waypoints) {
                        pts.add(new PuntoRuta(w.latitud, w.longitud));
                    }
                }
                String json = gson.toJson(pts);
                Ruta existente = db.rutaDao().obtenerPorRemoteId(usuarioId, dto.idRuta);
                if (existente == null) {
                    String nom = (dto.nombre != null && !dto.nombre.trim().isEmpty()) ? dto.nombre : ("Ruta " + dto.diaSemana);
                    Ruta nueva = new Ruta(usuarioId, nom, dto.diaSemana == null ? "Lunes" : dto.diaSemana, dto.horario, json);
                    nueva.remoteId = dto.idRuta;
                    nueva.estado = dto.estado != null ? dto.estado : "Activa";
                    db.rutaDao().insertar(nueva);
                } else {
                    if (dto.nombre != null && !dto.nombre.trim().isEmpty()) existente.nombre = dto.nombre.trim();
                    if (dto.diaSemana != null) existente.diaSemana = dto.diaSemana;
                    if (dto.horario != null) existente.horario = dto.horario;
                    if (!pts.isEmpty()) existente.puntosJson = json;
                    if (dto.estado != null) existente.estado = dto.estado;
                    db.rutaDao().actualizar(existente);
                }
            }
            List<Ruta> locales = db.rutaDao().obtenerPorVendedor(usuarioId);
            for (Ruta local : locales) {
                if (local.remoteId > 0 && !vistos.contains(local.remoteId)) {
                    db.rutaDao().eliminarPorId(local.id);
                }
            }
            cargarRutasDesdeRoom();
        });
    }

    private void cargarRutasDesdeRoom() {
        if (usuarioId < 1) return;
        executor.execute(() -> {
            List<Ruta> lista = db.rutaDao().obtenerPorVendedor(usuarioId);
            runOnUiThread(() -> {
                rutasActuales.clear();
                rutasActuales.addAll(lista);
                adapter.setItems(rutasActuales);
                if (tvCounter != null) {
                    tvCounter.setText(rutasActuales.size() + " de " + ValidadorRuta.RUTAS_MAX_PREMIUM + " rutas");
                }
                if (emptyState != null) {
                    emptyState.setVisibility(rutasActuales.isEmpty() ? View.VISIBLE : View.GONE);
                }
                if (recycler != null) {
                    recycler.setVisibility(rutasActuales.isEmpty() ? View.GONE : View.VISIBLE);
                }
            });
        });
    }

    private void confirmarEliminar(Ruta r) {
        new AlertDialog.Builder(this)
                .setTitle("Eliminar ruta")
                .setMessage("¿Eliminar la ruta \"" + r.nombre + "\" (" + r.diaSemana + ")?")
                .setNegativeButton("Cancelar", null)
                .setPositiveButton("Eliminar", (d, w) -> eliminarRuta(r))
                .show();
    }

    private void eliminarRuta(Ruta r) {
        if (r.remoteId > 0) {
            apiService.eliminarRuta(r.remoteId).enqueue(new Callback<Void>() {
                @Override public void onResponse(@NonNull Call<Void> call, @NonNull Response<Void> response) {
                    eliminarRutaLocal(r);
                }
                @Override public void onFailure(@NonNull Call<Void> call, @NonNull Throwable t) {
                    eliminarRutaLocal(r);
                }
            });
        } else {
            eliminarRutaLocal(r);
        }
    }

    private void eliminarRutaLocal(Ruta r) {
        executor.execute(() -> {
            db.rutaDao().eliminarPorId(r.id);
            runOnUiThread(() -> {
                Toast.makeText(this, "Ruta eliminada", Toast.LENGTH_SHORT).show();
                cargarRutasDesdeRoom();
            });
        });
    }

    private List<PuntoRuta> parsearPuntos(String json) {
        try {
            Type t = new TypeToken<List<PuntoRuta>>() {}.getType();
            List<PuntoRuta> parsed = gson.fromJson(json, t);
            return parsed != null ? parsed : new ArrayList<>();
        } catch (Exception e) {
            return new ArrayList<>();
        }
    }

    private class RutaAdapter extends RecyclerView.Adapter<RutaAdapter.Holder> {
        private final List<Ruta> items = new ArrayList<>();
        void setItems(List<Ruta> next) {
            items.clear();
            items.addAll(next);
            notifyDataSetChanged();
        }

        @NonNull
        @Override
        public Holder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            LinearLayout card = new LinearLayout(parent.getContext());
            card.setOrientation(LinearLayout.VERTICAL);
            card.setPadding(dp(16), dp(14), dp(16), dp(14));
            card.setBackgroundResource(R.drawable.bg_surface_card);
            RecyclerView.LayoutParams lp = new RecyclerView.LayoutParams(
                    RecyclerView.LayoutParams.MATCH_PARENT, RecyclerView.LayoutParams.WRAP_CONTENT);
            lp.bottomMargin = dp(10);
            card.setLayoutParams(lp);

            LinearLayout top = new LinearLayout(parent.getContext());
            top.setOrientation(LinearLayout.HORIZONTAL);
            top.setGravity(Gravity.CENTER_VERTICAL);

            TextView tvTitle = new TextView(parent.getContext());
            tvTitle.setTextColor(getColor(R.color.tamalon_ink));
            tvTitle.setTextSize(14f);
            tvTitle.setTypeface(null, Typeface.BOLD);
            top.addView(tvTitle, new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f));

            TextView tvDay = new TextView(parent.getContext());
            tvDay.setTextColor(getColor(R.color.tamalon_field_text));
            tvDay.setTextSize(9.5f);
            tvDay.setTypeface(null, Typeface.BOLD);
            tvDay.setPadding(dp(8), dp(3), dp(8), dp(3));
            GradientDrawable dayBg = new GradientDrawable();
            dayBg.setColor(getColor(R.color.tamalon_sand));
            dayBg.setCornerRadius(dp(10));
            tvDay.setBackground(dayBg);
            top.addView(tvDay);

            card.addView(top);

            TextView tvSub = new TextView(parent.getContext());
            tvSub.setTextColor(getColor(R.color.tamalon_muted));
            tvSub.setTextSize(10f);
            tvSub.setPadding(0, dp(4), 0, 0);
            card.addView(tvSub);

            LinearLayout actions = new LinearLayout(parent.getContext());
            actions.setOrientation(LinearLayout.HORIZONTAL);
            LinearLayout.LayoutParams actLp = new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
            actLp.topMargin = dp(10);
            actions.setLayoutParams(actLp);

            Button btnEdit = new Button(parent.getContext());
            btnEdit.setText("Editar");
            btnEdit.setAllCaps(false);
            btnEdit.setTextSize(10f);
            btnEdit.setMinHeight(0);
            btnEdit.setMinimumHeight(0);
            btnEdit.setBackgroundTintList(null);
            GradientDrawable eBg = new GradientDrawable();
            eBg.setColor(getColor(R.color.tamalon_surface_strong));
            eBg.setCornerRadius(dp(10));
            eBg.setStroke(dp(1), getColor(R.color.tamalon_line));
            btnEdit.setBackground(eBg);
            btnEdit.setTextColor(getColor(R.color.tamalon_field_text));

            Button btnDel = new Button(parent.getContext());
            btnDel.setText("Eliminar");
            btnDel.setAllCaps(false);
            btnDel.setTextSize(10f);
            btnDel.setMinHeight(0);
            btnDel.setMinimumHeight(0);
            btnDel.setBackgroundTintList(null);
            GradientDrawable dBg = new GradientDrawable();
            dBg.setColor(getColor(R.color.tamalon_surface_strong));
            dBg.setCornerRadius(dp(10));
            dBg.setStroke(dp(1), getColor(R.color.tamalon_error));
            btnDel.setBackground(dBg);
            btnDel.setTextColor(getColor(R.color.tamalon_error));

            LinearLayout.LayoutParams p1 = new LinearLayout.LayoutParams(0, dp(36), 1f);
            p1.setMarginEnd(dp(6));
            LinearLayout.LayoutParams p2 = new LinearLayout.LayoutParams(0, dp(36), 1f);
            p2.setMarginStart(dp(6));
            actions.addView(btnEdit, p1);
            actions.addView(btnDel, p2);
            card.addView(actions);

            return new Holder(card, tvTitle, tvDay, tvSub, btnEdit, btnDel);
        }

        @Override
        public void onBindViewHolder(@NonNull Holder h, int position) {
            Ruta r = items.get(position);
            List<PuntoRuta> pts = parsearPuntos(r.puntosJson);
            h.title.setText(r.nombre);
            h.day.setText(r.diaSemana);
            String horario = (r.horario == null || r.horario.isEmpty()) ? "Horario libre" : r.horario;
            h.sub.setText(horario + " · " + pts.size() + " puntos trazados");
            h.btnEdit.setOnClickListener(v -> abrirFormularioEditar(r));
            h.btnDel.setOnClickListener(v -> confirmarEliminar(r));
        }

        @Override
        public int getItemCount() {
            return items.size();
        }

        class Holder extends RecyclerView.ViewHolder {
            TextView title, day, sub;
            Button btnEdit, btnDel;
            Holder(View v, TextView title, TextView day, TextView sub, Button btnEdit, Button btnDel) {
                super(v);
                this.title = title;
                this.day = day;
                this.sub = sub;
                this.btnEdit = btnEdit;
                this.btnDel = btnDel;
            }
        }
    }

    private int dp(float valor) {
        return Math.round(valor * getResources().getDisplayMetrics().density);
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (routeMap != null) routeMap.onResume();
    }

    @Override
    protected void onPause() {
        if (routeMap != null) routeMap.onPause();
        super.onPause();
    }

    @Override
    protected void onDestroy() {
        if (routeMap != null) routeMap.onDetach();
        super.onDestroy();
        executor.shutdown();
    }
}
