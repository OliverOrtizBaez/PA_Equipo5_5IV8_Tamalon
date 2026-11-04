package com.tamalon.app.ui;

import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.view.Gravity;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;
import androidx.appcompat.app.AppCompatActivity;
import androidx.lifecycle.Observer;
import com.tamalon.app.R;
import com.tamalon.app.data.local.AppDatabase;
import com.tamalon.app.model.Favorito;
import com.tamalon.app.model.Usuario;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class FavoritosActivity extends AppCompatActivity {
    private final ExecutorService executor = Executors.newSingleThreadExecutor();
    private AppDatabase db;
    private int usuarioId = -1;
    private String rol = "cliente", plan = "gratuito";
    private LinearLayout results;
    private View empty;
    @Override protected void onCreate(Bundle state) {
        super.onCreate(state); setContentView(R.layout.activity_favorites);
        usuarioId = getIntent().getIntExtra("usuario_id", -1);
        if (getIntent().getStringExtra("rol") != null) rol = getIntent().getStringExtra("rol");
        if (getIntent().getStringExtra("plan") != null) plan = getIntent().getStringExtra("plan");
        db = AppDatabase.getInstance(this); results = findViewById(R.id.favorites_results); empty = findViewById(R.id.favorites_empty);
        BottomNavigation.configurar(this, findViewById(android.R.id.content), usuarioId, rol, plan);
        if (!"cliente".equals(rol) || usuarioId < 1) { showEmpty("Los favoritos están disponibles para cuentas de cliente."); return; }
        if (!("premium".equals(plan))) { showEmpty("Esta función es exclusiva de cuentas Premium."); Toast.makeText(this, "Esta función es exclusiva de cuentas Premium", Toast.LENGTH_LONG).show(); return; }
        db.favoritoDao().obtenerPorCliente(usuarioId).observe(this, favoritos -> cargarNombres(favoritos));
    }
    private void cargarNombres(List<Favorito> favoritos) {
        executor.execute(() -> {
            List<String> names = new ArrayList<>();
            for (Favorito f : favoritos) { Usuario u = db.usuarioDao().obtenerPorId(f.vendedorId); if (u != null) names.add(u.nombreCompleto); }
            runOnUiThread(() -> render(names));
        });
    }
    private void render(List<String> names) {
        results.removeAllViews();
        if (names.isEmpty()) { showEmpty("Todavía no guardaste ningún puesto. Puedes guardarlos desde la Lista."); return; }
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
    @Override protected void onDestroy() { super.onDestroy(); executor.shutdown(); }
}
