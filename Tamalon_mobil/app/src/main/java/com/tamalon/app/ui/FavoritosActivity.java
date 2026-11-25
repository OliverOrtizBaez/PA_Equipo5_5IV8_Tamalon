package com.tamalon.app.ui;

import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.util.Log;
import android.view.Gravity;
import android.view.View;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;

import com.tamalon.app.BuildConfig;
import com.tamalon.app.R;
import com.tamalon.app.data.local.AppDatabase;
import com.tamalon.app.model.Favorito;
import com.tamalon.app.model.Usuario;
import com.tamalon.app.network.*;
import com.tamalon.app.service.AutenticacionService;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

public class FavoritosActivity extends AppCompatActivity {
    private static final String TAG = "FavoritosActivity";
    private static boolean loggedFavoritos = false;

    private final ExecutorService executor = Executors.newSingleThreadExecutor();
    private AppDatabase db;
    private ApiService apiService;
    private int usuarioId = -1;
    private String rol = "cliente", plan = "gratuito";
    private LinearLayout results;
    private View empty;
    private TextView countText;

    private static class FavoritoItem {
        final int idVendedor;
        final String nombre;
        final String fotoPerfil;
        FavoritoItem(int idVendedor, String nombre, String fotoPerfil) {
            this.idVendedor = idVendedor;
            this.nombre = nombre;
            this.fotoPerfil = fotoPerfil;
        }
    }

    @Override protected void onCreate(Bundle state) {
        super.onCreate(state);
        setContentView(R.layout.activity_favorites);
        usuarioId = getIntent().getIntExtra("usuario_id", -1);
        if (getIntent().getStringExtra("rol") != null) rol = getIntent().getStringExtra("rol");
        if (getIntent().getStringExtra("plan") != null) plan = getIntent().getStringExtra("plan");

        db = AppDatabase.getInstance(this);
        apiService = ApiClient.getApiService(this);
        results = findViewById(R.id.favorites_results);
        empty = findViewById(R.id.favorites_empty);
        countText = findViewById(R.id.favorites_count_text);
        BottomNavigation.configurar(this, findViewById(android.R.id.content), usuarioId, rol, plan);

        sincronizarUsuarioYCargar();
    }

    @Override
    protected void onResume() {
        super.onResume();
        if ("cliente".equalsIgnoreCase(rol) && "premium".equalsIgnoreCase(plan)) {
            cargarFavoritos();
        }
    }

    private void sincronizarUsuarioYCargar() {
        new AutenticacionService(this).obtenerUsuarioActivo(new AutenticacionService.Callback<Usuario>() {
            @Override
            public void onExito(Usuario u) {
                runOnUiThread(() -> {
                    usuarioId = u.id;
                    if (u.rol != null) rol = u.rol;
                    if (u.plan != null) plan = u.plan;
                    BottomNavigation.configurar(FavoritosActivity.this, findViewById(android.R.id.content), usuarioId, rol, plan);
                    verificarYCargar();
                });
            }

            @Override
            public void onError(String m) {
                runOnUiThread(() -> verificarYCargar());
            }
        });
    }

    private void verificarYCargar() {
        if (!"cliente".equalsIgnoreCase(rol)) {
            showEmpty("Los favoritos están disponibles para cuentas de cliente.");
            return;
        }
        if (!"premium".equalsIgnoreCase(plan)) {
            showEmpty("Esta función es exclusiva de cuentas Premium.");
            Toast.makeText(this, "Esta función es exclusiva de cuentas Premium", Toast.LENGTH_LONG).show();
            return;
        }
        cargarFavoritos();
    }

