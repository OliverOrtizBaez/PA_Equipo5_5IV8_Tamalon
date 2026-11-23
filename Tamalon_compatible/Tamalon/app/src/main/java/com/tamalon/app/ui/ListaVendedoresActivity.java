package com.tamalon.app.ui;

import android.content.Intent;
import android.content.SharedPreferences;
import android.content.res.ColorStateList;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.RippleDrawable;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.util.Log;
import android.view.Gravity;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;

import com.tamalon.app.BuildConfig;
import com.tamalon.app.R;
import com.tamalon.app.data.local.AppDatabase;
import com.tamalon.app.model.Favorito;
import com.tamalon.app.model.Producto;
import com.tamalon.app.model.Usuario;
import com.tamalon.app.network.*;
import com.tamalon.app.service.AutenticacionService;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
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
 * Pantalla de búsqueda de vendedores y antojitos de la zona conectada a la API.
 * Soporta modo oscuro, filtros por preferencias compartidas con el Mapa y toggle de favoritos.
 */
public class ListaVendedoresActivity extends AppCompatActivity {

    private static final String TAG = "ListaVendedoresActivity";
    private static boolean loggedVendedores = false;

    private final ExecutorService executor = Executors.newSingleThreadExecutor();
    private AppDatabase db;
    private ApiService apiService;
    private int usuarioId = -1;
    private String rol = "cliente";
    private String plan = "gratuito";
    private String filtroTexto = "";

    private final String[] categorias = {
            "Todos", "Tamales", "Atole", "Quesadillas", "Pambazos", "Sopes", "Champurrado"
    };
    private final Set<String> filtrosAntojitos = new LinkedHashSet<>();

    private ScrollView scrollView;
    private LinearLayout resultsContainer;
    private View emptyState;
    private TextView tvCountBadge;
    private LinearLayout categoryFiltersContainer;

    private LinearLayout selectedCard;
    private ImageView ivSelectedAvatar;
    private TextView tvSelectedName;
    private TextView tvSelectedStatus;
    private TextView tvSelectedDesc;
    private Button btnSelectedCatalog;
    private Button btnSelectedFavorite;
    private ImageButton btnSelectedClose;
    private VendedorListaDto vendedorSeleccionadoActual;

    private final List<VendedorListaDto> listaVendedores = new ArrayList<>();
    private final Set<Integer> favoritosIds = new HashSet<>();
    private final Map<Integer, String> categoriasPorVendedor = new HashMap<>();
    private final Map<Integer, String> productosTextoPorVendedor = new HashMap<>();
    private final Set<Integer> productosCargadosVendedor = new HashSet<>();

