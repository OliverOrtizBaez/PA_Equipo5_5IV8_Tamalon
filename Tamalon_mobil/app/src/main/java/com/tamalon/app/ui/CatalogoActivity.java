package com.tamalon.app.ui;

import android.content.Intent;
import android.content.res.ColorStateList;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.text.InputFilter;
import android.util.Log;
import android.view.Gravity;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;

import com.tamalon.app.BuildConfig;
import com.tamalon.app.R;
import com.tamalon.app.data.local.AppDatabase;
import com.tamalon.app.model.Catalogo;
import com.tamalon.app.model.Producto;
import com.tamalon.app.model.Usuario;
import com.tamalon.app.network.*;
import com.tamalon.app.service.AutenticacionService;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

/**
 * Gestión de catálogos del Vendedor (RF10/RF11).
 * Reglas: nombre 1–50 caracteres, estado inicial «Visible», límite sobre catálogos «Visible»
 * (1 en Gratuito, 3 en Premium; «Oculto» no cuenta), sin IDs técnicos ni colores fuera de la paleta.
 */
public class CatalogoActivity extends AppCompatActivity {

    private static final String TAG = "CatalogoActivity";
    private static boolean loggedCatalogos = false;

    private final ExecutorService executor = Executors.newSingleThreadExecutor();
    private AppDatabase db;
    private ApiService apiService;
    private int usuarioId = -1;
    private String rol = "vendedor", plan = "gratuito";

    private ScrollView scrollView;
    private LinearLayout listContainer;
    private View emptyState, notice, form;
    private View progress1, progress2, progress3;
    private Button btnAdd, btnStateActive, btnStateInactive;
    private EditText nameInput;
    private TextView errorText, counterBadge, formTitle;

    private final List<Catalogo> catalogosActuales = new ArrayList<>();
    private final Map<Integer, Integer> conteoProductosLocal = new HashMap<>();
    private final Map<Integer, Integer> conteoProductosRemote = new HashMap<>();
    private int catalogoCount = 0;
    private int visibleCatalogCount = 0;
    private Catalogo catalogEnEdicionInline = null;
    private String estadoFormulario = "Visible";

    @Override
    protected void onCreate(Bundle state) {
        super.onCreate(state);
        setContentView(R.layout.activity_catalogs);

        usuarioId = getIntent().getIntExtra("usuario_id", -1);
        if (getIntent().getStringExtra("rol") != null) rol = getIntent().getStringExtra("rol");
        if (getIntent().getStringExtra("plan") != null) plan = getIntent().getStringExtra("plan");

        db = AppDatabase.getInstance(this);
        apiService = ApiClient.getApiService(this);

        scrollView = findViewById(R.id.catalogs_scroll_view);
        listContainer = findViewById(R.id.catalogs_list_container);
        emptyState = findViewById(R.id.catalogs_empty_state);
        notice = findViewById(R.id.catalogs_limit_notice);
        form = findViewById(R.id.catalogs_form_panel);
        nameInput = findViewById(R.id.catalogs_form_name);
        errorText = findViewById(R.id.catalogs_form_error);
        counterBadge = findViewById(R.id.catalogs_counter_badge);
        progress1 = findViewById(R.id.catalogs_progress_1);
        progress2 = findViewById(R.id.catalogs_progress_2);
        progress3 = findViewById(R.id.catalogs_progress_3);
        btnAdd = findViewById(R.id.catalogs_btn_add);
        formTitle = findViewById(R.id.catalogs_form_title);
        btnStateActive = findViewById(R.id.catalogs_form_state_active);
        btnStateInactive = findViewById(R.id.catalogs_form_state_inactive);

        if (btnAdd != null) {
            btnAdd.setBackgroundTintList(null);
            btnAdd.setOnClickListener(v -> onAddCatalogClicked());
        }

        if (btnStateActive != null) {
            btnStateActive.setBackgroundTintList(null);
            btnStateActive.setOnClickListener(v -> actualizarSelectorEstado("Visible"));
        }
        if (btnStateInactive != null) {
            btnStateInactive.setBackgroundTintList(null);
            btnStateInactive.setOnClickListener(v -> actualizarSelectorEstado("Oculto"));
        }

        View btnCancel = findViewById(R.id.catalogs_form_cancel);
        if (btnCancel != null) {
            if (btnCancel instanceof Button) ((Button) btnCancel).setBackgroundTintList(null);
            btnCancel.setOnClickListener(v -> cerrarFormulario());
        }

        View btnSave = findViewById(R.id.catalogs_form_save);
        if (btnSave != null) {
            if (btnSave instanceof Button) ((Button) btnSave).setBackgroundTintList(null);
            btnSave.setOnClickListener(v -> guardar());
        }

        BottomNavigation.configurar(this, findViewById(android.R.id.content), usuarioId, rol, plan);

        if (!"vendedor".equalsIgnoreCase(rol)) {
            Toast.makeText(this, "Esta pantalla es solo para vendedores", Toast.LENGTH_LONG).show();
            finish();
            return;
        }

        new AutenticacionService(this).obtenerUsuarioActivo(new AutenticacionService.Callback<Usuario>() {
            @Override
            public void onExito(Usuario u) {
                runOnUiThread(() -> {
                    usuarioId = u.id;
                    plan = u.plan == null ? "gratuito" : u.plan;
                    rol = u.rol == null ? "vendedor" : u.rol;
                    BottomNavigation.configurar(CatalogoActivity.this, findViewById(android.R.id.content), usuarioId, rol, plan);
                    cargarCatalogos();
                });
            }

            @Override
            public void onError(String m) {
                runOnUiThread(() -> {
                    if (usuarioId > 0) {
                        cargarCatalogos();
                    } else {
                        Toast.makeText(CatalogoActivity.this, "Inicia sesión nuevamente", Toast.LENGTH_LONG).show();
                        finish();
                    }
                });
            }
        });
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (usuarioId > 0) {
            cargarCatalogos();
        }
        refrescarPlanDesdeServidor();
    }

