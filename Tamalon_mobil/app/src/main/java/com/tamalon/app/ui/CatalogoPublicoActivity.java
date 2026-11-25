package com.tamalon.app.ui;

import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import com.tamalon.app.R;
import com.tamalon.app.data.local.AppDatabase;
import com.tamalon.app.model.Catalogo;
import com.tamalon.app.model.Producto;
import com.tamalon.app.model.Usuario;
import com.tamalon.app.network.ApiClient;
import com.tamalon.app.network.ApiService;
import com.tamalon.app.network.CatalogoDto;
import com.tamalon.app.network.CatalogosResponse;
import com.tamalon.app.network.ProductoDto;
import com.tamalon.app.network.ProductosResponse;
import com.tamalon.app.service.AutenticacionService;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

/**
 * Vista pública del catálogo de un Vendedor (RF12).
 * Cliente Gratuito: muestra únicamente Nombre, Imagen y Categoría de productos «Visible».
 * Cliente Premium: muestra Nombre, Imagen, Categoría, Precio y Descripción de productos «Visible».
 * Catálogo vacío: «Este catálogo aún no tiene productos».
 * Vendedor sin catálogos visibles: «Este Vendedor aún no tiene catálogos».
 */
public class CatalogoPublicoActivity extends AppCompatActivity {

    private final ExecutorService executor = Executors.newSingleThreadExecutor();
    private AppDatabase db;
    private ApiService apiService;
    private int vendedorId;
    private String planCliente = "gratuito";
    private TextView tvVendorName, tvPlanBadge, tvUpgradeNotice, tvEmptyText;
    private View emptyView;
    private RecyclerView recycler;
    private PublicCatalogAdapter adapter;

    private static class RowItem {
        static final int TYPE_CATALOG_HEADER = 0;
        static final int TYPE_PRODUCT = 1;
        static final int TYPE_EMPTY_CATALOG = 2;
        final int type;
        final String catalogName;
        final ProductoDto product;
        RowItem(int type, String catalogName, ProductoDto product) {
            this.type = type;
            this.catalogName = catalogName;
            this.product = product;
        }
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_public_catalog);

        db = AppDatabase.getInstance(this);
        apiService = ApiClient.getApiService(this);
        vendedorId = getIntent().getIntExtra("vendedor_id", -1);
        if (getIntent().getStringExtra("plan_cliente") != null) {
            planCliente = getIntent().getStringExtra("plan_cliente");
        }
        String vendorNameExtra = getIntent().getStringExtra("vendedor_nombre");

        tvVendorName = findViewById(R.id.catalog_vendor_name);
        tvPlanBadge = findViewById(R.id.catalog_plan_badge);
        tvUpgradeNotice = findViewById(R.id.catalog_upgrade_notice);
        emptyView = findViewById(R.id.catalog_empty);
        tvEmptyText = findViewById(R.id.catalog_empty_text);
        recycler = findViewById(R.id.catalog_recycler);

        if (vendorNameExtra != null && !vendorNameExtra.trim().isEmpty() && tvVendorName != null) {
            tvVendorName.setText(vendorNameExtra.trim());
        }

        recycler.setLayoutManager(new LinearLayoutManager(this));
        adapter = new PublicCatalogAdapter();
        recycler.setAdapter(adapter);