    private void cargarFavoritos() {
        cargarDesdeLocalYRender(null);
        apiService.obtenerFavoritos().enqueue(new Callback<FavoritosResponse>() {
            @Override
            public void onResponse(@NonNull Call<FavoritosResponse> call, @NonNull Response<FavoritosResponse> response) {
                if (response.isSuccessful() && response.body() != null) {
                    if (BuildConfig.DEBUG && !loggedFavoritos) {
                        loggedFavoritos = true;
                        Log.d(TAG, "GET /api/favoritos HTTP " + response.code() + " total=" + (response.body().favoritos != null ? response.body().favoritos.size() : 0));
                    }
                    List<FavoritoItem> apiItems = new ArrayList<>();
                    if (response.body().favoritos != null) {
                        SharedPreferences cache = usuarioId > 0
                                ? getSharedPreferences("tamalon_fav_cache_" + usuarioId, MODE_PRIVATE)
                                : null;
                        SharedPreferences.Editor ed = cache != null ? cache.edit() : null;
                        for (FavoritoDto f : response.body().favoritos) {
                            int idVend = f.idVendedor > 0 ? f.idVendedor : (f.vendedor != null ? f.vendedor.idUsuario : 0);
                            String name = f.nombreVendedor != null ? f.nombreVendedor
                                    : (f.vendedor != null && f.vendedor.nombreCompleto != null ? f.vendedor.nombreCompleto : "Puesto de Vendedor");
                            String foto = f.fotoPerfil != null ? f.fotoPerfil
                                    : (f.vendedor != null ? f.vendedor.fotoPerfil : null);
                            if (idVend > 0) {
                                apiItems.add(new FavoritoItem(idVend, name, foto));
                                if (ed != null) {
                                    ed.putString("vendor_" + idVend, name);
                                    if (foto != null && !foto.trim().isEmpty()) {
                                        ed.putString("foto_" + idVend, foto.trim());
                                    }
                                }
                            }
                        }
                        if (ed != null) ed.apply();
                    }
                    cargarDesdeLocalYRender(apiItems);
                } else {
                    String err = ApiErrorHelper.parseError(FavoritosActivity.this, null, response);
                    cargarDesdeLocalYRenderError(err);
                }
            }

            @Override
            public void onFailure(@NonNull Call<FavoritosResponse> call, @NonNull Throwable t) {
                cargarDesdeLocalYRender(null);
            }
        });
    }

    private void cargarDesdeLocalYRenderError(String err) {
        executor.execute(() -> {
            List<FavoritoItem> combinados = leerLocales();
            runOnUiThread(() -> {
                if (!combinados.isEmpty()) render(combinados);
                else showEmpty(err);
            });
        });
    }

    private void cargarDesdeLocalYRender(List<FavoritoItem> apiItems) {
        executor.execute(() -> {
            Map<Integer, FavoritoItem> mapa = new LinkedHashMap<>();
            if (apiItems != null) {
                for (FavoritoItem item : apiItems) {
                    mapa.put(item.idVendedor, item);
                    if (usuarioId > 0) {
                        db.favoritoDao().insertar(new Favorito(usuarioId, item.idVendedor));
                    }
                }
            }
            for (FavoritoItem local : leerLocales()) {
                if (!mapa.containsKey(local.idVendedor)) {
                    mapa.put(local.idVendedor, local);
                }
            }
            List<FavoritoItem> listaFinal = new ArrayList<>(mapa.values());
            runOnUiThread(() -> render(listaFinal));
        });
    }

    private List<FavoritoItem> leerLocales() {
        List<FavoritoItem> out = new ArrayList<>();
        if (usuarioId < 1) return out;
        SharedPreferences cache = getSharedPreferences("tamalon_fav_cache_" + usuarioId, MODE_PRIVATE);
        List<Favorito> locales = db.favoritoDao().obtenerListaPorCliente(usuarioId);
        for (Favorito f : locales) {
            String nombre = cache.getString("vendor_" + f.vendedorId, null);
            String foto = cache.getString("foto_" + f.vendedorId, null);
            if (nombre == null || nombre.trim().isEmpty() || foto == null) {
                Usuario u = db.usuarioDao().obtenerPorId(f.vendedorId);
                if (nombre == null || nombre.trim().isEmpty()) {
                    nombre = (u != null && u.nombreCompleto != null) ? u.nombreCompleto : ("Vendedor #" + f.vendedorId);
                }
                if (foto == null && u != null) {
                    foto = u.fotoUri;
                }
            }
            out.add(new FavoritoItem(f.vendedorId, nombre, foto));
        }
        return out;
    }