    private void refrescarPlanDesdeServidor() {
        apiService.obtenerMiPlan().enqueue(new Callback<MiPlanResponse>() {
            @Override
            public void onResponse(@NonNull Call<MiPlanResponse> call, @NonNull Response<MiPlanResponse> response) {
                if (response.isSuccessful() && response.body() != null && response.body().plan != null) {
                    plan = response.body().plan;
                    new TokenStore(CatalogoActivity.this).updatePlanRolTipo(plan, response.body().rol, response.body().tipo);
                    BottomNavigation.configurar(CatalogoActivity.this, findViewById(android.R.id.content), usuarioId, rol, plan);
                    actualizarEstadoLimite();
                }
            }
            @Override
            public void onFailure(@NonNull Call<MiPlanResponse> call, @NonNull Throwable t) { }
        });
    }

    private void onAddCatalogClicked() {
        if (visibleCatalogCount >= limiteVisiblesPlan()) {
            actualizarEstadoLimite();
            Toast.makeText(this, "Alcanzaste el límite de catálogos de tu plan", Toast.LENGTH_LONG).show();
            return;
        }
        abrirFormularioCrear();
    }

    private int limiteVisiblesPlan() {
        return "premium".equalsIgnoreCase(plan) ? 3 : 1;
    }

    private void abrirFormularioCrear() {
        catalogEnEdicionInline = null;
        if (formTitle != null) formTitle.setText("Nuevo catálogo");
        if (nameInput != null) {
            nameInput.setText("");
        }
        actualizarSelectorEstado("Visible");
        if (errorText != null) errorText.setVisibility(View.GONE);
        if (form != null) form.setVisibility(View.VISIBLE);
        renderLista();
    }

    private void abrirEdicionInline(Catalogo catalogo) {
        cerrarFormulario();
        catalogEnEdicionInline = catalogo;
        renderLista();
    }

    private boolean esVisible(Catalogo c) {
        String e = c.getEstadoEfectivo();
        return !"Oculto".equalsIgnoreCase(e) && !"Inactivo".equalsIgnoreCase(e) && c.visible;
    }

    private void cerrarFormulario() {
        if (form != null) form.setVisibility(View.GONE);
        if (errorText != null) errorText.setVisibility(View.GONE);
    }

    private void actualizarSelectorEstado(String estado) {
        estadoFormulario = "Oculto".equalsIgnoreCase(estado) ? "Oculto" : "Visible";
        boolean activo = "Visible".equals(estadoFormulario);
        if (btnStateActive != null) {
            btnStateActive.setBackgroundResource(activo ? R.drawable.bg_button : R.drawable.bg_surface_card);
            btnStateActive.setBackgroundTintList(null);
            btnStateActive.setTextColor(getColor(activo ? R.color.tamalon_ink : R.color.tamalon_muted));
        }
        if (btnStateInactive != null) {
            btnStateInactive.setBackgroundResource(!activo ? R.drawable.bg_button : R.drawable.bg_surface_card);
            btnStateInactive.setBackgroundTintList(null);
            btnStateInactive.setTextColor(getColor(!activo ? R.color.tamalon_ink : R.color.tamalon_muted));
        }
    }

    private void cargarCatalogos() {
        cargarDesdeRoom();
        apiService.obtenerMisCatalogos().enqueue(new Callback<CatalogosResponse>() {
            @Override
            public void onResponse(@NonNull Call<CatalogosResponse> call, @NonNull Response<CatalogosResponse> response) {
                if (response.isSuccessful() && response.body() != null) {
                    if (BuildConfig.DEBUG && !loggedCatalogos) {
                        loggedCatalogos = true;
                        Log.d(TAG, "GET /api/catalogos HTTP " + response.code() + " total=" + (response.body().catalogos != null ? response.body().catalogos.size() : 0));
                    }
                    List<CatalogoDto> apiList = response.body().catalogos != null ? response.body().catalogos : new ArrayList<>();
                    if (!apiList.isEmpty()) {
                        mostrarCatalogosDesdeApi(apiList);
                    } else {
                        cargarCatalogosVendedorFallback(true);
                    }
                } else {
                    cargarCatalogosVendedorFallback(false);
                }
            }

            @Override
            public void onFailure(@NonNull Call<CatalogosResponse> call, @NonNull Throwable t) {
                Log.e(TAG, "Error cargando catálogos desde API, mostrando caché local", t);
                cargarCatalogosVendedorFallback(false);
            }
        });
    }

