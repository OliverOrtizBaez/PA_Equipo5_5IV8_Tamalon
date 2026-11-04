package com.tamalon.app.ui;

import android.content.Intent;
import android.content.res.ColorStateList;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.RippleDrawable;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
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

import androidx.appcompat.app.AppCompatActivity;

import com.tamalon.app.R;
import com.tamalon.app.data.local.AppDatabase;
import com.tamalon.app.model.Favorito;
import com.tamalon.app.model.Usuario;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Pantalla de búsqueda de vendedores y antojitos de la zona con filtros interactivos
 * y tarjeta de descripción del puesto seleccionado.
 */
public class ListaVendedoresActivity extends AppCompatActivity {

    private final ExecutorService executor = Executors.newSingleThreadExecutor();
    private AppDatabase db;
    private int usuarioId = -1;
    private String rol = "cliente";
    private String plan = "gratuito";
    private String filtroTexto = "";
    private String categoriaSeleccionada = "Todos";

    private final String[] categorias = {
            "Todos", "Tamales", "Atole", "Quesadillas", "Pambazos", "Sopes", "Champurrado", "Tacos"
    };

    private ScrollView scrollView;
    private LinearLayout resultsContainer;
    private View emptyState;
    private TextView tvCountBadge;
    private LinearLayout categoryFiltersContainer;

    // Tarjeta de descripción del puesto seleccionado
    private LinearLayout selectedCard;
    private TextView tvSelectedName;
    private TextView tvSelectedStatus;
    private TextView tvSelectedDesc;
    private Button btnSelectedCatalog;
    private Button btnSelectedFavorite;
    private ImageButton btnSelectedClose;
    private Usuario vendedorSeleccionadoActual;

    private final List<Usuario> listaVendedores = new ArrayList<>();

