package com.tamalon.app.ui;

import android.content.Intent;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.text.InputFilter;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.tamalon.app.R;
import com.tamalon.app.data.local.AppDatabase;
import com.tamalon.app.model.Catalogo;
import com.tamalon.app.model.Producto;
import com.tamalon.app.network.ApiClient;
import com.tamalon.app.network.ApiService;
import com.tamalon.app.network.CatalogoDto;
import com.tamalon.app.network.CrearCatalogoRequest;
import com.tamalon.app.network.ProductoDto;
import com.tamalon.app.network.ProductosResponse;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

/**
 * Pantalla de administración de productos dentro de un catálogo (RF11).
 * Sin IDs técnicos. Los productos en estado «Oculto» no muestran la opción Eliminar.
 */
public class CatalogoProductosActivity extends AppCompatActivity {

    private final ExecutorService executor = Executors.newSingleThreadExecutor();
    private AppDatabase db;
    private ApiService apiService;

    private int catalogoLocalId = -1;
    private int catalogoRemoteId = 0;
    private int vendedorId = -1;
    private String catalogoNombre = "Catálogo";
    private String catalogoEstado = "Activo";
    private long catalogoFecha = 0L;
    private String plan = "gratuito";

    private TextView tvStateBadge, tvCountBadge, tvTitle;
    private RecyclerView recycler;
    private View emptyState;
    private ProductAdapter adapter;
    private int visibleProductCount = 0;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_catalog_products);

        db = AppDatabase.getInstance(this);
        apiService = ApiClient.getApiService(this);

        catalogoLocalId = getIntent().getIntExtra("catalogo_id", -1);
        catalogoRemoteId = getIntent().getIntExtra("catalogo_remote_id", 0);
        vendedorId = getIntent().getIntExtra("vendedor_id", -1);
        if (getIntent().getStringExtra("catalogo_nombre") != null) {
            catalogoNombre = getIntent().getStringExtra("catalogo_nombre");
        }
        if (getIntent().getStringExtra("catalogo_estado") != null) {
            catalogoEstado = getIntent().getStringExtra("catalogo_estado");
        }
        catalogoFecha = getIntent().getLongExtra("catalogo_fecha", 0L);
        if (getIntent().getStringExtra("plan") != null) {
            plan = getIntent().getStringExtra("plan");
        }

        tvStateBadge = findViewById(R.id.catalog_products_state_badge);
        tvCountBadge = findViewById(R.id.catalog_products_count_badge);
        tvTitle = findViewById(R.id.catalog_products_title);
        recycler = findViewById(R.id.catalog_products_recycler);
        emptyState = findViewById(R.id.catalog_products_empty);

        recycler.setLayoutManager(new LinearLayoutManager(this));
        adapter = new ProductAdapter();
        recycler.setAdapter(adapter);

        Button btnAdd = findViewById(R.id.catalog_products_btn_add);
        Button btnEditCat = findViewById(R.id.catalog_products_btn_edit_catalog);
        Button btnDeleteCat = findViewById(R.id.catalog_products_btn_delete_catalog);

        if (btnAdd != null) {
            btnAdd.setBackgroundTintList(null);
            btnAdd.setOnClickListener(v -> {
                int max = "premium".equalsIgnoreCase(plan) ? 50 : 10;
                if (visibleProductCount >= max) {
                    Toast.makeText(this, "Alcanzaste el límite de productos visibles de tu plan (" + max + ")", Toast.LENGTH_LONG).show();
                    return;
                }
                abrirFormularioProducto(null);
            });
        }
        if (btnEditCat != null) {
            btnEditCat.setBackgroundTintList(null);
            btnEditCat.setOnClickListener(v -> mostrarDialogoEditarCatalogo());
        }
        if (btnDeleteCat != null) {
            btnDeleteCat.setBackgroundTintList(null);
            btnDeleteCat.setOnClickListener(v -> confirmarBorrarCatalogo());
        }

        actualizarEncabezado();
        cargarProductos();
    }

    @Override
    protected void onResume() {
        super.onResume();
        cargarProductos();
    }

    private void actualizarEncabezado() {
        if (tvTitle != null) tvTitle.setText(catalogoNombre);
        if (tvStateBadge != null) {
            boolean activo = !"Oculto".equalsIgnoreCase(catalogoEstado) && !"Inactivo".equalsIgnoreCase(catalogoEstado);
            tvStateBadge.setText(activo ? "Visible" : "Oculto");
            tvStateBadge.setTextColor(getColor(activo ? R.color.tamalon_ink : R.color.tamalon_muted));
        }
    }

    private void cargarProductos() {
        recargarCatalogoYProductosDesdeRoom();
        if (catalogoRemoteId > 0) {
            apiService.obtenerProductosCatalogo(catalogoRemoteId).enqueue(new Callback<ProductosResponse>() {
                @Override
                public void onResponse(@NonNull Call<ProductosResponse> call, @NonNull Response<ProductosResponse> response) {
                    if (response.isSuccessful() && response.body() != null && response.body().productos != null) {
                        sincronizarProductosConRoom(response.body().productos);
                    } else {
                        cargarProductosDesdeVendedorFallback();
                    }
                }

                @Override
                public void onFailure(@NonNull Call<ProductosResponse> call, @NonNull Throwable t) {
                    cargarProductosDesdeVendedorFallback();
                }
            });
        }
    }

    private void cargarProductosDesdeVendedorFallback() {
        if (vendedorId < 1 || catalogoRemoteId < 1) return;
        apiService.obtenerProductosVendedor(vendedorId).enqueue(new Callback<ProductosResponse>() {
            @Override
            public void onResponse(@NonNull Call<ProductosResponse> call, @NonNull Response<ProductosResponse> response) {
                if (response.isSuccessful() && response.body() != null && response.body().productos != null) {
                    List<ProductoDto> filtrados = new ArrayList<>();
                    for (ProductoDto p : response.body().productos) {
                        if (p.idCatalogo == catalogoRemoteId) filtrados.add(p);
                    }
                    sincronizarProductosConRoom(filtrados);
                }
            }
            @Override public void onFailure(@NonNull Call<ProductosResponse> call, @NonNull Throwable t) { }
        });
    }

    private void sincronizarProductosConRoom(List<ProductoDto> apiList) {
        List<Producto> directos = new ArrayList<>();
        int visDirectos = 0;
        for (ProductoDto dto : apiList) {
            Producto p = new Producto(
                    catalogoLocalId,
                    vendedorId,
                    dto.nombre == null ? "Producto" : dto.nombre,
                    dto.descripcion == null ? "" : dto.descripcion,
                    dto.precio,
                    dto.categoria == null ? "Tamales" : dto.categoria,
                    dto.visible ? "Activo" : "Inactivo"
            );
            p.imagenUri = dto.getImagenEfectiva();
            p.remoteId = dto.idProducto;
            p.visible = !"Oculto".equalsIgnoreCase(p.estado) && !"Inactivo".equalsIgnoreCase(p.estado) && dto.visible;
            if (p.visible) visDirectos++;
            directos.add(p);
        }
        final int visFinal = visDirectos;
        runOnUiThread(() -> {
            visibleProductCount = visFinal;
            adapter.setItems(directos);
            if (tvCountBadge != null) {
                tvCountBadge.setText(directos.size() + (directos.size() == 1 ? " producto" : " productos"));
            }
            if (emptyState != null) {
                emptyState.setVisibility(directos.isEmpty() ? View.VISIBLE : View.GONE);
            }
            if (recycler != null) {
                recycler.setVisibility(directos.isEmpty() ? View.GONE : View.VISIBLE);
            }
        });

        if (catalogoLocalId < 1) return;
        executor.execute(() -> {
            try {
                Set<Integer> vistos = new HashSet<>();
                for (ProductoDto dto : apiList) {
                    if (dto.idProducto <= 0) continue;
                    vistos.add(dto.idProducto);
                    Producto existente = db.productoDao().obtenerPorRemoteId(catalogoLocalId, dto.idProducto);
                    if (existente == null) {
                        Producto nuevo = new Producto(
                                catalogoLocalId,
                                vendedorId,
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
                List<Producto> locales = db.productoDao().obtenerListaPorCatalogo(catalogoLocalId);
                for (Producto local : locales) {
                    if (local.remoteId > 0 && !vistos.contains(local.remoteId)) {
                        db.productoDao().eliminarPorId(local.id);
                    }
                }
                recargarCatalogoYProductosDesdeRoom();
            } catch (Exception ignored) { }
        });
    }

    private void recargarCatalogoYProductosDesdeRoom() {
        if (catalogoLocalId < 1) return;
        executor.execute(() -> {
            Catalogo cat = db.catalogoDao().obtenerPorId(catalogoLocalId);
            List<Producto> productos = db.productoDao().obtenerListaPorCatalogo(catalogoLocalId);
            int visibles = 0;
            for (Producto p : productos) {
                if (!"Oculto".equalsIgnoreCase(p.getEstadoEfectivo()) && !"Inactivo".equalsIgnoreCase(p.getEstadoEfectivo()) && p.visible) {
                    visibles++;
                }
            }
            final int visiblesFinal = visibles;
            runOnUiThread(() -> {
                visibleProductCount = visiblesFinal;
                if (cat != null) {
                    catalogoNombre = cat.nombre;
                    catalogoEstado = cat.getEstadoEfectivo();
                    catalogoFecha = cat.fechaCreacion;
                    catalogoRemoteId = cat.remoteId;
                    actualizarEncabezado();
                }
                adapter.setItems(productos);
                if (tvCountBadge != null) {
                    tvCountBadge.setText(productos.size() + (productos.size() == 1 ? " producto" : " productos"));
                }
                if (emptyState != null) {
                    emptyState.setVisibility(productos.isEmpty() ? View.VISIBLE : View.GONE);
                }
                if (recycler != null) {
                    recycler.setVisibility(productos.isEmpty() ? View.GONE : View.VISIBLE);
                }
            });
        });
    }

    private void abrirFormularioProducto(Producto producto) {
        Intent i = new Intent(this, ProductoFormActivity.class);
        i.putExtra("catalogo_id", catalogoLocalId);
        i.putExtra("catalogo_remote_id", catalogoRemoteId);
        i.putExtra("catalogo_nombre", catalogoNombre);
        i.putExtra("vendedor_id", vendedorId);
        i.putExtra("plan", plan);
        if (producto != null) {
            i.putExtra("producto_id", producto.id);
            i.putExtra("producto_remote_id", producto.remoteId);
            i.putExtra("producto_nombre", producto.nombre);
            i.putExtra("producto_descripcion", producto.descripcion);
            i.putExtra("producto_precio", producto.precioMxn);
            i.putExtra("producto_categoria", producto.categoria);
            i.putExtra("producto_imagen", producto.imagenUri);
            i.putExtra("producto_estado", producto.getEstadoEfectivo());
        }
        startActivity(i);
    }

    private void mostrarDialogoEditarCatalogo() {
        LinearLayout container = new LinearLayout(this);
        container.setOrientation(LinearLayout.VERTICAL);
        container.setPadding(dp(20), dp(12), dp(20), dp(8));

        EditText input = new EditText(this);
        input.setText(catalogoNombre);
        input.setHint("Nombre del catálogo (máx. 50)");
        input.setFilters(new InputFilter[]{new InputFilter.LengthFilter(50)});
        container.addView(input);

        CheckBox cbActivo = new CheckBox(this);
        cbActivo.setText("Catálogo en estado Visible");
        cbActivo.setChecked(!"Oculto".equalsIgnoreCase(catalogoEstado) && !"Inactivo".equalsIgnoreCase(catalogoEstado));
        LinearLayout.LayoutParams cbLp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        cbLp.topMargin = dp(8);
        container.addView(cbActivo, cbLp);

        new AlertDialog.Builder(this)
                .setTitle("Editar catálogo")
                .setView(container)
                .setNegativeButton("Cancelar", null)
                .setPositiveButton("Guardar", (d, w) -> {
                    String nuevoNombre = input.getText().toString().trim();
                    if (nuevoNombre.isEmpty()) {
                        Toast.makeText(this, "El nombre es obligatorio", Toast.LENGTH_SHORT).show();
                        return;
                    }
                    String nuevoEstado = cbActivo.isChecked() ? "Visible" : "Oculto";
                    guardarEdicionCatalogo(nuevoNombre, nuevoEstado);
                })
                .show();
    }

    private void guardarEdicionCatalogo(String nuevoNombre, String nuevoEstado) {
        if (catalogoRemoteId > 0) {
            CrearCatalogoRequest req = new CrearCatalogoRequest(nuevoNombre, nuevoEstado, vendedorId);
            apiService.actualizarCatalogo(catalogoRemoteId, req).enqueue(new Callback<CatalogoDto>() {
                @Override
                public void onResponse(@NonNull Call<CatalogoDto> call, @NonNull Response<CatalogoDto> response) {
                    actualizarCatalogoLocal(nuevoNombre, nuevoEstado);
                }

                @Override
                public void onFailure(@NonNull Call<CatalogoDto> call, @NonNull Throwable t) {
                    actualizarCatalogoLocal(nuevoNombre, nuevoEstado);
                }
            });
        } else {
            actualizarCatalogoLocal(nuevoNombre, nuevoEstado);
        }
    }

    private void actualizarCatalogoLocal(String nuevoNombre, String nuevoEstado) {
        executor.execute(() -> {
            Catalogo cat = db.catalogoDao().obtenerPorId(catalogoLocalId);
            if (cat != null) {
                cat.nombre = nuevoNombre;
                cat.estado = nuevoEstado;
                cat.visible = "Visible".equalsIgnoreCase(nuevoEstado);
                cat.fechaActualizacion = System.currentTimeMillis();
                db.catalogoDao().actualizar(cat);
            }
            runOnUiThread(() -> {
                catalogoNombre = nuevoNombre;
                catalogoEstado = nuevoEstado;
                actualizarEncabezado();
                Toast.makeText(this, "Catálogo actualizado", Toast.LENGTH_SHORT).show();
            });
        });
    }

    private void confirmarBorrarCatalogo() {
        new AlertDialog.Builder(this)
                .setTitle("Borrar catálogo")
                .setMessage("¿Eliminar \"" + catalogoNombre + "\" y todos sus productos asociados?")
                .setNegativeButton("Cancelar", null)
                .setPositiveButton("Borrar catálogo", (d, w) -> borrarCatalogoCompleto())
                .show();
    }

    private void borrarCatalogoCompleto() {
        if (catalogoRemoteId > 0) {
            apiService.eliminarCatalogo(catalogoRemoteId).enqueue(new Callback<Void>() {
                @Override
                public void onResponse(@NonNull Call<Void> call, @NonNull Response<Void> response) {
                    borrarCatalogoLocal();
                }

                @Override
                public void onFailure(@NonNull Call<Void> call, @NonNull Throwable t) {
                    borrarCatalogoLocal();
                }
            });
        } else {
            borrarCatalogoLocal();
        }
    }

    private void borrarCatalogoLocal() {
        executor.execute(() -> {
            db.productoDao().eliminarPorCatalogo(catalogoLocalId);
            db.catalogoDao().eliminarPorId(catalogoLocalId);
            runOnUiThread(() -> {
                Toast.makeText(this, "Catálogo eliminado", Toast.LENGTH_SHORT).show();
                finish();
            });
        });
    }

    private void confirmarEliminarProducto(Producto p) {
        new AlertDialog.Builder(this)
                .setTitle("Eliminar producto")
                .setMessage("¿Eliminar \"" + p.nombre + "\" de este catálogo?")
                .setNegativeButton("Cancelar", null)
                .setPositiveButton("Eliminar", (d, w) -> eliminarProducto(p))
                .show();
    }

    private void eliminarProducto(Producto p) {
        if (p.remoteId > 0) {
            apiService.eliminarProducto(p.remoteId).enqueue(new Callback<Void>() {
                @Override
                public void onResponse(@NonNull Call<Void> call, @NonNull Response<Void> response) {
                    eliminarProductoLocal(p);
                }

                @Override
                public void onFailure(@NonNull Call<Void> call, @NonNull Throwable t) {
                    eliminarProductoLocal(p);
                }
            });
        } else {
            eliminarProductoLocal(p);
        }
    }

    private void eliminarProductoLocal(Producto p) {
        executor.execute(() -> {
            db.productoDao().eliminarPorId(p.id);
            runOnUiThread(() -> {
                Toast.makeText(this, "Producto eliminado", Toast.LENGTH_SHORT).show();
                recargarCatalogoYProductosDesdeRoom();
            });
        });
    }

    private String formatearFecha(long millis) {
        if (millis <= 0) return "Hoy";
        return new SimpleDateFormat("dd/MM/yyyy", new Locale("es", "MX")).format(new Date(millis));
    }

    private class ProductAdapter extends RecyclerView.Adapter<ProductAdapter.Holder> {
        private final List<Producto> items = new ArrayList<>();

        void setItems(List<Producto> list) {
            items.clear();
            if (list != null) items.addAll(list);
            notifyDataSetChanged();
        }

        @NonNull
        @Override
        public Holder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            LinearLayout card = new LinearLayout(parent.getContext());
            card.setOrientation(LinearLayout.VERTICAL);
            card.setPadding(dp(14), dp(14), dp(14), dp(14));
            card.setBackgroundResource(R.drawable.bg_surface_card);
            RecyclerView.LayoutParams lp = new RecyclerView.LayoutParams(
                    RecyclerView.LayoutParams.MATCH_PARENT, RecyclerView.LayoutParams.WRAP_CONTENT);
            lp.bottomMargin = dp(12);
            card.setLayoutParams(lp);

            LinearLayout topRow = new LinearLayout(parent.getContext());
            topRow.setOrientation(LinearLayout.HORIZONTAL);
            topRow.setGravity(Gravity.TOP);

            ImageView ivThumb = new ImageView(parent.getContext());
            ivThumb.setScaleType(ImageView.ScaleType.CENTER_CROP);
            GradientDrawable imgBg = new GradientDrawable();
            imgBg.setColor(getColor(R.color.tamalon_sand));
            imgBg.setCornerRadius(dp(12));
            ivThumb.setBackground(imgBg);
            ivThumb.setClipToOutline(true);
            LinearLayout.LayoutParams imgLp = new LinearLayout.LayoutParams(dp(74), dp(74));
            imgLp.setMarginEnd(dp(12));
            topRow.addView(ivThumb, imgLp);

            LinearLayout info = new LinearLayout(parent.getContext());
            info.setOrientation(LinearLayout.VERTICAL);

            LinearLayout badgeRow = new LinearLayout(parent.getContext());
            badgeRow.setOrientation(LinearLayout.HORIZONTAL);
            badgeRow.setGravity(Gravity.CENTER_VERTICAL);

            TextView tvState = new TextView(parent.getContext());
            tvState.setTextSize(9f);
            tvState.setTypeface(null, Typeface.BOLD);
            tvState.setPadding(dp(8), dp(2), dp(8), dp(2));
            badgeRow.addView(tvState);

            View spacer = new View(parent.getContext());
            badgeRow.addView(spacer, new LinearLayout.LayoutParams(0, 1, 1f));

            TextView tvPrice = new TextView(parent.getContext());
            tvPrice.setTextColor(getColor(R.color.tamalon_ink));
            tvPrice.setTextSize(13f);
            tvPrice.setTypeface(null, Typeface.BOLD);
            badgeRow.addView(tvPrice);

            info.addView(badgeRow);

            TextView tvName = new TextView(parent.getContext());
            tvName.setTextColor(getColor(R.color.tamalon_ink));
            tvName.setTextSize(14f);
            tvName.setTypeface(null, Typeface.BOLD);
            tvName.setPadding(0, dp(4), 0, 0);
            info.addView(tvName);

            TextView tvDesc = new TextView(parent.getContext());
            tvDesc.setTextColor(getColor(R.color.tamalon_field_text));
            tvDesc.setTextSize(10.5f);
            tvDesc.setPadding(0, dp(3), 0, 0);
            info.addView(tvDesc);

            TextView tvCategory = new TextView(parent.getContext());
            tvCategory.setTextColor(getColor(R.color.tamalon_ink));
            tvCategory.setTextSize(9.5f);
            tvCategory.setTypeface(null, Typeface.BOLD);
            tvCategory.setPadding(dp(8), dp(3), dp(8), dp(3));
            LinearLayout.LayoutParams catLp = new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT);
            catLp.topMargin = dp(6);
            info.addView(tvCategory, catLp);

            TextView tvMeta = new TextView(parent.getContext());
            tvMeta.setTextColor(getColor(R.color.tamalon_muted));
            tvMeta.setTextSize(8.5f);
            tvMeta.setPadding(0, dp(4), 0, 0);
            info.addView(tvMeta);

            topRow.addView(info, new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f));
            card.addView(topRow);

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
            GradientDrawable editBg = new GradientDrawable();
            editBg.setColor(getColor(R.color.tamalon_surface_strong));
            editBg.setCornerRadius(dp(10));
            editBg.setStroke(dp(1), getColor(R.color.tamalon_line));
            btnEdit.setBackground(editBg);
            btnEdit.setTextColor(getColor(R.color.tamalon_field_text));

            Button btnDelete = new Button(parent.getContext());
            btnDelete.setText("Eliminar");
            btnDelete.setAllCaps(false);
            btnDelete.setTextSize(10f);
            btnDelete.setMinHeight(0);
            btnDelete.setMinimumHeight(0);
            btnDelete.setBackgroundTintList(null);
            GradientDrawable delBg = new GradientDrawable();
            delBg.setColor(getColor(R.color.tamalon_surface_strong));
            delBg.setCornerRadius(dp(10));
            delBg.setStroke(dp(1), getColor(R.color.tamalon_error));
            btnDelete.setBackground(delBg);
            btnDelete.setTextColor(getColor(R.color.tamalon_error));

            LinearLayout.LayoutParams eLp = new LinearLayout.LayoutParams(0, dp(36), 1f);
            eLp.setMarginEnd(dp(6));
            LinearLayout.LayoutParams dLp = new LinearLayout.LayoutParams(0, dp(36), 1f);
            dLp.setMarginStart(dp(6));
            actions.addView(btnEdit, eLp);
            actions.addView(btnDelete, dLp);
            card.addView(actions);

            return new Holder(card, ivThumb, tvState, tvPrice, tvName, tvDesc, tvCategory, tvMeta, btnEdit, btnDelete);
        }

        @Override
        public void onBindViewHolder(@NonNull Holder h, int position) {
            Producto p = items.get(position);
            boolean activo = !"Oculto".equalsIgnoreCase(p.getEstadoEfectivo())
                    && !"Inactivo".equalsIgnoreCase(p.getEstadoEfectivo())
                    && p.visible;
            String estado = activo ? "Visible" : "Oculto";

            ImagenPerfil.mostrarProducto(h.thumb, p.imagenUri, CatalogoProductosActivity.this);

            GradientDrawable stBg = new GradientDrawable();
            stBg.setColor(getColor(activo ? R.color.tamalon_sand_strong : R.color.tamalon_sand));
            stBg.setCornerRadius(dp(8));
            h.state.setBackground(stBg);
            h.state.setText(estado);
            h.state.setTextColor(getColor(activo ? R.color.tamalon_ink : R.color.tamalon_muted));

            h.price.setText(String.format(Locale.US, "$%.2f", p.precioMxn));
            h.name.setText(p.nombre);
            h.desc.setText(p.descripcion);

            GradientDrawable catBg = new GradientDrawable();
            catBg.setColor(getColor(R.color.tamalon_sand));
            catBg.setCornerRadius(dp(12));
            h.category.setBackground(catBg);
            h.category.setText(p.categoria == null || p.categoria.trim().isEmpty() ? "Sin categoría" : p.categoria);

            h.meta.setText("Creado: " + formatearFecha(p.fechaCreacion));
            h.btnDelete.setVisibility(activo ? View.VISIBLE : View.GONE);

            h.itemView.setOnClickListener(v -> abrirFormularioProducto(p));
            h.btnEdit.setOnClickListener(v -> abrirFormularioProducto(p));
            h.btnDelete.setOnClickListener(v -> confirmarEliminarProducto(p));
        }

        @Override
        public int getItemCount() {
            return items.size();
        }

        class Holder extends RecyclerView.ViewHolder {
            ImageView thumb;
            TextView state, price, name, desc, category, meta;
            Button btnEdit, btnDelete;

            Holder(View v, ImageView thumb, TextView state, TextView price,
                   TextView name, TextView desc, TextView category, TextView meta,
                   Button btnEdit, Button btnDelete) {
                super(v);
                this.thumb = thumb;
                this.state = state;
                this.price = price;
                this.name = name;
                this.desc = desc;
                this.category = category;
                this.meta = meta;
                this.btnEdit = btnEdit;
                this.btnDelete = btnDelete;
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