    private void cargarCatalogosVendedorFallback(boolean apiPrimariaVacia) {
        if (usuarioId < 1) return;
        apiService.obtenerCatalogosVendedor(usuarioId).enqueue(new Callback<CatalogosResponse>() {
            @Override
            public void onResponse(@NonNull Call<CatalogosResponse> call, @NonNull Response<CatalogosResponse> response) {
                if (response.isSuccessful() && response.body() != null && response.body().catalogos != null) {
                    mostrarCatalogosDesdeApi(response.body().catalogos);
                } else if (apiPrimariaVacia) {
                    mostrarCatalogosDesdeApi(new ArrayList<>());
                }
            }

            @Override
            public void onFailure(@NonNull Call<CatalogosResponse> call, @NonNull Throwable t) {
                if (apiPrimariaVacia) {
                    mostrarCatalogosDesdeApi(new ArrayList<>());
                }
            }
        });
    }

    private void mostrarCatalogosDesdeApi(List<CatalogoDto> apiList) {
        List<Catalogo> convertidos = new ArrayList<>();
        for (CatalogoDto dto : apiList) {
            String est = dto.visible ? "Activo" : "Inactivo";
            Catalogo c = new Catalogo(
                    dto.getIdVendedorEfectivo() > 0 ? dto.getIdVendedorEfectivo() : usuarioId,
                    dto.nombre == null ? "Catálogo" : dto.nombre,
                    est
            );
            c.remoteId = dto.idCatalogo;
            c.visible = !"Oculto".equalsIgnoreCase(est) && !"Inactivo".equalsIgnoreCase(est) && dto.visible;
            convertidos.add(c);
        }
        catalogosActuales.clear();
        catalogosActuales.addAll(convertidos);
        catalogoCount = catalogosActuales.size();
        visibleCatalogCount = contarVisibles(catalogosActuales);
        renderLista();
        actualizarEstadoLimite();
        sincronizarConRoom(apiList);
        sincronizarProductosDesdeApi();
    }

    private int contarVisibles(List<Catalogo> lista) {
        int v = 0;
        for (Catalogo c : lista) {
            if (esVisible(c)) v++;
        }
        return v;
    }

    private void sincronizarConRoom(List<CatalogoDto> apiList) {
        executor.execute(() -> {
            try {
                Set<Integer> remoteIdsVistos = new HashSet<>();
                for (CatalogoDto dto : apiList) {
                    if (dto.idCatalogo <= 0) continue;
                    remoteIdsVistos.add(dto.idCatalogo);
                    Catalogo existente = db.catalogoDao().obtenerPorRemoteId(usuarioId, dto.idCatalogo);
                    if (existente == null) {
                        Catalogo nuevo = new Catalogo(usuarioId, dto.nombre == null ? "Catálogo" : dto.nombre, dto.visible ? "Activo" : "Inactivo");
                        nuevo.remoteId = dto.idCatalogo;
                        nuevo.visible = !"Oculto".equalsIgnoreCase(nuevo.estado) && !"Inactivo".equalsIgnoreCase(nuevo.estado) && dto.visible;
                        long newLocalId = db.catalogoDao().insertar(nuevo);
                        nuevo.id = (int) newLocalId;
                    } else {
                        existente.nombre = dto.nombre == null ? existente.nombre : dto.nombre.trim();
                        existente.estado = dto.visible ? "Activo" : "Inactivo";
                        existente.visible = !"Oculto".equalsIgnoreCase(existente.estado) && !"Inactivo".equalsIgnoreCase(existente.estado) && dto.visible;
                        db.catalogoDao().actualizar(existente);
                    }
                }
                List<Catalogo> locales = db.catalogoDao().obtenerListaPorVendedor(usuarioId);
                for (Catalogo local : locales) {
                    if (local.remoteId > 0 && !remoteIdsVistos.contains(local.remoteId)) {
                        db.productoDao().eliminarPorCatalogo(local.id);
                        db.catalogoDao().eliminarPorId(local.id);
                    }
                }
                List<Catalogo> actualizados = db.catalogoDao().obtenerListaPorVendedor(usuarioId);
                Map<Integer, Integer> localCounts = new HashMap<>();
                for (Catalogo c : actualizados) {
                    localCounts.put(c.id, db.productoDao().contarTodosPorCatalogo(c.id));
                }
                runOnUiThread(() -> {
                    catalogosActuales.clear();
                    catalogosActuales.addAll(actualizados);
                    conteoProductosLocal.clear();
                    conteoProductosLocal.putAll(localCounts);
                    catalogoCount = catalogosActuales.size();
                    visibleCatalogCount = contarVisibles(catalogosActuales);
                    renderLista();
                    actualizarEstadoLimite();
                });
            } catch (Exception e) {
                Log.e(TAG, "Error sincronizando catálogos en Room", e);
            }
        });
    }