    private void render(List<FavoritoItem> items) {
        results.removeAllViews();
        int total = items == null ? 0 : items.size();
        if (countText != null) {
            countText.setText(total + " de 5 guardados");
        }
        if (items == null || items.isEmpty()) {
            showEmpty("Todavía no guardaste ningún puesto. Puedes guardarlos desde la Lista.");
            return;
        }
        for (FavoritoItem item : items) {
            LinearLayout card = new LinearLayout(this);
            card.setOrientation(LinearLayout.VERTICAL);
            card.setPadding(dp(14), dp(14), dp(14), dp(14));
            GradientDrawable bg = new GradientDrawable();
            bg.setColor(getColor(R.color.tamalon_surface));
            bg.setCornerRadius(dp(16));
            bg.setStroke(dp(1), getColor(R.color.tamalon_line));
            card.setBackground(bg);

            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
            lp.bottomMargin = dp(10);

            LinearLayout header = new LinearLayout(this);
            header.setOrientation(LinearLayout.HORIZONTAL);
            header.setGravity(Gravity.CENTER_VERTICAL);

            ImageView ivAvatar = new ImageView(this);
            ivAvatar.setBackgroundResource(R.drawable.bg_circle);
            ivAvatar.setClipToOutline(true);
            ImagenPerfil.mostrar(ivAvatar, item.fotoPerfil, this);
            LinearLayout.LayoutParams avatarLp = new LinearLayout.LayoutParams(dp(42), dp(42));
            avatarLp.setMarginEnd(dp(12));
            header.addView(ivAvatar, avatarLp);

            TextView row = new TextView(this);
            row.setText(item.nombre);
            row.setTextSize(15f);
            row.setTextColor(getColor(R.color.tamalon_ink));
            row.setTypeface(null, Typeface.BOLD);
            header.addView(row, new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f));

            card.addView(header);

            LinearLayout actions = new LinearLayout(this);
            actions.setOrientation(LinearLayout.HORIZONTAL);
            LinearLayout.LayoutParams actLp = new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
            actLp.topMargin = dp(10);
            actions.setLayoutParams(actLp);

            Button btnCatalog = new Button(this);
            btnCatalog.setText("Ver catálogo");
            btnCatalog.setAllCaps(false);
            btnCatalog.setTextSize(10f);
            btnCatalog.setMinHeight(0);
            btnCatalog.setMinimumHeight(0);
            btnCatalog.setBackgroundTintList(null);
            btnCatalog.setBackgroundResource(R.drawable.bg_button);
            btnCatalog.setTextColor(getColor(R.color.tamalon_ink));
            btnCatalog.setOnClickListener(v -> {
                Intent i = new Intent(this, CatalogoPublicoActivity.class);
                i.putExtra("vendedor_id", item.idVendedor);
                i.putExtra("plan_cliente", plan);
                i.putExtra("vendedor_nombre", item.nombre);
                startActivity(i);
            });

            Button btnRemove = new Button(this);
            btnRemove.setText("Quitar de favoritos");
            btnRemove.setAllCaps(false);
            btnRemove.setTextSize(10f);
            btnRemove.setMinHeight(0);
            btnRemove.setMinimumHeight(0);
            btnRemove.setBackgroundTintList(null);
            GradientDrawable remBg = new GradientDrawable();
            remBg.setColor(getColor(R.color.tamalon_surface_strong));
            remBg.setCornerRadius(dp(10));
            remBg.setStroke(dp(1), getColor(R.color.tamalon_error));
            btnRemove.setBackground(remBg);
            btnRemove.setTextColor(getColor(R.color.tamalon_error));
            btnRemove.setOnClickListener(v -> quitarFavorito(item));

            LinearLayout.LayoutParams p1 = new LinearLayout.LayoutParams(0, dp(36), 1f);
            p1.setMarginEnd(dp(6));
            LinearLayout.LayoutParams p2 = new LinearLayout.LayoutParams(0, dp(36), 1f);
            p2.setMarginStart(dp(6));
            actions.addView(btnCatalog, p1);
            actions.addView(btnRemove, p2);
            card.addView(actions);

            results.addView(card, lp);
        }
        empty.setVisibility(View.GONE);
        results.setVisibility(View.VISIBLE);
    }

    private void quitarFavorito(FavoritoItem item) {
        if (usuarioId > 0) {
            getSharedPreferences("tamalon_fav_cache_" + usuarioId, MODE_PRIVATE)
                    .edit().remove("vendor_" + item.idVendedor).remove("foto_" + item.idVendedor).apply();
            executor.execute(() -> db.favoritoDao().eliminarPorIds(usuarioId, item.idVendedor));
        }
        apiService.eliminarFavorito(item.idVendedor).enqueue(new Callback<Void>() {
            @Override
            public void onResponse(@NonNull Call<Void> call, @NonNull Response<Void> response) {
                Toast.makeText(FavoritosActivity.this, "Quitado de favoritos", Toast.LENGTH_SHORT).show();
                cargarDesdeLocalYRender(null);
            }

            @Override
            public void onFailure(@NonNull Call<Void> call, @NonNull Throwable t) {
                Toast.makeText(FavoritosActivity.this, "Quitado de favoritos", Toast.LENGTH_SHORT).show();
                cargarDesdeLocalYRender(null);
            }
        });
    }

    private void showEmpty(String message) {
        if (countText != null) countText.setText("0 de 5 guardados");
        empty.setVisibility(View.VISIBLE); results.setVisibility(View.GONE);
        if (empty instanceof LinearLayout) {
            LinearLayout l = (LinearLayout) empty;
            if (l.getChildCount() > 0 && l.getChildAt(0) instanceof TextView) ((TextView) l.getChildAt(0)).setText(message);
        }
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
