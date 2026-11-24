package com.tamalon.app.ui;

import android.content.Intent;
import android.graphics.Typeface;
import android.os.Bundle;
import android.util.Log;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.tamalon.app.BuildConfig;
import com.tamalon.app.R;
import com.tamalon.app.model.Usuario;
import com.tamalon.app.network.*;
import com.tamalon.app.service.AutenticacionService;

import java.util.ArrayList;
import java.util.List;

import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

/**
 * Gestión de catálogos del Vendedor conectada a la API REST.
 */
public class CatalogoActivity extends AppCompatActivity {

    private static final String TAG = "CatalogoActivity";
    private static boolean loggedCatalogos = false;

    private ApiService apiService;
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

        apiService = ApiClient.getApiService(this);

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

        if (!"vendedor".equalsIgnoreCase(rol)) {
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
                        cargarCatalogosApi();
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
            cargarCatalogosApi();
        }
    }

    private void onAddCatalogClicked() {
        if (!esPremium() && catalogoCount >= 1) {
            if (notice != null) {
                notice.setVisibility(View.VISIBLE);
            }
        } else {
            if (form != null) {
                form.setVisibility(form.getVisibility() == View.VISIBLE ? View.GONE : View.VISIBLE);
            }
        }
    }

    private void cargarCatalogosApi() {
        apiService.obtenerMisCatalogos().enqueue(new Callback<CatalogosResponse>() {
            @Override
            public void onResponse(@NonNull Call<CatalogosResponse> call, @NonNull Response<CatalogosResponse> response) {
                if (response.isSuccessful() && response.body() != null) {
                    if (BuildConfig.DEBUG && !loggedCatalogos) {
                        loggedCatalogos = true;
                        Log.d(TAG, "GET /api/catalogos HTTP " + response.code() + " total=" + (response.body().catalogos != null ? response.body().catalogos.size() : 0));
                    }
                    List<CatalogoDto> list = response.body().catalogos != null ? response.body().catalogos : new ArrayList<>();
                    adapter.setItems(list);
                    catalogoCount = list.size();

                    if (emptyState != null) {
                        emptyState.setVisibility(list.isEmpty() ? View.VISIBLE : View.GONE);
                    }
                    if (list.isEmpty() && notice != null) {
                        notice.setVisibility(View.GONE);
                    }
                }
            }

            @Override
            public void onFailure(@NonNull Call<CatalogosResponse> call, @NonNull Throwable t) {
                Log.e(TAG, "Error cargando catálogos", t);
            }
        });
    }

    private boolean esPremium() {
        return "premium".equalsIgnoreCase(plan);
    }

    private void guardar() {
        String value = nameInput.getText().toString().trim();
        if (value.isEmpty()) {
            errorText.setText("Escribe un nombre para el catálogo.");
            errorText.setVisibility(View.VISIBLE);
            return;
        }

        if (!esPremium() && catalogoCount >= 1) {
            errorText.setText("Una cuenta gratuita puede crear 1 catálogo.");
            errorText.setVisibility(View.VISIBLE);
            if (notice != null) notice.setVisibility(View.VISIBLE);
            return;
        }

        CrearCatalogoRequest req = new CrearCatalogoRequest(value);
        apiService.crearCatalogo(req).enqueue(new Callback<CatalogoDto>() {
            @Override
            public void onResponse(@NonNull Call<CatalogoDto> call, @NonNull Response<CatalogoDto> response) {
                if (response.isSuccessful() && response.body() != null) {
                    nameInput.setText("");
                    errorText.setVisibility(View.GONE);
                    form.setVisibility(View.GONE);
                    Toast.makeText(CatalogoActivity.this, "Catálogo creado", Toast.LENGTH_SHORT).show();
                    cargarCatalogosApi();
                } else {
                    String err = ApiErrorHelper.parseError(CatalogoActivity.this, null, response);
                    errorText.setText(err);
                    errorText.setVisibility(View.VISIBLE);
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

    private class CatalogAdapter extends RecyclerView.Adapter<CatalogAdapter.Holder> {
        private final List<CatalogoDto> items = new ArrayList<>();

        void setItems(List<CatalogoDto> values) {
            items.clear();
            items.addAll(values);
            notifyDataSetChanged();
        }

        @Override
        public Holder onCreateViewHolder(ViewGroup parent, int type) {
            LinearLayout card = new LinearLayout(parent.getContext());
            card.setOrientation(LinearLayout.HORIZONTAL);
            card.setGravity(Gravity.CENTER_VERTICAL);
            card.setPadding(dp(16), dp(14), dp(16), dp(14));
            card.setBackgroundResource(R.drawable.bg_surface_card);

            RecyclerView.LayoutParams lp = new RecyclerView.LayoutParams(
                    RecyclerView.LayoutParams.MATCH_PARENT, RecyclerView.LayoutParams.WRAP_CONTENT);
            lp.bottomMargin = dp(10);
            card.setLayoutParams(lp);

            ImageView ivIcon = new ImageView(parent.getContext());
            ivIcon.setImageResource(R.drawable.ic_list);
            ivIcon.setBackgroundResource(R.drawable.bg_circle);
            ivIcon.setPadding(dp(8), dp(8), dp(8), dp(8));
            LinearLayout.LayoutParams iconLp = new LinearLayout.LayoutParams(dp(40), dp(40));
            iconLp.setMarginEnd(dp(12));
            card.addView(ivIcon, iconLp);

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

            ImageView ivChevron = new ImageView(parent.getContext());
            ivChevron.setImageResource(R.drawable.ic_chevron);
            card.addView(ivChevron, new LinearLayout.LayoutParams(dp(18), dp(18)));

            return new Holder(card, tvTitle, tvSubtitle);
        }

        @Override
        public void onBindViewHolder(Holder h, int position) {
            CatalogoDto c = items.get(position);
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
}