    private void sincronizarProductosDesdeApi() {
        if (usuarioId < 1) return;
        apiService.obtenerProductosVendedor(usuarioId).enqueue(new Callback<ProductosResponse>() {
            @Override
            public void onResponse(@NonNull Call<ProductosResponse> call, @NonNull Response<ProductosResponse> response) {
                if (!response.isSuccessful() || response.body() == null || response.body().productos == null) return;
                List<ProductoDto> apiProds = response.body().productos;
                Map<Integer, Integer> remoteCounts = new HashMap<>();
                for (ProductoDto dto : apiProds) {
                    if (dto.idCatalogo > 0) {
                        Integer prev = remoteCounts.get(dto.idCatalogo);
                        remoteCounts.put(dto.idCatalogo, (prev == null ? 0 : prev) + 1);
                    }
                }
                conteoProductosRemote.clear();
                conteoProductosRemote.putAll(remoteCounts);
                renderLista();

                executor.execute(() -> {
                    try {
                        List<Catalogo> cats = db.catalogoDao().obtenerListaPorVendedor(usuarioId);
                        Map<Integer, Integer> remoteToLocalCat = new HashMap<>();
                        for (Catalogo c : cats) {
                            if (c.remoteId > 0) remoteToLocalCat.put(c.remoteId, c.id);
                        }
                        for (ProductoDto dto : apiProds) {
                            if (dto.idProducto <= 0) continue;
                            Integer localCatId = remoteToLocalCat.get(dto.idCatalogo);
                            if (localCatId == null) continue;
                            Producto existente = db.productoDao().obtenerPorRemoteId(localCatId, dto.idProducto);
                            if (existente == null) {
                                Producto nuevo = new Producto(
                                        localCatId,
                                        usuarioId,
                                        dto.nombre == null ? "Producto" : dto.nombre,
                                        dto.descripcion == null ? "" : dto.descripcion,
                                        dto.precio,
                                        dto.categoria == null ? "Tamales" : dto.categoria,
                                        dto.visible ? "Activo" : "Inactivo"
                                );
                                nuevo.imagenUri = dto.getImagenEfectiva();
                                nuevo.remoteId = dto.idProducto;
                                nuevo.visible = !"Oculto".equalsIgnoreCase(nuevo.estado) && !"Inactivo".equalsIgnoreCase(nuevo.estado) && dto.visible;
                                db.productoDao().insertar(nuevo);
                            } else {
                                existente.nombre = dto.nombre == null ? existente.nombre : dto.nombre.trim();
                                existente.descripcion = dto.descripcion == null ? existente.descripcion : dto.descripcion.trim();
                                existente.precioMxn = dto.precio;
                                existente.categoria = dto.categoria == null ? existente.categoria : dto.categoria;
                                existente.imagenUri = dto.getImagenEfectiva();
                                existente.estado = dto.visible ? "Activo" : "Inactivo";
                                existente.visible = !"Oculto".equalsIgnoreCase(existente.estado) && !"Inactivo".equalsIgnoreCase(existente.estado) && dto.visible;
                                db.productoDao().actualizar(existente);
                            }
                        }
                    } catch (Exception e) {
                        Log.e(TAG, "Error sincronizando productos en Room", e);
                    }
                });
            }

            @Override
            public void onFailure(@NonNull Call<ProductosResponse> call, @NonNull Throwable t) { }
        });
    }

    private void cargarDesdeRoom() {
        if (usuarioId < 1) return;
        executor.execute(() -> {
            try {
                List<Catalogo> lista = db.catalogoDao().obtenerListaPorVendedor(usuarioId);
                Map<Integer, Integer> conteos = new HashMap<>();
                for (Catalogo c : lista) {
                    conteos.put(c.id, db.productoDao().contarTodosPorCatalogo(c.id));
                }
                final int visiblesFinal = contarVisibles(lista);
                runOnUiThread(() -> {
                    if (catalogosActuales.isEmpty() || !lista.isEmpty()) {
                        catalogosActuales.clear();
                        catalogosActuales.addAll(lista);
                        conteoProductosLocal.clear();
                        conteoProductosLocal.putAll(conteos);
                        catalogoCount = catalogosActuales.size();
                        visibleCatalogCount = visiblesFinal;
                        renderLista();
                        actualizarEstadoLimite();
                    }
                });
            } catch (Exception e) {
                Log.e(TAG, "Error leyendo catálogos locales", e);
            }
        });
    }

    private void actualizarEstadoLimite() {
        int maxVisibles = limiteVisiblesPlan();
        if (counterBadge != null) {
            counterBadge.setText(visibleCatalogCount + " de " + maxVisibles + (maxVisibles == 1 ? " visible" : " visibles"));
        }
        int colorActivo = getColor(R.color.tamalon_field_text);
        int colorVacio = getColor(R.color.tamalon_line);
        if (progress1 != null) progress1.setBackgroundColor(visibleCatalogCount >= 1 ? colorActivo : colorVacio);
        if (progress2 != null) {
            progress2.setVisibility(maxVisibles >= 2 ? View.VISIBLE : View.GONE);
            progress2.setBackgroundColor(visibleCatalogCount >= 2 ? colorActivo : colorVacio);
        }
        if (progress3 != null) {
            progress3.setVisibility(maxVisibles >= 3 ? View.VISIBLE : View.GONE);
            progress3.setBackgroundColor(visibleCatalogCount >= 3 ? colorActivo : colorVacio);
        }

        boolean limiteAlcanzado = visibleCatalogCount >= maxVisibles;
        if (btnAdd != null) {
            btnAdd.setVisibility(limiteAlcanzado ? View.GONE : View.VISIBLE);
            btnAdd.setEnabled(!limiteAlcanzado);
        }
        if (notice != null) {
            notice.setVisibility(limiteAlcanzado ? View.VISIBLE : View.GONE);
        }
        if (emptyState != null) {
            emptyState.setVisibility(catalogosActuales.isEmpty() ? View.VISIBLE : View.GONE);
        }
        if (listContainer != null) {
            listContainer.setVisibility(catalogosActuales.isEmpty() ? View.GONE : View.VISIBLE);
        }
    }