    @Override
    protected void onCreate(Bundle state) {
        super.onCreate(state);
        setContentView(R.layout.activity_list);

        usuarioId = getIntent().getIntExtra("usuario_id", -1);
        if (getIntent().getStringExtra("rol") != null) rol = getIntent().getStringExtra("rol");
        if (getIntent().getStringExtra("plan") != null) plan = getIntent().getStringExtra("plan");

        db = AppDatabase.getInstance(this);
        apiService = ApiClient.getApiService(this);

        scrollView = findViewById(R.id.list_scroll_view);
        resultsContainer = findViewById(R.id.list_results);
        emptyState = findViewById(R.id.list_empty_state);
        tvCountBadge = findViewById(R.id.list_count_badge);
        categoryFiltersContainer = findViewById(R.id.list_category_filters);

        View btnNotificaciones = findViewById(R.id.list_btn_notifications);
        if (btnNotificaciones != null) {
            btnNotificaciones.setOnClickListener(v ->
                    Toast.makeText(this, "Notificaciones: Tienes alertas de puestos en ruta en tu zona", Toast.LENGTH_LONG).show());
        }

        selectedCard = findViewById(R.id.list_selected_card);
        ivSelectedAvatar = findViewById(R.id.list_selected_avatar);
        tvSelectedName = findViewById(R.id.list_selected_name);
        tvSelectedStatus = findViewById(R.id.list_selected_status);
        tvSelectedDesc = findViewById(R.id.list_selected_description);
        btnSelectedCatalog = findViewById(R.id.list_selected_btn_catalog);
        btnSelectedFavorite = findViewById(R.id.list_selected_btn_favorite);
        btnSelectedClose = findViewById(R.id.list_selected_btn_close);

        if (btnSelectedCatalog != null) {
            btnSelectedCatalog.setBackgroundTintList(null);
        }
        if (btnSelectedFavorite != null) {
            btnSelectedFavorite.setBackgroundTintList(null);
        }

        if (btnSelectedClose != null) {
            btnSelectedClose.setOnClickListener(v -> {
                if (selectedCard != null) selectedCard.setVisibility(View.GONE);
                vendedorSeleccionadoActual = null;
            });
        }

        if (btnSelectedCatalog != null) {
            btnSelectedCatalog.setOnClickListener(v -> {
                if (vendedorSeleccionadoActual != null) {
                    Intent i = new Intent(this, CatalogoPublicoActivity.class);
                    i.putExtra("vendedor_id", vendedorSeleccionadoActual.idUsuario);
                    i.putExtra("plan_cliente", plan);
                    i.putExtra("vendedor_nombre", vendedorSeleccionadoActual.nombreCompleto);
                    startActivity(i);
                }
            });
        }

        if (btnSelectedFavorite != null) {
            btnSelectedFavorite.setOnClickListener(v -> {
                if (vendedorSeleccionadoActual != null) {
                    toggleFavorito(vendedorSeleccionadoActual);
                }
            });
        }

        BottomNavigation.configurar(this, findViewById(android.R.id.content), usuarioId, rol, plan);

        EditText search = findViewById(R.id.list_search);
        search.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
            @Override public void onTextChanged(CharSequence s, int start, int before, int count) {
                filtroTexto = s.toString().trim().toLowerCase(Locale.ROOT);
                cargarVendedoresDeApi();
            }
            @Override public void afterTextChanged(Editable e) {}
        });

        cargarPreferenciasGuardadas();
        construirChipsCategorias();
        sincronizarUsuarioYCargar();
    }

    @Override
    protected void onResume() {
        super.onResume();
        cargarPreferenciasGuardadas();
        construirChipsCategorias();
        cargarFavoritos();
        render();
    }

    private void sincronizarUsuarioYCargar() {
        new AutenticacionService(this).obtenerUsuarioActivo(new AutenticacionService.Callback<Usuario>() {
            @Override
            public void onExito(Usuario u) {
                runOnUiThread(() -> {
                    usuarioId = u.id;
                    if (u.rol != null) rol = u.rol;
                    if (u.plan != null) plan = u.plan;
                    BottomNavigation.configurar(ListaVendedoresActivity.this, findViewById(android.R.id.content), usuarioId, rol, plan);
                    cargarPreferenciasGuardadas();
                    construirChipsCategorias();
                    cargarFavoritos();
                    cargarVendedoresDeApi();
                });
            }

            @Override
            public void onError(String m) {
                runOnUiThread(() -> {
                    cargarFavoritos();
                    cargarVendedoresDeApi();
                });
            }
        });
    }

    private void cargarPreferenciasGuardadas() {
        filtrosAntojitos.clear();
        if (usuarioId > 0) {
            SharedPreferences prefs = getSharedPreferences("tamalon_map_filters_" + usuarioId, MODE_PRIVATE);
            Set<String> guardadas = prefs.getStringSet("categorias", null);
            if (guardadas != null) {
                filtrosAntojitos.addAll(guardadas);
            }
        }
    }

    private void guardarPreferencias() {
        if (usuarioId > 0) {
            getSharedPreferences("tamalon_map_filters_" + usuarioId, MODE_PRIVATE)
                    .edit()
                    .putStringSet("categorias", new HashSet<>(filtrosAntojitos))
                    .apply();
        }
    }

    private void cargarFavoritos() {
        if (usuarioId > 0) {
            executor.execute(() -> {
                List<Favorito> locales = db.favoritoDao().obtenerListaPorCliente(usuarioId);
                runOnUiThread(() -> {
                    for (Favorito f : locales) {
                        favoritosIds.add(f.vendedorId);
                    }
                    if (vendedorSeleccionadoActual != null) {
                        actualizarBotonFavorito(vendedorSeleccionadoActual);
                    }
                    render();
                });
            });
        }

        apiService.obtenerFavoritos().enqueue(new Callback<FavoritosResponse>() {
            @Override
            public void onResponse(@NonNull Call<FavoritosResponse> call, @NonNull Response<FavoritosResponse> response) {
                if (response.isSuccessful() && response.body() != null) {
                    Set<Integer> apiIds = new HashSet<>();
                    List<FavoritoDto> lista = response.body().favoritos;
                    if (lista != null) {
                        SharedPreferences cache = usuarioId > 0
                                ? getSharedPreferences("tamalon_fav_cache_" + usuarioId, MODE_PRIVATE)
                                : null;
                        SharedPreferences.Editor ed = cache != null ? cache.edit() : null;
                        for (FavoritoDto f : lista) {
                            int idVend = f.idVendedor > 0 ? f.idVendedor : (f.vendedor != null ? f.vendedor.idUsuario : 0);
                            if (idVend > 0) {
                                apiIds.add(idVend);
                                String nombre = f.nombreVendedor != null ? f.nombreVendedor
                                        : (f.vendedor != null && f.vendedor.nombreCompleto != null ? f.vendedor.nombreCompleto : "Puesto de Antojitos");
                                String foto = f.fotoPerfil != null ? f.fotoPerfil
                                        : (f.vendedor != null ? f.vendedor.fotoPerfil : null);
                                if (ed != null) {
                                    ed.putString("vendor_" + idVend, nombre);
                                    if (foto != null && !foto.trim().isEmpty()) {
                                        ed.putString("foto_" + idVend, foto.trim());
                                    }
                                }
                            }
                        }
                        if (ed != null) ed.apply();
                    }
                    if (usuarioId > 0) {
                        executor.execute(() -> {
                            for (Integer idVend : apiIds) {
                                db.favoritoDao().insertar(new Favorito(usuarioId, idVend));
                            }
                        });
                    }
                    favoritosIds.addAll(apiIds);
                    if (vendedorSeleccionadoActual != null) {
                        actualizarBotonFavorito(vendedorSeleccionadoActual);
                    }
                    render();
                }
            }

            @Override
            public void onFailure(@NonNull Call<FavoritosResponse> call, @NonNull Throwable t) {
                // Conserva caché local
            }
        });
    }

    private void cargarVendedoresDeApi() {
        String buscarParam = filtroTexto.isEmpty() ? null : filtroTexto;
        apiService.obtenerVendedores(buscarParam, 1, 50).enqueue(new Callback<VendedoresListaResponse>() {
            @Override
            public void onResponse(@NonNull Call<VendedoresListaResponse> call, @NonNull Response<VendedoresListaResponse> response) {
                if (response.isSuccessful() && response.body() != null) {
                    if (BuildConfig.DEBUG && !loggedVendedores) {
                        loggedVendedores = true;
                        Log.d(TAG, "GET /api/usuarios/vendedores HTTP " + response.code() + " total=" + (response.body().vendedores != null ? response.body().vendedores.size() : 0));
                    }
                    listaVendedores.clear();
                    if (response.body().vendedores != null) {
                        listaVendedores.addAll(response.body().vendedores);
                    }
                    cargarCategoriasDeVendedores();
                    render();
                }
            }

            @Override
            public void onFailure(@NonNull Call<VendedoresListaResponse> call, @NonNull Throwable t) {
                Log.e(TAG, "Error cargando lista de vendedores", t);
            }
        });
    }

    private void cargarCategoriasDeVendedores() {
        for (VendedorListaDto v : listaVendedores) {
            if (v.categorias != null && !v.categorias.trim().isEmpty()) {
                categoriasPorVendedor.put(v.idUsuario, v.categorias.trim());
            }
            if (productosCargadosVendedor.contains(v.idUsuario)) continue;
            productosCargadosVendedor.add(v.idUsuario);

            executor.execute(() -> {
                List<Producto> locales = db.productoDao().obtenerListaPorVendedor(v.idUsuario);
                if (!locales.isEmpty()) {
                    Set<String> cats = new LinkedHashSet<>();
                    StringBuilder nombres = new StringBuilder();
                    for (Producto p : locales) {
                        if (p.categoria != null && !p.categoria.trim().isEmpty()) cats.add(p.categoria.trim());
                        if (p.nombre != null) nombres.append(p.nombre).append(' ');
                    }
                    runOnUiThread(() -> {
                        if (!cats.isEmpty()) categoriasPorVendedor.put(v.idUsuario, String.join(", ", cats));
                        productosTextoPorVendedor.put(v.idUsuario, nombres.toString().toLowerCase(Locale.ROOT));
                        render();
                    });
                }
            });

            apiService.obtenerProductosVendedor(v.idUsuario).enqueue(new Callback<ProductosResponse>() {
                @Override
                public void onResponse(@NonNull Call<ProductosResponse> call, @NonNull Response<ProductosResponse> response) {
                    if (response.isSuccessful() && response.body() != null && response.body().productos != null) {
                        Set<String> cats = new LinkedHashSet<>();
                        StringBuilder nombres = new StringBuilder();
                        for (ProductoDto p : response.body().productos) {
                            if (p.categoria != null && !p.categoria.trim().isEmpty()) {
                                cats.add(p.categoria.trim());
                            }
                            if (p.nombre != null) {
                                nombres.append(p.nombre).append(' ');
                            }
                        }
                        if (!cats.isEmpty()) {
                            categoriasPorVendedor.put(v.idUsuario, String.join(", ", cats));
                        }
                        if (nombres.length() > 0) {
                            productosTextoPorVendedor.put(v.idUsuario, nombres.toString().toLowerCase(Locale.ROOT));
                        }
                        render();
                    }
                }

                @Override
                public void onFailure(@NonNull Call<ProductosResponse> call, @NonNull Throwable t) {
                    // Ignorar error individual
                }
            });
        }
    }

    private void construirChipsCategorias() {
        if (categoryFiltersContainer == null) return;
        categoryFiltersContainer.removeAllViews();

        for (String cat : categorias) {
            Button chip = new Button(this);
            chip.setText(cat);
            chip.setAllCaps(false);
            chip.setTextSize(11f);
            chip.setTypeface(null, Typeface.BOLD);
            chip.setMinHeight(0);
            chip.setMinimumHeight(0);
            chip.setMinWidth(0);
            chip.setMinimumWidth(0);
            chip.setPadding(dp(14), dp(4), dp(14), dp(4));

            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.WRAP_CONTENT, dp(38));
            lp.setMargins(dp(4), 0, dp(4), 0);
            chip.setLayoutParams(lp);

            actualizarEstiloChip(chip, cat);

            chip.setOnClickListener(v -> {
                if ("Todos".equalsIgnoreCase(cat)) {
                    filtrosAntojitos.clear();
                } else {
                    if (filtrosAntojitos.contains(cat)) {
                        filtrosAntojitos.remove(cat);
                    } else {
                        filtrosAntojitos.add(cat);
                    }
                }
                guardarPreferencias();
                construirChipsCategorias();
                render();
            });

            categoryFiltersContainer.addView(chip);
        }
    }

    private void actualizarEstiloChip(Button chip, String cat) {
        boolean activa = "Todos".equalsIgnoreCase(cat)
                ? filtrosAntojitos.isEmpty()
                : contieneIgnoreCase(filtrosAntojitos, cat);
        int colorFondo = activa ? getColor(R.color.tamalon_sand_strong) : getColor(R.color.tamalon_surface);
        int colorTexto = activa ? getColor(R.color.tamalon_ink) : getColor(R.color.tamalon_field_text);
        int colorBorde = activa ? getColor(R.color.tamalon_heading_muted) : getColor(R.color.tamalon_line);

        GradientDrawable forma = new GradientDrawable();
        forma.setColor(colorFondo);
        forma.setCornerRadius(dp(20));
        forma.setStroke(dp(1), colorBorde);

        RippleDrawable ripple = new RippleDrawable(
                ColorStateList.valueOf(getColor(R.color.tamalon_sand)), forma, null);
        chip.setBackground(ripple);
        chip.setBackgroundTintList(null);
        chip.setTextColor(colorTexto);
        chip.setElevation(activa ? dp(2) : 0);
    }

    private boolean contieneIgnoreCase(Set<String> set, String target) {
        for (String item : set) {
            if (item != null && item.equalsIgnoreCase(target)) return true;
        }
        return false;
    }

    private boolean coincideConFiltros(VendedorListaDto u) {
        String nombreLower = u.nombreCompleto != null ? u.nombreCompleto.toLowerCase(Locale.ROOT) : "";
        String catsStr = categoriasPorVendedor.containsKey(u.idUsuario)
                ? categoriasPorVendedor.get(u.idUsuario)
                : (u.categorias != null ? u.categorias : "");
        String catsLower = catsStr != null ? catsStr.toLowerCase(Locale.ROOT) : "";
        String prodsLower = productosTextoPorVendedor.containsKey(u.idUsuario)
                ? productosTextoPorVendedor.get(u.idUsuario)
                : "";

        if (!filtroTexto.isEmpty()) {
            boolean matchTexto = nombreLower.contains(filtroTexto)
                    || catsLower.contains(filtroTexto)
                    || prodsLower.contains(filtroTexto);
            if (!matchTexto) return false;
        }

        if (!filtrosAntojitos.isEmpty()) {
            boolean matchPref = false;
            for (String pref : filtrosAntojitos) {
                String pLower = pref.toLowerCase(Locale.ROOT);
                if (catsLower.contains(pLower) || nombreLower.contains(pLower) || prodsLower.contains(pLower)) {
                    matchPref = true;
                    break;
                }
            }
            if (!matchPref) return false;
        }
        return true;
    }

    private void render() {
        resultsContainer.removeAllViews();
        int count = 0;

        for (VendedorListaDto u : listaVendedores) {
            if (!coincideConFiltros(u)) continue;
            count++;
            final VendedorListaDto vendedorItem = u;
            boolean esFav = favoritosIds.contains(u.idUsuario);

            LinearLayout card = new LinearLayout(this);
            card.setOrientation(LinearLayout.HORIZONTAL);
            card.setGravity(Gravity.CENTER_VERTICAL);
            card.setPadding(dp(14), dp(12), dp(14), dp(12));

            GradientDrawable bg = new GradientDrawable();
            bg.setColor(getColor(R.color.tamalon_surface));
            bg.setCornerRadius(dp(16));
            bg.setStroke(dp(1), getColor(R.color.tamalon_line));
            card.setBackground(bg);

            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
            lp.bottomMargin = dp(10);
            card.setLayoutParams(lp);

            ImageView ivAvatar = new ImageView(this);
            ivAvatar.setBackgroundResource(R.drawable.bg_circle);
            ivAvatar.setClipToOutline(true);
            ImagenPerfil.mostrar(ivAvatar, u.fotoPerfil, this);
            LinearLayout.LayoutParams avatarLp = new LinearLayout.LayoutParams(dp(40), dp(40));
            avatarLp.setMarginEnd(dp(12));
            card.addView(ivAvatar, avatarLp);

            LinearLayout info = new LinearLayout(this);
            info.setOrientation(LinearLayout.VERTICAL);

            TextView tvName = new TextView(this);
            String baseName = u.nombreCompleto == null ? "Puesto de Antojitos" : u.nombreCompleto;
            tvName.setText(esFav ? "♥ " + baseName : baseName);
            tvName.setTextColor(getColor(R.color.tamalon_ink));
            tvName.setTextSize(14f);
            tvName.setTypeface(null, Typeface.BOLD);
            info.addView(tvName);

            TextView tvSub = new TextView(this);
            String cats = categoriasPorVendedor.get(u.idUsuario);
            String especialidad = (cats != null && !cats.isEmpty()) ? cats : "Especialidad en antojitos";
            tvSub.setText(especialidad + (u.enRuta ? " · En ruta activa" : " · Fuera de ruta"));
            tvSub.setTextColor(getColor(R.color.tamalon_muted));
            tvSub.setTextSize(10f);
            tvSub.setPadding(0, dp(2), 0, 0);
            info.addView(tvSub);

            card.addView(info, new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f));

            Button btnVer = new Button(this);
            btnVer.setText("Detalle");
            btnVer.setAllCaps(false);
            btnVer.setTextSize(10f);
            btnVer.setMinHeight(0);
            btnVer.setMinimumHeight(0);
            btnVer.setPadding(dp(10), dp(4), dp(10), dp(4));
            btnVer.setBackgroundTintList(null);
            GradientDrawable btnBg = new GradientDrawable();
            btnBg.setColor(getColor(R.color.tamalon_surface_strong));
            btnBg.setCornerRadius(dp(12));
            btnBg.setStroke(dp(1), getColor(R.color.tamalon_line));
            btnVer.setBackground(btnBg);
            btnVer.setTextColor(getColor(R.color.tamalon_ink));
            btnVer.setOnClickListener(v -> seleccionarVendedor(vendedorItem));
            card.addView(btnVer);

            card.setOnClickListener(v -> seleccionarVendedor(vendedorItem));
            resultsContainer.addView(card);
        }

        if (tvCountBadge != null) {
            tvCountBadge.setText(count + (count == 1 ? " puesto" : " puestos"));
        }

        emptyState.setVisibility(count == 0 ? View.VISIBLE : View.GONE);
        resultsContainer.setVisibility(count == 0 ? View.GONE : View.VISIBLE);
    }

    private void seleccionarVendedor(VendedorListaDto vendedor) {
        vendedorSeleccionadoActual = vendedor;
        if (selectedCard == null) return;

        if (tvSelectedName != null) {
            tvSelectedName.setText(vendedor.nombreCompleto == null ? "Puesto de Antojitos" : vendedor.nombreCompleto);
        }
        if (ivSelectedAvatar != null) {
            ImagenPerfil.mostrar(ivSelectedAvatar, vendedor.fotoPerfil, this);
        }
        if (tvSelectedStatus != null) {
            tvSelectedStatus.setText(vendedor.enRuta ? "● En ruta activa" : "○ Fuera de ruta");
        }
        if (tvSelectedDesc != null) {
            String cats = categoriasPorVendedor.get(vendedor.idUsuario);
            if (cats != null && !cats.isEmpty()) {
                tvSelectedDesc.setText("Ofrece: " + cats + ". Consulta el catálogo para ver productos disponibles.");
            } else {
                tvSelectedDesc.setText("Puesto seleccionado: Venta de antojitos tradicionales preparados al día. Consulta el catálogo para ver productos disponibles.");
            }
        }

        actualizarBotonFavorito(vendedor);
        selectedCard.setVisibility(View.VISIBLE);

        if (scrollView != null) {
            scrollView.smoothScrollTo(0, selectedCard.getTop());
        }
    }

    private void actualizarBotonFavorito(VendedorListaDto vendedor) {
        if (btnSelectedFavorite == null || vendedor == null) return;
        boolean esFav = favoritosIds.contains(vendedor.idUsuario);
        btnSelectedFavorite.setBackgroundTintList(null);
        if (esFav) {
            btnSelectedFavorite.setText("Quitar de favoritos");
            GradientDrawable favBg = new GradientDrawable();
            favBg.setColor(getColor(R.color.tamalon_sand_strong));
            favBg.setCornerRadius(dp(12));
            favBg.setStroke(dp(1), getColor(R.color.tamalon_heading_muted));
            btnSelectedFavorite.setBackground(favBg);
            btnSelectedFavorite.setTextColor(getColor(R.color.tamalon_ink));
        } else {
            btnSelectedFavorite.setText("Añadir a favoritos");
            btnSelectedFavorite.setBackgroundResource(R.drawable.bg_surface_card);
            btnSelectedFavorite.setTextColor(getColor(R.color.tamalon_ink));
        }
    }

    private void toggleFavorito(VendedorListaDto vendedor) {
        if (usuarioId < 1) {
            Toast.makeText(this, "No se pudo identificar tu cuenta", Toast.LENGTH_SHORT).show();
            return;
        }
        if (!"premium".equalsIgnoreCase(plan)) {
            Toast.makeText(this, "Guardar puestos en favoritos requiere el plan Premium", Toast.LENGTH_LONG).show();
            return;
        }

        final boolean yaEsFavorito = favoritosIds.contains(vendedor.idUsuario);
        if (yaEsFavorito) {
            quitarFavorito(vendedor);
        } else {
            if (favoritosIds.size() >= 5) {
                Toast.makeText(this, "Ya alcanzaste el máximo de 5 favoritos", Toast.LENGTH_LONG).show();
                return;
            }
            agregarFavorito(vendedor);
        }
    }

    private void agregarFavorito(VendedorListaDto vendedor) {
        String nombreVendedor = vendedor.nombreCompleto == null ? "Puesto de Antojitos" : vendedor.nombreCompleto;
        favoritosIds.add(vendedor.idUsuario);
        actualizarBotonFavorito(vendedor);
        render();

        SharedPreferences cache = getSharedPreferences("tamalon_fav_cache_" + usuarioId, MODE_PRIVATE);
        SharedPreferences.Editor editor = cache.edit().putString("vendor_" + vendedor.idUsuario, nombreVendedor);
        if (vendedor.fotoPerfil != null && !vendedor.fotoPerfil.trim().isEmpty()) {
            editor.putString("foto_" + vendedor.idUsuario, vendedor.fotoPerfil.trim());
        }
        editor.apply();
        executor.execute(() -> db.favoritoDao().insertar(new Favorito(usuarioId, vendedor.idUsuario)));

        apiService.agregarFavorito(new AgregarFavoritoRequest(vendedor.idUsuario)).enqueue(new Callback<Void>() {
            @Override
            public void onResponse(@NonNull Call<Void> call, @NonNull Response<Void> response) {
                if (response.isSuccessful() || response.code() == 409) {
                    Toast.makeText(ListaVendedoresActivity.this, "Puesto añadido a favoritos", Toast.LENGTH_SHORT).show();
                } else {
                    favoritosIds.remove(vendedor.idUsuario);
                    cache.edit().remove("vendor_" + vendedor.idUsuario).remove("foto_" + vendedor.idUsuario).apply();
                    executor.execute(() -> db.favoritoDao().eliminarPorIds(usuarioId, vendedor.idUsuario));
                    actualizarBotonFavorito(vendedor);
                    render();
                    String err = ApiErrorHelper.parseError(ListaVendedoresActivity.this, null, response);
                    Toast.makeText(ListaVendedoresActivity.this, err, Toast.LENGTH_LONG).show();
                }
            }

            @Override
            public void onFailure(@NonNull Call<Void> call, @NonNull Throwable t) {
                Toast.makeText(ListaVendedoresActivity.this, "Puesto añadido a favoritos", Toast.LENGTH_SHORT).show();
            }
        });
    }

    private void quitarFavorito(VendedorListaDto vendedor) {
        favoritosIds.remove(vendedor.idUsuario);
        actualizarBotonFavorito(vendedor);
        render();

        SharedPreferences cache = getSharedPreferences("tamalon_fav_cache_" + usuarioId, MODE_PRIVATE);
        cache.edit().remove("vendor_" + vendedor.idUsuario).remove("foto_" + vendedor.idUsuario).apply();
        executor.execute(() -> db.favoritoDao().eliminarPorIds(usuarioId, vendedor.idUsuario));

        apiService.eliminarFavorito(vendedor.idUsuario).enqueue(new Callback<Void>() {
            @Override
            public void onResponse(@NonNull Call<Void> call, @NonNull Response<Void> response) {
                if (response.isSuccessful() || response.code() == 404) {
                    Toast.makeText(ListaVendedoresActivity.this, "Puesto quitado de favoritos", Toast.LENGTH_SHORT).show();
                } else {
                    String err = ApiErrorHelper.parseError(ListaVendedoresActivity.this, null, response);
                    Toast.makeText(ListaVendedoresActivity.this, err, Toast.LENGTH_LONG).show();
                }
            }

            @Override
            public void onFailure(@NonNull Call<Void> call, @NonNull Throwable t) {
                Toast.makeText(ListaVendedoresActivity.this, "Puesto quitado de favoritos", Toast.LENGTH_SHORT).show();
            }
        });
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
