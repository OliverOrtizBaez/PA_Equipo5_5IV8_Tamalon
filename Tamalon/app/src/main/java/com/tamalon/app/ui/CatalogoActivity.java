package com.tamalon.app.ui;

import android.content.Intent;
import android.graphics.Typeface;
import android.os.Bundle;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.tamalon.app.R;
import com.tamalon.app.data.local.AppDatabase;
import com.tamalon.app.model.Catalogo;
import com.tamalon.app.model.Usuario;
import com.tamalon.app.service.AutenticacionService;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Gestión de catálogos del Vendedor — Tamalón.
 * Estructura consistente con Configuración y Privacidad y seguridad.
 */
public class CatalogoActivity extends AppCompatActivity {

    private final ExecutorService executor = Executors.newSingleThreadExecutor();
    private AppDatabase db;
    private int usuarioId = -1;
    private String rol = "vendedor", plan = "gratuito";

    private RecyclerView recycler;
    private View emptyState, notice, form;
    private EditText nameInput;
    private TextView errorText;
    private CatalogAdapter adapter;

    private int catalogoCount = 0;

    @Override
    protected void onCreate(Bundle state) {
        super.onCreate(state);
        setContentView(R.layout.activity_catalogs);

        usuarioId = getIntent().getIntExtra("usuario_id", -1);
        if (getIntent().getStringExtra("rol") != null) rol = getIntent().getStringExtra("rol");
        if (getIntent().getStringExtra("plan") != null) plan = getIntent().getStringExtra("plan");

        db = AppDatabase.getInstance(this);

        recycler = findViewById(R.id.catalogs_recycler);
        emptyState = findViewById(R.id.catalogs_empty_state);
        notice = findViewById(R.id.catalogs_limit_notice);
        form = findViewById(R.id.catalogs_form_panel);
        nameInput = findViewById(R.id.catalogs_form_name);
        errorText = findViewById(R.id.catalogs_form_error);

        recycler.setLayoutManager(new LinearLayoutManager(this));
        adapter = new CatalogAdapter();
        recycler.setAdapter(adapter);

        findViewById(R.id.catalogs_btn_add).setOnClickListener(v -> onAddCatalogClicked());

        View btnUpgrade = findViewById(R.id.catalogs_btn_upgrade);
        if (btnUpgrade != null) {
            btnUpgrade.setOnClickListener(v -> {
                Intent i = new Intent(this, PagoActivity.class);
                i.putExtra("usuario_id", usuarioId);
                i.putExtra("rol", rol);
                startActivity(i);
            });
        }

        findViewById(R.id.catalogs_form_cancel).setOnClickListener(v -> {
            form.setVisibility(View.GONE);
            errorText.setVisibility(View.GONE);
        });

        findViewById(R.id.catalogs_form_save).setOnClickListener(v -> guardar());

        if (!"vendedor".equals(rol)) {
            Toast.makeText(this, "Esta pantalla es solo para vendedores", Toast.LENGTH_LONG).show();
            finish();
            return;
        }

        if (usuarioId < 1) {
            new AutenticacionService(this).obtenerUsuarioActivo(new AutenticacionService.Callback<Usuario>() {
                @Override
                public void onExito(Usuario u) {
                    runOnUiThread(() -> {
                        usuarioId = u.id;
                        plan = u.plan;
                        observarCatalogos();
                    });
                }

                @Override
                public void onError(String m) {
                    runOnUiThread(() -> {
                        Toast.makeText(CatalogoActivity.this, "Inicia sesión nuevamente", Toast.LENGTH_LONG).show();
                        finish();
                    });
                }
            });
        } else {
            observarCatalogos();
        }
    }

    private void onAddCatalogClicked() {
        if (!esPremium() && catalogoCount >= 1) {
            // Cuenta gratuita: máximo 1 catálogo. Muestra aviso de Premium
            if (notice != null) {
                notice.setVisibility(View.VISIBLE);
            }
        } else {
            if (form != null) {
                form.setVisibility(form.getVisibility() == View.VISIBLE ? View.GONE : View.VISIBLE);
            }
        }
    }

    private void observarCatalogos() {
        db.catalogoDao().obtenerPorVendedor(usuarioId).observe(this, list -> {
            adapter.setItems(list);
            catalogoCount = list.size();

            if (emptyState != null) {
                emptyState.setVisibility(list.isEmpty() ? View.VISIBLE : View.GONE);
            }

            // Ocultar aviso si tiene 0 catálogos
            if (list.isEmpty() && notice != null) {
                notice.setVisibility(View.GONE);
            }
        });
    }