    private void guardar() {
        String value = nameInput.getText().toString().trim();
        if (value.isEmpty()) {
            errorText.setText("Dato inválido en Nombre del catálogo: debe tener entre 1 y 50 caracteres.");
            errorText.setVisibility(View.VISIBLE);
            return;
        }
        if (value.length() > 50) {
            errorText.setText("Dato inválido en Nombre del catálogo: debe tener máximo 50 caracteres.");
            errorText.setVisibility(View.VISIBLE);
            return;
        }

        if ("Visible".equalsIgnoreCase(estadoFormulario) && visibleCatalogCount >= limiteVisiblesPlan()) {
            errorText.setText("Alcanzaste el límite de catálogos de tu plan");
            errorText.setVisibility(View.VISIBLE);
            actualizarEstadoLimite();
            return;
        }

        CrearCatalogoRequest req = new CrearCatalogoRequest(value, estadoFormulario, usuarioId);
        apiService.crearCatalogo(req).enqueue(new Callback<CatalogoDto>() {
            @Override
            public void onResponse(@NonNull Call<CatalogoDto> call, @NonNull Response<CatalogoDto> response) {
                if (response.isSuccessful() && response.body() != null) {
                    int remoteId = response.body().idCatalogo;
                    insertarCatalogoLocal(value, estadoFormulario, remoteId);
                } else {
                    String err = ApiErrorHelper.parseError(CatalogoActivity.this, null, response);
                    errorText.setText(err);
                    errorText.setVisibility(View.VISIBLE);
                    cargarCatalogos();
                }
            }

            @Override
            public void onFailure(@NonNull Call<CatalogoDto> call, @NonNull Throwable t) {
                String err = ApiErrorHelper.parseError(CatalogoActivity.this, t, null);
                errorText.setText(err);
                errorText.setVisibility(View.VISIBLE);
            }
        });
    }

    private void insertarCatalogoLocal(String nombre, String estado, int remoteId) {
        Catalogo nuevo = new Catalogo(usuarioId, nombre, estado);
        nuevo.visible = "Visible".equalsIgnoreCase(estado);
        nuevo.remoteId = remoteId;

        catalogosActuales.add(nuevo);
        catalogoCount = catalogosActuales.size();
        visibleCatalogCount = contarVisibles(catalogosActuales);
        if (nameInput != null) nameInput.setText("");
        cerrarFormulario();
        renderLista();
        actualizarEstadoLimite();
        Toast.makeText(CatalogoActivity.this, "Catálogo creado en estado " + estado, Toast.LENGTH_SHORT).show();

        executor.execute(() -> {
            try {
                long idLocal = db.catalogoDao().insertar(nuevo);
                nuevo.id = (int) idLocal;
            } catch (Exception ignored) { }
        });
    }

    private void guardarEdicion(Catalogo catalogo, String nuevoNombre, String nuevoEstado) {
        if (catalogo.remoteId > 0) {
            CrearCatalogoRequest req = new CrearCatalogoRequest(nuevoNombre, nuevoEstado, usuarioId);
            apiService.actualizarCatalogo(catalogo.remoteId, req).enqueue(new Callback<CatalogoDto>() {
                @Override
                public void onResponse(@NonNull Call<CatalogoDto> call, @NonNull Response<CatalogoDto> response) {
                    actualizarCatalogoLocal(catalogo, nuevoNombre, nuevoEstado);
                }

                @Override
                public void onFailure(@NonNull Call<CatalogoDto> call, @NonNull Throwable t) {
                    actualizarCatalogoLocal(catalogo, nuevoNombre, nuevoEstado);
                }
            });
        } else {
            actualizarCatalogoLocal(catalogo, nuevoNombre, nuevoEstado);
        }
    }

    private void actualizarCatalogoLocal(Catalogo catalogo, String nuevoNombre, String nuevoEstado) {
        catalogo.nombre = nuevoNombre;
        catalogo.estado = nuevoEstado;
        catalogo.visible = "Visible".equalsIgnoreCase(nuevoEstado);
        catalogo.fechaActualizacion = System.currentTimeMillis();
        catalogEnEdicionInline = null;
        visibleCatalogCount = contarVisibles(catalogosActuales);
        renderLista();
        actualizarEstadoLimite();
        Toast.makeText(CatalogoActivity.this, "Catálogo actualizado", Toast.LENGTH_SHORT).show();

        executor.execute(() -> {
            try {
                if (catalogo.id > 0) {
                    db.catalogoDao().actualizar(catalogo);
                }
            } catch (Exception ignored) { }
        });
    }

    private void confirmarEliminarCatalogo(Catalogo catalogo) {
        new AlertDialog.Builder(this)
                .setTitle("Borrar catálogo")
                .setMessage("¿Eliminar \"" + catalogo.nombre + "\" junto con todos sus productos asociados?")
                .setNegativeButton("Cancelar", null)
                .setPositiveButton("Borrar catálogo", (d, w) -> eliminarCatalogo(catalogo))
                .show();
    }

