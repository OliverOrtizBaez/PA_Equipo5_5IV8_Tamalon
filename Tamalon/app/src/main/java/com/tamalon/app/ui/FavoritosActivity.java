package com.tamalon.app.ui;

import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;

import com.tamalon.app.BuildConfig;
import com.tamalon.app.R;
import com.tamalon.app.network.*;

import java.util.ArrayList;
import java.util.List;

import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

public class FavoritosActivity extends AppCompatActivity {
    private static final String TAG = "FavoritosActivity";
    private static boolean loggedFavoritos = false;

    private ApiService apiService;
    private int usuarioId = -1;
    private String rol = "cliente", plan = "gratuito";
    private LinearLayout results;
    private View empty;

    @Override protected void onCreate(Bundle state) {
        super.onCreate(state);
        setContentView(R.layout.activity_favorites);
        usuarioId = getIntent().getIntExtra("usuario_id", -1);
        if (getIntent().getStringExtra("rol") != null) rol = getIntent().getStringExtra("rol");
        if (getIntent().getStringExtra("plan") != null) plan = getIntent().getStringExtra("plan");

        apiService = ApiClient.getApiService(this);
        results = findViewById(R.id.favorites_results);
        empty = findViewById(R.id.favorites_empty);
        BottomNavigation.configurar(this, findViewById(android.R.id.content), usuarioId, rol, plan);

        if (!"cliente".equalsIgnoreCase(rol)) { showEmpty("Los favoritos están disponibles para cuentas de cliente."); return; }
        if (!("premium".equalsIgnoreCase(plan))) { showEmpty("Esta función es exclusiva de cuentas Premium."); Toast.makeText(this, "Esta función es exclusiva de cuentas Premium", Toast.LENGTH_LONG).show(); return; }

        cargarFavoritos();
    }

    private void cargarFavoritos() {
        apiService.obtenerFavoritos().enqueue(new Callback<FavoritosResponse>() {
            @Override
            public void onResponse(@NonNull Call<FavoritosResponse> call, @NonNull Response<FavoritosResponse> response) {
                if (response.isSuccessful() && response.body() != null) {
                    if (BuildConfig.DEBUG && !loggedFavoritos) {
                        loggedFavoritos = true;
                        Log.d(TAG, "GET /api/favoritos HTTP " + response.code() + " total=" + (response.body().favoritos != null ? response.body().favoritos.size() : 0));
                    }
                    List<String> names = new ArrayList<>();
                    if (response.body().favoritos != null) {
                        for (FavoritoDto f : response.body().favoritos) {
                            String name = f.nombreVendedor != null ? f.nombreVendedor : (f.vendedor != null ? f.vendedor.nombreCompleto : "Puesto de Antojitos");
                            names.add(name);
                        }
                    }
                    render(names);
                } else {
                    String err = ApiErrorHelper.parseError(FavoritosActivity.this, null, response);
                    showEmpty(err);
                }
            }

            @Override
            public void onFailure(@NonNull Call<FavoritosResponse> call, @NonNull Throwable t) {
                String err = ApiErrorHelper.parseError(FavoritosActivity.this, t, null);
                showEmpty(err);
            }
        });
    }

    private void render(List<String> names) {
        results.removeAllViews();
        if (names == null || names.isEmpty()) { showEmpty("Todavía no guardaste ningún puesto. Puedes guardarlos desde la Lista."); return; }
        for (String name : names) {
            TextView row = new TextView(this); row.setText("♥  " + name); row.setTextSize(15); row.setTextColor(0xFF342C24); row.setTypeface(null, Typeface.BOLD); row.setPadding(18, 18, 18, 18);
            GradientDrawable bg = new GradientDrawable(); bg.setColor(Color.WHITE); bg.setCornerRadius(22); bg.setStroke(1, 0xFFE5DED1); row.setBackground(bg);
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1, -2); lp.bottomMargin = 10; results.addView(row, lp);
        }
        empty.setVisibility(View.GONE); results.setVisibility(View.VISIBLE);
    }

    private void showEmpty(String message) {
        empty.setVisibility(View.VISIBLE); results.setVisibility(View.GONE);
        if (empty instanceof LinearLayout) {
            LinearLayout l = (LinearLayout) empty;
            if (l.getChildCount() > 0 && l.getChildAt(0) instanceof TextView) ((TextView) l.getChildAt(0)).setText(message);
        }
    }
}