    @Override
    protected void onCreate(Bundle state) {
        super.onCreate(state);
        setContentView(R.layout.activity_list);

        usuarioId = getIntent().getIntExtra("usuario_id", -1);
        if (getIntent().getStringExtra("rol") != null) rol = getIntent().getStringExtra("rol");
        if (getIntent().getStringExtra("plan") != null) plan = getIntent().getStringExtra("plan");

        db = AppDatabase.getInstance(this);

        scrollView = findViewById(R.id.list_scroll_view);
        resultsContainer = findViewById(R.id.list_results);
        emptyState = findViewById(R.id.list_empty_state);
        tvCountBadge = findViewById(R.id.list_count_badge);
        categoryFiltersContainer = findViewById(R.id.list_category_filters);

        // Notificaciones
        View btnNotificaciones = findViewById(R.id.list_btn_notifications);
        if (btnNotificaciones != null) {
            btnNotificaciones.setOnClickListener(v ->
                    Toast.makeText(this, "Notificaciones: Tienes alertas de puestos en ruta en tu zona", Toast.LENGTH_LONG).show());
        }

        // Configurar tarjeta de descripción seleccionada
        selectedCard = findViewById(R.id.list_selected_card);
        tvSelectedName = findViewById(R.id.list_selected_name);
        tvSelectedStatus = findViewById(R.id.list_selected_status);
        tvSelectedDesc = findViewById(R.id.list_selected_description);
        btnSelectedCatalog = findViewById(R.id.list_selected_btn_catalog);
        btnSelectedFavorite = findViewById(R.id.list_selected_btn_favorite);
        btnSelectedClose = findViewById(R.id.list_selected_btn_close);

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
                    i.putExtra("vendedor_id", vendedorSeleccionadoActual.id);
                    startActivity(i);
                }
            });
        }

        if (btnSelectedFavorite != null) {
            btnSelectedFavorite.setOnClickListener(v -> {
                if (vendedorSeleccionadoActual != null) {
                    guardarFavorito(vendedorSeleccionadoActual);
                }
            });
        }

        // Barra inferior
        BottomNavigation.configurar(this, findViewById(android.R.id.content), usuarioId, rol, plan);

        // Buscador
        EditText search = findViewById(R.id.list_search);
        search.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
            @Override public void onTextChanged(CharSequence s, int start, int before, int count) {
                filtroTexto = s.toString().trim().toLowerCase();
                render();
            }
            @Override public void afterTextChanged(Editable e) {}
        });

        // Construir chips de categorías
        construirChipsCategorias();

        // Cargar datos locales de Room
        db.usuarioDao().obtenerTodos().observe(this, users -> {
            listaVendedores.clear();
            if (users != null) {
                for (Usuario u : users) {
                    if (u.esVendedor()) {
                        listaVendedores.add(u);
                    }
                }
            }
            render();
        });
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
                categoriaSeleccionada = cat;
                construirChipsCategorias();
                render();
            });

            categoryFiltersContainer.addView(chip);
        }
    }

    private void actualizarEstiloChip(Button chip, String cat) {
        boolean activa = cat.equalsIgnoreCase(categoriaSeleccionada);
        int colorFondo = activa ? getColor(R.color.tamalon_sand_strong) : getColor(R.color.tamalon_surface);
        int colorTexto = getColor(R.color.tamalon_field_text);
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

    private void render() {
        resultsContainer.removeAllViews();
        int count = 0;

        for (Usuario u : listaVendedores) {
            String nombre = u.nombreCompleto == null ? "" : u.nombreCompleto.toLowerCase();
            if (!filtroTexto.isEmpty() && !nombre.contains(filtroTexto)) {
                continue;
            }

            count++;
            final Usuario vendedorItem = u;

            // Tarjeta de puesto
            LinearLayout card = new LinearLayout(this);
            card.setOrientation(LinearLayout.HORIZONTAL);
            card.setGravity(Gravity.CENTER_VERTICAL);
            card.setPadding(dp(14), dp(12), dp(14), dp(12));

            GradientDrawable bg = new GradientDrawable();
            bg.setColor(Color.WHITE);
            bg.setCornerRadius(dp(16));
            bg.setStroke(dp(1), getColor(R.color.tamalon_line));
            card.setBackground(bg);

            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
            lp.bottomMargin = dp(10);
            card.setLayoutParams(lp);

            // Icono / avatar
            ImageView ivAvatar = new ImageView(this);
            ivAvatar.setImageResource(R.drawable.ic_user);
            ivAvatar.setBackgroundResource(R.drawable.bg_circle);
            ivAvatar.setPadding(dp(8), dp(8), dp(8), dp(8));
            LinearLayout.LayoutParams avatarLp = new LinearLayout.LayoutParams(dp(40), dp(40));
            avatarLp.setMarginEnd(dp(12));
            card.addView(ivAvatar, avatarLp);

            // Textos info
            LinearLayout info = new LinearLayout(this);
            info.setOrientation(LinearLayout.VERTICAL);

            TextView tvName = new TextView(this);
            tvName.setText(u.nombreCompleto == null ? "Puesto de Antojitos" : u.nombreCompleto);
            tvName.setTextColor(getColor(R.color.tamalon_ink));
            tvName.setTextSize(14f);
            tvName.setTypeface(null, Typeface.BOLD);
            info.addView(tvName);

            TextView tvSub = new TextView(this);
            tvSub.setText("Especialidad en antojitos · En ruta activa");
            tvSub.setTextColor(getColor(R.color.tamalon_muted));
            tvSub.setTextSize(10f);
            tvSub.setPadding(0, dp(2), 0, 0);
            info.addView(tvSub);

            card.addView(info, new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f));

            // Botón rápido de seleccionar/ver descripción
            Button btnVer = new Button(this);
            btnVer.setText("Detalle");
            btnVer.setAllCaps(false);
            btnVer.setTextSize(10f);
            btnVer.setPadding(dp(10), dp(4), dp(10), dp(4));
            btnVer.setBackgroundResource(R.drawable.bg_surface_card);
            btnVer.setTextColor(getColor(R.color.tamalon_ink));
            btnVer.setOnClickListener(v -> seleccionarVendedor(vendedorItem));
            card.addView(btnVer);

            // Al tocar la tarjeta completa, también se selecciona y muestra la descripción
            card.setOnClickListener(v -> seleccionarVendedor(vendedorItem));

            resultsContainer.addView(card);
        }

        if (tvCountBadge != null) {
            tvCountBadge.setText(count + (count == 1 ? " puesto" : " puestos"));
        }

        emptyState.setVisibility(count == 0 ? View.VISIBLE : View.GONE);
        resultsContainer.setVisibility(count == 0 ? View.GONE : View.VISIBLE);
    }

    private void seleccionarVendedor(Usuario vendedor) {
        vendedorSeleccionadoActual = vendedor;
        if (selectedCard == null) return;

        if (tvSelectedName != null) {
            tvSelectedName.setText(vendedor.nombreCompleto == null ? "Puesto de Antojitos" : vendedor.nombreCompleto);
        }
        if (tvSelectedStatus != null) {
            tvSelectedStatus.setText("● En ruta activa · Radio 3 km");
        }
        if (tvSelectedDesc != null) {
            tvSelectedDesc.setText("Puesto seleccionado: Venta de antojitos tradicionales preparados al día. Consulta el catálogo para ver productos disponibles.");
        }

        selectedCard.setVisibility(View.VISIBLE);

        // Desplazar suavemente para que la descripción quede a la vista
        if (scrollView != null) {
            scrollView.smoothScrollTo(0, selectedCard.getTop());
        }
    }

    private void guardarFavorito(Usuario vendedor) {
        if (usuarioId < 1) {
            Toast.makeText(this, "No se pudo identificar tu cuenta", Toast.LENGTH_SHORT).show();
            return;
        }
        if (!"premium".equals(plan)) {
            Toast.makeText(this, "Guardar puestos en favoritos requiere el plan Premium", Toast.LENGTH_LONG).show();
            return;
        }
        executor.execute(() -> {
            int count = db.favoritoDao().contarPorCliente(usuarioId);
            if (count >= 5) {
                runOnUiThread(() -> Toast.makeText(this, "Ya alcanzaste el máximo de 5 favoritos", Toast.LENGTH_LONG).show());
                return;
            }
            long id = db.favoritoDao().insertar(new Favorito(usuarioId, vendedor.id));
            runOnUiThread(() -> Toast.makeText(this, id == -1 ? "Este puesto ya está guardado" : "Puesto guardado en favoritos", Toast.LENGTH_SHORT).show());
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