        new AutenticacionService(this).obtenerUsuarioActivo(new AutenticacionService.Callback<Usuario>() {
            @Override
            public void onExito(Usuario u) {
                runOnUiThread(() -> {
                    if (u.plan != null) planCliente = u.plan;
                    actualizarPlanUI();
                    cargarCatalogosYProductos();
                });
            }
            @Override
            public void onError(String m) {
                runOnUiThread(() -> {
                    actualizarPlanUI();
                    cargarCatalogosYProductos();
                });
            }
        });
    }

    private boolean esPremium() {
        return "premium".equalsIgnoreCase(planCliente);
    }

    private void actualizarPlanUI() {
        boolean prem = esPremium();
        if (tvPlanBadge != null) tvPlanBadge.setText(prem ? "Premium" : "Gratuito");
        if (tvUpgradeNotice != null) tvUpgradeNotice.setVisibility(prem ? View.GONE : View.VISIBLE);
    }

    private void cargarCatalogosYProductos() {
        if (vendedorId < 1) {
            mostrarVendedorSinCatalogos();
            return;
        }
        apiService.obtenerCatalogosVendedor(vendedorId).enqueue(new Callback<CatalogosResponse>() {
            @Override
            public void onResponse(@NonNull Call<CatalogosResponse> call, @NonNull Response<CatalogosResponse> response) {
                if (response.isSuccessful() && response.body() != null && response.body().catalogos != null) {
                    List<CatalogoDto> visibles = new ArrayList<>();
                    for (CatalogoDto c : response.body().catalogos) {
                        String est = c.visible ? "Activo" : "Inactivo";
                        if (!"Oculto".equalsIgnoreCase(est) && !"Inactivo".equalsIgnoreCase(est) && c.visible) {
                            visibles.add(c);
                        }
                    }
                    if (visibles.isEmpty()) {
                        mostrarVendedorSinCatalogos();
                    } else {
                        cargarProductosApiParaCatalogos(visibles);
                    }
                } else {
                    cargarDesdeRoom();
                }
            }

            @Override
            public void onFailure(@NonNull Call<CatalogosResponse> call, @NonNull Throwable t) {
                cargarDesdeRoom();
            }
        });
    }

    private void cargarProductosApiParaCatalogos(List<CatalogoDto> catalogosVisibles) {
        apiService.obtenerProductosVendedor(vendedorId).enqueue(new Callback<ProductosResponse>() {
            @Override
            public void onResponse(@NonNull Call<ProductosResponse> call, @NonNull Response<ProductosResponse> response) {
                List<ProductoDto> todos = (response.isSuccessful() && response.body() != null && response.body().productos != null)
                        ? response.body().productos : new ArrayList<>();
                Map<Integer, List<ProductoDto>> agrupados = new LinkedHashMap<>();
                for (CatalogoDto c : catalogosVisibles) {
                    agrupados.put(c.idCatalogo, new ArrayList<>());
                }
                for (ProductoDto p : todos) {
                    String est = p.visible ? "Activo" : "Inactivo";
                    boolean vis = !"Oculto".equalsIgnoreCase(est) && !"Inactivo".equalsIgnoreCase(est) && p.visible;
                    if (vis && agrupados.containsKey(p.idCatalogo)) {
                        agrupados.get(p.idCatalogo).add(p);
                    }
                }
                executor.execute(() -> {
                    List<Catalogo> localesCat = db.catalogoDao().obtenerVisiblesPorVendedor(vendedorId);
                    for (CatalogoDto c : catalogosVisibles) {
                        List<ProductoDto> prods = agrupados.get(c.idCatalogo);
                        if (prods != null && prods.isEmpty()) {
                            for (Catalogo lc : localesCat) {
                                if (lc.remoteId == c.idCatalogo) {
                                    for (Producto lp : db.productoDao().obtenerListaPorCatalogo(lc.id)) {
                                        if (!"Oculto".equalsIgnoreCase(lp.getEstadoEfectivo()) && !"Inactivo".equalsIgnoreCase(lp.getEstadoEfectivo()) && lp.visible) {
                                            ProductoDto dto = new ProductoDto();
                                            dto.nombre = lp.nombre;
                                            dto.descripcion = lp.descripcion;
                                            dto.precio = lp.precioMxn;
                                            dto.categoria = lp.categoria;
                                            dto.imagen = lp.imagenUri;
                                            dto.estado = lp.getEstadoEfectivo();
                                            dto.visible = lp.visible;
                                            prods.add(dto);
                                        }
                                    }
                                }
                            }
                        }
                    }
                    List<RowItem> rows = new ArrayList<>();
                    for (CatalogoDto c : catalogosVisibles) {
                        rows.add(new RowItem(RowItem.TYPE_CATALOG_HEADER, c.nombre, null));
                        List<ProductoDto> prods = agrupados.get(c.idCatalogo);
                        if (prods == null || prods.isEmpty()) {
                            rows.add(new RowItem(RowItem.TYPE_EMPTY_CATALOG, c.nombre, null));
                        } else {
                            for (ProductoDto p : prods) {
                                rows.add(new RowItem(RowItem.TYPE_PRODUCT, c.nombre, p));
                            }
                        }
                    }
                    runOnUiThread(() -> mostrarFilas(rows));
                });
            }

            @Override
            public void onFailure(@NonNull Call<ProductosResponse> call, @NonNull Throwable t) {
                cargarDesdeRoom();
            }
        });
    }

    private void cargarDesdeRoom() {
        executor.execute(() -> {
            Usuario vendedor = db.usuarioDao().obtenerPorId(vendedorId);
            List<Catalogo> cats = db.catalogoDao().obtenerVisiblesPorVendedor(vendedorId);
            List<RowItem> rows = new ArrayList<>();
            for (Catalogo c : cats) {
                if ("Oculto".equalsIgnoreCase(c.getEstadoEfectivo()) || "Inactivo".equalsIgnoreCase(c.getEstadoEfectivo())) continue;
                rows.add(new RowItem(RowItem.TYPE_CATALOG_HEADER, c.nombre, null));
                List<Producto> prods = db.productoDao().obtenerListaPorCatalogo(c.id);
                int countVis = 0;
                for (Producto p : prods) {
                    if ("Oculto".equalsIgnoreCase(p.getEstadoEfectivo()) || "Inactivo".equalsIgnoreCase(p.getEstadoEfectivo()) || !p.visible) continue;
                    countVis++;
                    ProductoDto dto = new ProductoDto();
                    dto.nombre = p.nombre;
                    dto.descripcion = p.descripcion;
                    dto.precio = p.precioMxn;
                    dto.categoria = p.categoria;
                    dto.imagen = p.imagenUri;
                    dto.estado = p.getEstadoEfectivo();
                    dto.visible = p.visible;
                    rows.add(new RowItem(RowItem.TYPE_PRODUCT, c.nombre, dto));
                }
                if (countVis == 0) {
                    rows.add(new RowItem(RowItem.TYPE_EMPTY_CATALOG, c.nombre, null));
                }
            }
            runOnUiThread(() -> {
                if (vendedor != null && vendedor.nombreCompleto != null && tvVendorName != null) {
                    tvVendorName.setText(vendedor.nombreCompleto);
                }
                if (rows.isEmpty()) {
                    mostrarVendedorSinCatalogos();
                } else {
                    mostrarFilas(rows);
                }
            });
        });
    }

    private void mostrarVendedorSinCatalogos() {
        if (emptyView != null) emptyView.setVisibility(View.VISIBLE);
        if (tvEmptyText != null) tvEmptyText.setText("Este Vendedor aún no tiene catálogos");
        if (recycler != null) recycler.setVisibility(View.GONE);
    }

    private void mostrarFilas(List<RowItem> rows) {
        if (rows.isEmpty()) {
            mostrarVendedorSinCatalogos();
            return;
        }
        if (emptyView != null) emptyView.setVisibility(View.GONE);
        if (recycler != null) recycler.setVisibility(View.VISIBLE);
        adapter.setItems(rows);
    }

    private class PublicCatalogAdapter extends RecyclerView.Adapter<RecyclerView.ViewHolder> {
        private final List<RowItem> items = new ArrayList<>();
        void setItems(List<RowItem> next) {
            items.clear();
            items.addAll(next);
            notifyDataSetChanged();
        }

        @Override
        public int getItemViewType(int position) {
            return items.get(position).type;
        }

        @NonNull
        @Override
        public RecyclerView.ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            if (viewType == RowItem.TYPE_CATALOG_HEADER) {
                TextView tv = new TextView(parent.getContext());
                tv.setTextColor(getColor(R.color.tamalon_ink));
                tv.setTextSize(15f);
                tv.setTypeface(null, Typeface.BOLD);
                tv.setPadding(0, dp(12), 0, dp(6));
                return new TextHolder(tv);
            }
            if (viewType == RowItem.TYPE_EMPTY_CATALOG) {
                TextView tv = new TextView(parent.getContext());
                tv.setTextColor(getColor(R.color.tamalon_muted));
                tv.setTextSize(11f);
                tv.setGravity(Gravity.CENTER);
                tv.setPadding(dp(16), dp(18), dp(16), dp(18));
                tv.setBackgroundResource(R.drawable.bg_empty);
                RecyclerView.LayoutParams lp = new RecyclerView.LayoutParams(
                        RecyclerView.LayoutParams.MATCH_PARENT, RecyclerView.LayoutParams.WRAP_CONTENT);
                lp.bottomMargin = dp(10);
                tv.setLayoutParams(lp);
                return new TextHolder(tv);
            }

            LinearLayout card = new LinearLayout(parent.getContext());
            card.setOrientation(LinearLayout.HORIZONTAL);
            card.setGravity(Gravity.TOP);
            card.setPadding(dp(12), dp(12), dp(12), dp(12));
            card.setBackgroundResource(R.drawable.bg_surface_card);
            RecyclerView.LayoutParams lp = new RecyclerView.LayoutParams(
                    RecyclerView.LayoutParams.MATCH_PARENT, RecyclerView.LayoutParams.WRAP_CONTENT);
            lp.bottomMargin = dp(10);
            card.setLayoutParams(lp);

            ImageView thumb = new ImageView(parent.getContext());
            thumb.setScaleType(ImageView.ScaleType.CENTER_CROP);
            GradientDrawable imgBg = new GradientDrawable();
            imgBg.setColor(getColor(R.color.tamalon_sand));
            imgBg.setCornerRadius(dp(12));
            thumb.setBackground(imgBg);
            thumb.setClipToOutline(true);
            LinearLayout.LayoutParams thumbLp = new LinearLayout.LayoutParams(dp(70), dp(70));
            thumbLp.setMarginEnd(dp(12));
            card.addView(thumb, thumbLp);

            LinearLayout info = new LinearLayout(parent.getContext());
            info.setOrientation(LinearLayout.VERTICAL);

            TextView tvName = new TextView(parent.getContext());
            tvName.setTextColor(getColor(R.color.tamalon_ink));
            tvName.setTextSize(14f);
            tvName.setTypeface(null, Typeface.BOLD);
            info.addView(tvName);

            TextView tvCat = new TextView(parent.getContext());
            tvCat.setTextColor(getColor(R.color.tamalon_field_text));
            tvCat.setTextSize(9.5f);
            tvCat.setTypeface(null, Typeface.BOLD);
            tvCat.setPadding(dp(8), dp(2), dp(8), dp(2));
            GradientDrawable catBg = new GradientDrawable();
            catBg.setColor(getColor(R.color.tamalon_sand));
            catBg.setCornerRadius(dp(10));
            tvCat.setBackground(catBg);
            LinearLayout.LayoutParams catLp = new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT);
            catLp.topMargin = dp(4);
            info.addView(tvCat, catLp);

            TextView tvPrice = new TextView(parent.getContext());
            tvPrice.setTextColor(getColor(R.color.tamalon_ink));
            tvPrice.setTextSize(13f);
            tvPrice.setTypeface(null, Typeface.BOLD);
            tvPrice.setPadding(0, dp(5), 0, 0);
            info.addView(tvPrice);

            TextView tvDesc = new TextView(parent.getContext());
            tvDesc.setTextColor(getColor(R.color.tamalon_field_text));
            tvDesc.setTextSize(10.5f);
            tvDesc.setPadding(0, dp(3), 0, 0);
            info.addView(tvDesc);

            card.addView(info, new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f));
            return new ProductHolder(card, thumb, tvName, tvCat, tvPrice, tvDesc);
        }

        @Override
        public void onBindViewHolder(@NonNull RecyclerView.ViewHolder holder, int position) {
            RowItem item = items.get(position);
            if (holder instanceof TextHolder) {
                TextView tv = ((TextHolder) holder).tv;
                tv.setText(item.type == RowItem.TYPE_EMPTY_CATALOG
                        ? "Este catálogo aún no tiene productos"
                        : item.catalogName);
                return;
            }
            ProductHolder ph = (ProductHolder) holder;
            ProductoDto p = item.product;
            ImagenPerfil.mostrarProducto(ph.thumb, p.getImagenEfectiva(), CatalogoPublicoActivity.this);
            ph.name.setText(p.nombre);
            ph.cat.setText(p.categoria == null || p.categoria.trim().isEmpty() ? "Tamales" : p.categoria);
            if (esPremium()) {
                ph.price.setVisibility(View.VISIBLE);
                ph.desc.setVisibility(View.VISIBLE);
                ph.price.setText(String.format(Locale.US, "$%.2f MXN", p.precio));
                ph.desc.setText(p.descripcion == null ? "" : p.descripcion);
            } else {
                ph.price.setVisibility(View.GONE);
                ph.desc.setVisibility(View.GONE);
            }
        }

        @Override
        public int getItemCount() {
            return items.size();
        }

        class TextHolder extends RecyclerView.ViewHolder {
            TextView tv;
            TextHolder(TextView tv) { super(tv); this.tv = tv; }
        }

        class ProductHolder extends RecyclerView.ViewHolder {
            ImageView thumb;
            TextView name, cat, price, desc;
            ProductHolder(View v, ImageView thumb, TextView name, TextView cat, TextView price, TextView desc) {
                super(v);
                this.thumb = thumb;
                this.name = name;
                this.cat = cat;
                this.price = price;
                this.desc = desc;
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