    private void eliminarCatalogo(Catalogo catalogo) {
        if (catalogo.remoteId > 0) {
            apiService.eliminarCatalogo(catalogo.remoteId).enqueue(new Callback<Void>() {
                @Override
                public void onResponse(@NonNull Call<Void> call, @NonNull Response<Void> response) {
                    eliminarCatalogoLocal(catalogo);
                }

                @Override
                public void onFailure(@NonNull Call<Void> call, @NonNull Throwable t) {
                    eliminarCatalogoLocal(catalogo);
                }
            });
        } else {
            eliminarCatalogoLocal(catalogo);
        }
    }

    private void eliminarCatalogoLocal(Catalogo catalogo) {
        catalogosActuales.remove(catalogo);
        if (mismoCatalogo(catalogo, catalogEnEdicionInline)) {
            catalogEnEdicionInline = null;
        }
        catalogoCount = catalogosActuales.size();
        visibleCatalogCount = contarVisibles(catalogosActuales);
        renderLista();
        actualizarEstadoLimite();
        Toast.makeText(CatalogoActivity.this, "Catálogo y productos eliminados", Toast.LENGTH_SHORT).show();

        executor.execute(() -> {
            try {
                if (catalogo.id > 0) {
                    db.productoDao().eliminarPorCatalogo(catalogo.id);
                    db.catalogoDao().eliminarPorId(catalogo.id);
                }
            } catch (Exception ignored) { }
        });
    }

    private void abrirProductosCatalogo(Catalogo catalogo) {
        Intent i = new Intent(this, CatalogoProductosActivity.class);
        i.putExtra("catalogo_id", catalogo.id);
        i.putExtra("catalogo_remote_id", catalogo.remoteId);
        i.putExtra("catalogo_nombre", catalogo.nombre);
        i.putExtra("catalogo_estado", catalogo.getEstadoEfectivo());
        i.putExtra("catalogo_fecha", catalogo.fechaCreacion);
        i.putExtra("vendedor_id", usuarioId);
        i.putExtra("plan", plan);
        startActivity(i);
    }

    private String formatearFecha(long millis) {
        if (millis <= 0) return "Hoy";
        return new SimpleDateFormat("dd/MM/yyyy", new Locale("es", "MX")).format(new Date(millis));
    }

    private boolean mismoCatalogo(Catalogo a, Catalogo b) {
        if (a == null || b == null) return false;
        if (a == b) return true;
        if (a.remoteId > 0 && b.remoteId > 0) return a.remoteId == b.remoteId;
        return a.id > 0 && b.id > 0 && a.id == b.id;
    }