    private boolean esPremium() {
        return "premium".equals(plan);
    }

    private void guardar() {
        String value = nameInput.getText().toString().trim();
        if (value.isEmpty()) {
            errorText.setText("Escribe un nombre para el catálogo.");
            errorText.setVisibility(View.VISIBLE);
            return;
        }

        executor.execute(() -> {
            int count = db.catalogoDao().contarVisiblesPorVendedor(usuarioId);
            if (!esPremium() && count >= 1) {
                runOnUiThread(() -> {
                    errorText.setText("Una cuenta gratuita puede crear 1 catálogo.");
                    errorText.setVisibility(View.VISIBLE);
                    if (notice != null) notice.setVisibility(View.VISIBLE);
                });
                return;
            }

            db.catalogoDao().insertar(new Catalogo(usuarioId, value));
            runOnUiThread(() -> {
                nameInput.setText("");
                errorText.setVisibility(View.GONE);
                form.setVisibility(View.GONE);
                Toast.makeText(this, "Catálogo creado", Toast.LENGTH_SHORT).show();
            });
        });
    }

    private class CatalogAdapter extends RecyclerView.Adapter<CatalogAdapter.Holder> {
        private final List<Catalogo> items = new ArrayList<>();

        void setItems(List<Catalogo> values) {
            items.clear();
            items.addAll(values);
            notifyDataSetChanged();
        }

        @Override
        public Holder onCreateViewHolder(ViewGroup parent, int type) {
            // Tarjeta estilo Configuración / Privacidad y seguridad
            LinearLayout card = new LinearLayout(parent.getContext());
            card.setOrientation(LinearLayout.HORIZONTAL);
            card.setGravity(Gravity.CENTER_VERTICAL);
            card.setPadding(dp(16), dp(14), dp(16), dp(14));
            card.setBackgroundResource(R.drawable.bg_surface_card);

            RecyclerView.LayoutParams lp = new RecyclerView.LayoutParams(
                    RecyclerView.LayoutParams.MATCH_PARENT, RecyclerView.LayoutParams.WRAP_CONTENT);
            lp.bottomMargin = dp(10);
            card.setLayoutParams(lp);

            // Icono dentro de círculo
            ImageView ivIcon = new ImageView(parent.getContext());
            ivIcon.setImageResource(R.drawable.ic_list);
            ivIcon.setBackgroundResource(R.drawable.bg_circle);
            ivIcon.setPadding(dp(8), dp(8), dp(8), dp(8));
            LinearLayout.LayoutParams iconLp = new LinearLayout.LayoutParams(dp(40), dp(40));
            iconLp.setMarginEnd(dp(12));
            card.addView(ivIcon, iconLp);

            // Info de textos
            LinearLayout info = new LinearLayout(parent.getContext());
            info.setOrientation(LinearLayout.VERTICAL);

            TextView tvTitle = new TextView(parent.getContext());
            tvTitle.setTextColor(0xFF262522);
            tvTitle.setTextSize(14f);
            tvTitle.setTypeface(null, Typeface.BOLD);
            info.addView(tvTitle);

            TextView tvSubtitle = new TextView(parent.getContext());
            tvSubtitle.setTextColor(0xFF817970);
            tvSubtitle.setTextSize(10f);
            tvSubtitle.setPadding(0, dp(3), 0, 0);
            info.addView(tvSubtitle);

            card.addView(info, new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f));

            // Icono de chevron
            ImageView ivChevron = new ImageView(parent.getContext());
            ivChevron.setImageResource(R.drawable.ic_chevron);
            card.addView(ivChevron, new LinearLayout.LayoutParams(dp(18), dp(18)));

            return new Holder(card, tvTitle, tvSubtitle);
        }

        @Override
        public void onBindViewHolder(Holder h, int position) {
            Catalogo c = items.get(position);
            h.title.setText(c.nombre);
            h.subtitle.setText(c.visible ? "Catálogo visible · En tu puesto" : "Catálogo oculto");

            h.itemView.setOnClickListener(v -> {
                Toast.makeText(CatalogoActivity.this, "Catálogo: " + c.nombre, Toast.LENGTH_SHORT).show();
            });
        }

        @Override
        public int getItemCount() {
            return items.size();
        }

        class Holder extends RecyclerView.ViewHolder {
            TextView title, subtitle;

            Holder(View v, TextView title, TextView subtitle) {
                super(v);
                this.title = title;
                this.subtitle = subtitle;
            }
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