    private void renderLista() {
        if (listContainer == null) return;
        listContainer.removeAllViews();

        for (Catalogo c : catalogosActuales) {
            final Catalogo item = c;
            boolean enEdicion = mismoCatalogo(item, catalogEnEdicionInline);
            boolean activo = esVisible(item);
            String estado = activo ? "Visible" : "Oculto";
            int totalProds = item.remoteId > 0 && conteoProductosRemote.containsKey(item.remoteId)
                    ? conteoProductosRemote.get(item.remoteId)
                    : (conteoProductosLocal.containsKey(item.id) ? conteoProductosLocal.get(item.id) : 0);

            LinearLayout card = new LinearLayout(this);
            card.setOrientation(LinearLayout.VERTICAL);
            card.setPadding(dp(16), dp(14), dp(16), dp(14));
            card.setBackgroundResource(R.drawable.bg_surface_card);
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
            lp.bottomMargin = dp(12);
            card.setLayoutParams(lp);

            if (enEdicion) {
                // Edición desplegada directamente sobre la propia tarjeta del catálogo
                TextView tvEditHeader = new TextView(this);
                tvEditHeader.setText("Editar catálogo");
                tvEditHeader.setTextColor(getColor(R.color.tamalon_ink));
                tvEditHeader.setTextSize(13.5f);
                tvEditHeader.setTypeface(null, Typeface.BOLD);
                card.addView(tvEditHeader);

                TextView tvLabel = new TextView(this);
                tvLabel.setText("Nombre del catálogo (máx. 50 caracteres)");
                tvLabel.setTextColor(getColor(R.color.tamalon_field_text));
                tvLabel.setTextSize(9.5f);
                tvLabel.setPadding(0, dp(8), 0, dp(4));
                card.addView(tvLabel);

                EditText etEditName = new EditText(this);
                etEditName.setText(item.nombre);
                etEditName.setSelection(etEditName.getText().length());
                etEditName.setFilters(new InputFilter[]{new InputFilter.LengthFilter(50)});
                etEditName.setBackgroundResource(R.drawable.bg_input);
                etEditName.setPadding(dp(10), dp(10), dp(10), dp(10));
                etEditName.setTextColor(getColor(R.color.tamalon_ink));
                etEditName.setTextSize(11.5f);
                card.addView(etEditName, new LinearLayout.LayoutParams(
                        LinearLayout.LayoutParams.MATCH_PARENT, dp(44)));

                final String[] estadoEdit = {activo ? "Visible" : "Oculto"};
                LinearLayout stateRow = new LinearLayout(this);
                stateRow.setOrientation(LinearLayout.HORIZONTAL);
                stateRow.setGravity(Gravity.CENTER_VERTICAL);
                LinearLayout.LayoutParams stLp = new LinearLayout.LayoutParams(
                        LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
                stLp.topMargin = dp(10);
                stateRow.setLayoutParams(stLp);

                TextView tvStateLbl = new TextView(this);
                tvStateLbl.setText("Estado");
                tvStateLbl.setTextColor(getColor(R.color.tamalon_field_text));
                tvStateLbl.setTextSize(9.5f);
                stateRow.addView(tvStateLbl, new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f));

                Button btnVis = new Button(this);
                btnVis.setText("Visible");
                btnVis.setAllCaps(false);
                btnVis.setTextSize(9.5f);
                btnVis.setMinHeight(0);
                btnVis.setMinimumHeight(0);
                btnVis.setPadding(dp(12), 0, dp(12), 0);

                Button btnOcu = new Button(this);
                btnOcu.setText("Oculto");
                btnOcu.setAllCaps(false);
                btnOcu.setTextSize(9.5f);
                btnOcu.setMinHeight(0);
                btnOcu.setMinimumHeight(0);
                btnOcu.setPadding(dp(12), 0, dp(12), 0);

                Runnable syncEstadoBtns = () -> {
                    boolean v = "Visible".equals(estadoEdit[0]);
                    btnVis.setBackgroundResource(v ? R.drawable.bg_button : R.drawable.bg_surface_card);
                    btnVis.setBackgroundTintList(null);
                    btnVis.setTextColor(getColor(v ? R.color.tamalon_ink : R.color.tamalon_muted));
                    btnOcu.setBackgroundResource(!v ? R.drawable.bg_button : R.drawable.bg_surface_card);
                    btnOcu.setBackgroundTintList(null);
                    btnOcu.setTextColor(getColor(!v ? R.color.tamalon_ink : R.color.tamalon_muted));
                };
                syncEstadoBtns.run();
                btnVis.setOnClickListener(v -> { estadoEdit[0] = "Visible"; syncEstadoBtns.run(); });
                btnOcu.setOnClickListener(v -> { estadoEdit[0] = "Oculto"; syncEstadoBtns.run(); });

                LinearLayout.LayoutParams vLp = new LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, dp(34));
                vLp.setMarginEnd(dp(6));
                stateRow.addView(btnVis, vLp);
                stateRow.addView(btnOcu, new LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, dp(34)));
                card.addView(stateRow);

                TextView tvInlineErr = new TextView(this);
                tvInlineErr.setTextColor(getColor(R.color.tamalon_error));
                tvInlineErr.setTextSize(9f);
                tvInlineErr.setVisibility(View.GONE);
                tvInlineErr.setPadding(0, dp(6), 0, 0);
                card.addView(tvInlineErr);

                LinearLayout editBtns = new LinearLayout(this);
                editBtns.setOrientation(LinearLayout.HORIZONTAL);
                LinearLayout.LayoutParams ebLp = new LinearLayout.LayoutParams(
                        LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
                ebLp.topMargin = dp(12);
                editBtns.setLayoutParams(ebLp);

                Button btnCancelEdit = crearBotonAccion("Cancelar", false, false);
                Button btnSaveEdit = crearBotonAccion("Guardar", true, false);
                btnCancelEdit.setOnClickListener(v -> {
                    catalogEnEdicionInline = null;
                    renderLista();
                });
                btnSaveEdit.setOnClickListener(v -> {
                    String nuevoNom = etEditName.getText().toString().trim();
                    if (nuevoNom.isEmpty() || nuevoNom.length() > 50) {
                        tvInlineErr.setText("Dato inválido en Nombre del catálogo: debe tener entre 1 y 50 caracteres.");
                        tvInlineErr.setVisibility(View.VISIBLE);
                        return;
                    }
                    boolean eraOculto = !activo;
                    boolean seraVisible = "Visible".equals(estadoEdit[0]);
                    if (eraOculto && seraVisible && visibleCatalogCount >= limiteVisiblesPlan()) {
                        tvInlineErr.setText("Alcanzaste el límite de catálogos de tu plan");
                        tvInlineErr.setVisibility(View.VISIBLE);
                        return;
                    }
                    guardarEdicion(item, nuevoNom, estadoEdit[0]);
                });

                LinearLayout.LayoutParams cLp = new LinearLayout.LayoutParams(0, dp(38), 1f);
                cLp.setMarginEnd(dp(6));
                LinearLayout.LayoutParams sLp = new LinearLayout.LayoutParams(0, dp(38), 1f);
                sLp.setMarginStart(dp(6));
                editBtns.addView(btnCancelEdit, cLp);
                editBtns.addView(btnSaveEdit, sLp);
                card.addView(editBtns);
            } else {
                // Tarjeta normal del catálogo
                LinearLayout topRow = new LinearLayout(this);
                topRow.setOrientation(LinearLayout.HORIZONTAL);
                topRow.setGravity(Gravity.CENTER_VERTICAL);

                ImageView ivIcon = new ImageView(this);
                ivIcon.setImageResource(R.drawable.ic_list);
                ivIcon.setBackgroundResource(R.drawable.bg_circle);
                ivIcon.setPadding(dp(8), dp(8), dp(8), dp(8));
                LinearLayout.LayoutParams iconLp = new LinearLayout.LayoutParams(dp(40), dp(40));
                iconLp.setMarginEnd(dp(12));
                topRow.addView(ivIcon, iconLp);

                LinearLayout info = new LinearLayout(this);
                info.setOrientation(LinearLayout.VERTICAL);

                LinearLayout titleRow = new LinearLayout(this);
                titleRow.setOrientation(LinearLayout.HORIZONTAL);
                titleRow.setGravity(Gravity.CENTER_VERTICAL);

                TextView tvTitle = new TextView(this);
                tvTitle.setText(item.nombre);
                tvTitle.setTextColor(getColor(R.color.tamalon_ink));
                tvTitle.setTextSize(15f);
                tvTitle.setTypeface(null, Typeface.BOLD);
                titleRow.addView(tvTitle, new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f));

                TextView tvBadge = new TextView(this);
                tvBadge.setText(estado);
                tvBadge.setTextSize(9.5f);
                tvBadge.setTypeface(null, Typeface.BOLD);
                tvBadge.setPadding(dp(8), dp(3), dp(8), dp(3));
                GradientDrawable badgeBg = new GradientDrawable();
                badgeBg.setColor(getColor(activo ? R.color.tamalon_sand_strong : R.color.tamalon_sand));
                badgeBg.setCornerRadius(dp(10));
                tvBadge.setBackground(badgeBg);
                tvBadge.setTextColor(getColor(activo ? R.color.tamalon_ink : R.color.tamalon_muted));
                titleRow.addView(tvBadge);

                info.addView(titleRow);

                TextView tvSubtitle = new TextView(this);
                tvSubtitle.setText(totalProds + (totalProds == 1 ? " producto" : " productos")
                        + (item.remoteId == 0 ? " · Solo en dispositivo" : "")
                        + " · " + formatearFecha(item.fechaCreacion));
                tvSubtitle.setTextColor(getColor(R.color.tamalon_muted));
                tvSubtitle.setTextSize(10.5f);
                tvSubtitle.setPadding(0, dp(4), 0, 0);
                info.addView(tvSubtitle);

                topRow.addView(info, new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f));
                card.addView(topRow);

                LinearLayout actionsRow = new LinearLayout(this);
                actionsRow.setOrientation(LinearLayout.HORIZONTAL);
                LinearLayout.LayoutParams actionsLp = new LinearLayout.LayoutParams(
                        LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
                actionsLp.topMargin = dp(12);
                actionsRow.setLayoutParams(actionsLp);

                Button btnProducts = crearBotonAccion("Productos", true, false);
                Button btnEdit = crearBotonAccion("Editar", false, false);
                Button btnDelete = crearBotonAccion("Borrar catálogo", false, true);

                LinearLayout.LayoutParams p1 = new LinearLayout.LayoutParams(0, dp(36), 1.2f);
                p1.setMarginEnd(dp(6));
                LinearLayout.LayoutParams p2 = new LinearLayout.LayoutParams(0, dp(36), 0.9f);
                p2.setMarginEnd(dp(6));
                LinearLayout.LayoutParams p3 = new LinearLayout.LayoutParams(0, dp(36), 1.2f);

                actionsRow.addView(btnProducts, p1);
                actionsRow.addView(btnEdit, p2);
                actionsRow.addView(btnDelete, p3);
                card.addView(actionsRow);

                card.setOnClickListener(v -> abrirProductosCatalogo(item));
                btnProducts.setOnClickListener(v -> abrirProductosCatalogo(item));
                btnEdit.setOnClickListener(v -> abrirEdicionInline(item));
                btnDelete.setOnClickListener(v -> confirmarEliminarCatalogo(item));
            }

            listContainer.addView(card);
            if (enEdicion && scrollView != null) {
                card.post(() -> scrollView.smoothScrollTo(0, card.getTop()));
            }
        }
    }

    private Button crearBotonAccion(String texto, boolean primario, boolean destructivo) {
        Button btn = new Button(this);
        btn.setText(texto);
        btn.setAllCaps(false);
        btn.setTextSize(10f);
        btn.setTypeface(null, primario ? Typeface.BOLD : Typeface.NORMAL);
        btn.setMinHeight(0);
        btn.setMinimumHeight(0);
        btn.setPadding(dp(6), 0, dp(6), 0);
        btn.setBackgroundTintList(null);
        if (primario) {
            btn.setBackgroundResource(R.drawable.bg_button);
            btn.setTextColor(getColor(R.color.tamalon_ink));
        } else {
            GradientDrawable bg = new GradientDrawable();
            bg.setColor(getColor(R.color.tamalon_surface_strong));
            bg.setCornerRadius(dp(10));
            bg.setStroke(dp(1), getColor(destructivo ? R.color.tamalon_error : R.color.tamalon_line));
            btn.setBackground(bg);
            btn.setTextColor(getColor(destructivo ? R.color.tamalon_error : R.color.tamalon_field_text));
        }
        return btn;
    }

    private int dp(float valor) {
        return Math.round(valor * getResources().getDisplayMetrics().density);
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        executor.shutdown();
    }
}
