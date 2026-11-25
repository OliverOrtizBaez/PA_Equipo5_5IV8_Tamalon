package com.tamalon.app.ui;

import android.content.Intent;
import android.database.Cursor;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.os.Bundle;
import android.provider.OpenableColumns;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.Gravity;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.Switch;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;

import com.tamalon.app.R;
import com.tamalon.app.data.local.AppDatabase;
import com.tamalon.app.model.Producto;
import com.tamalon.app.network.ApiClient;
import com.tamalon.app.network.ApiErrorHelper;
import com.tamalon.app.network.ApiService;
import com.tamalon.app.network.CategoriaDto;
import com.tamalon.app.network.CategoriasResponse;
import com.tamalon.app.network.CrearProductoRequest;
import com.tamalon.app.network.DtoUser;
import com.tamalon.app.network.ProductoDto;
import com.tamalon.app.network.TokenStore;
import com.tamalon.app.validation.ResultadoValidacion;
import com.tamalon.app.validation.ValidadorProducto;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

/**
 * Formulario de creación y edición de producto.
 * Carga dinámicamente las categorías desde el servidor (GET api/tipos-vendedor/{id}/categorias).
 * Si falla la red, muestra error con botón "Reintentar" y deshabilita el botón Guardar.
 * Envía id_categoria en el POST/PUT del producto.
 */
public class ProductoFormActivity extends AppCompatActivity {

    private static final int REQUEST_PRODUCT_IMAGE = 805;

    private final ExecutorService executor = Executors.newSingleThreadExecutor();
    private AppDatabase db;
    private ApiService apiService;

    private int catalogoLocalId = -1;
    private int catalogoRemoteId = 0;
    private String catalogoNombre = "Catálogo";
    private int vendedorId = -1;
    private int productoLocalId = -1;
    private int productoRemoteId = 0;
    private Producto productoEditando = null;

    private TextView tvTitle, tvCatalogSubtitle;
    private ImageView ivPreview;
    private Button btnPickImage, btnSave;
    private TextView tvImageHint, tvImageError;
    private EditText etName, etDescription, etPrice;
    private TextView tvNameCount, tvNameError, tvDescCount, tvDescError, tvPriceError;
    private TextView tvCategorySummary, tvCategoryError, tvGlobalError;
    private LinearLayout categoryContainer;
    private Switch switchVisible;
    private Button btnDelete;

    private List<CategoriaDto> listaCategorias = new ArrayList<>();
    private int idCategoriaSeleccionada = -1;
    private String nombreCategoriaSeleccionada = "";
    private String categoriaPendienteEditar = null;
    private final List<Button> chipButtons = new ArrayList<>();
    private String imagenSeleccionada = "";
    private long imagenTamanoBytes = 0L;
    private String imagenNombreArchivo = "";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_product_form);

        db = AppDatabase.getInstance(this);
        apiService = ApiClient.getApiService(this);

        catalogoLocalId = getIntent().getIntExtra("catalogo_id", -1);
        catalogoRemoteId = getIntent().getIntExtra("catalogo_remote_id", 0);
        if (getIntent().getStringExtra("catalogo_nombre") != null) {
            catalogoNombre = getIntent().getStringExtra("catalogo_nombre");
        }
        vendedorId = getIntent().getIntExtra("vendedor_id", -1);
        productoLocalId = getIntent().getIntExtra("producto_id", -1);
        productoRemoteId = getIntent().getIntExtra("producto_remote_id", 0);

        tvTitle = findViewById(R.id.product_form_title);
        tvCatalogSubtitle = findViewById(R.id.product_catalog_subtitle);
        ivPreview = findViewById(R.id.product_image_preview);
        btnPickImage = findViewById(R.id.product_btn_pick_image);
        tvImageHint = findViewById(R.id.product_image_hint);
        tvImageError = findViewById(R.id.product_image_error);
        etName = findViewById(R.id.product_name);
        tvNameCount = findViewById(R.id.product_name_count);
        tvNameError = findViewById(R.id.product_name_error);
        etDescription = findViewById(R.id.product_description);
        tvDescCount = findViewById(R.id.product_description_count);
        tvDescError = findViewById(R.id.product_description_error);
        etPrice = findViewById(R.id.product_price);
        tvPriceError = findViewById(R.id.product_price_error);
        tvCategorySummary = findViewById(R.id.product_category_summary);
        tvCategoryError = findViewById(R.id.product_category_error);
        categoryContainer = findViewById(R.id.product_category_container);
        switchVisible = findViewById(R.id.product_visible);
        tvGlobalError = findViewById(R.id.product_global_error);
        btnDelete = findViewById(R.id.product_btn_delete);
        btnSave = findViewById(R.id.product_btn_save);

        if (tvCatalogSubtitle != null) {
            tvCatalogSubtitle.setText("Catálogo: " + catalogoNombre);
        }

        if (btnPickImage != null) {
            btnPickImage.setBackgroundTintList(null);
            btnPickImage.setOnClickListener(v -> elegirImagen());
        }

        Button btnCancel = findViewById(R.id.product_btn_cancel);
        if (btnCancel != null) {
            btnCancel.setBackgroundTintList(null);
            btnCancel.setOnClickListener(v -> finish());
        }

        if (btnSave != null) {
            btnSave.setBackgroundTintList(null);
            btnSave.setEnabled(false); // Deshabilitado hasta que carguen las categorías
            btnSave.setOnClickListener(v -> guardarProducto());
        }

        if (btnDelete != null) {
            btnDelete.setBackgroundTintList(null);
            btnDelete.setOnClickListener(v -> confirmarEliminar());
        }

        configurarContadores();
        cargarCategoriasServidor();

        if (productoLocalId > 0) {
            cargarProductoExistente();
        } else if (productoRemoteId > 0 || getIntent().hasExtra("producto_nombre")) {
            cargarProductoDesdeIntent();
        } else {
            if (tvTitle != null) tvTitle.setText("Crear producto");
        }
    }

    private void cargarCategoriasServidor() {
        if (btnSave != null) btnSave.setEnabled(false);
        if (tvCategorySummary != null) tvCategorySummary.setText("Cargando categorías...");

        TokenStore tokenStore = new TokenStore(this);
        DtoUser user = tokenStore.getUser();
        int idTipoVendedor = user != null && user.idTipoVendedor > 0 ? user.idTipoVendedor : 1;

        apiService.obtenerCategoriasTipoVendedor(idTipoVendedor).enqueue(new Callback<CategoriasResponse>() {
            @Override
            public void onResponse(@NonNull Call<CategoriasResponse> call, @NonNull Response<CategoriasResponse> response) {
                if (response.isSuccessful() && response.body() != null && response.body().categorias != null) {
                    listaCategorias = response.body().categorias;
                    construirChipsCategorias();

                    if (categoriaPendienteEditar != null && !categoriaPendienteEditar.isEmpty()) {
                        for (CategoriaDto c : listaCategorias) {
                            if (c.nombre.equalsIgnoreCase(categoriaPendienteEditar) || String.valueOf(c.idCategoria).equals(categoriaPendienteEditar)) {
                                idCategoriaSeleccionada = c.idCategoria;
                                nombreCategoriaSeleccionada = c.nombre;
                                break;
                            }
                        }
                        if (idCategoriaSeleccionada <= 0 && !listaCategorias.isEmpty()) {
                            idCategoriaSeleccionada = listaCategorias.get(0).idCategoria;
                            nombreCategoriaSeleccionada = listaCategorias.get(0).nombre;
                        }
                        actualizarVistaChips();
                        categoriaPendienteEditar = null;
                    }

                    if (btnSave != null) btnSave.setEnabled(true);
                    if (tvCategoryError != null) tvCategoryError.setVisibility(View.GONE);
                } else {
                    mostrarErrorCargarCategorias("No se pudieron cargar las categorías del servidor.");
                }
            }

            @Override
            public void onFailure(@NonNull Call<CategoriasResponse> call, @NonNull Throwable t) {
                mostrarErrorCargarCategorias("Error de conexión al cargar categorías.");
            }
        });
    }

    private void mostrarErrorCargarCategorias(String mensaje) {
        if (categoryContainer != null) {
            categoryContainer.removeAllViews();
            LinearLayout errorLayout = new LinearLayout(this);
            errorLayout.setOrientation(LinearLayout.VERTICAL);
            errorLayout.setGravity(Gravity.CENTER);

            TextView tvErr = new TextView(this);
            tvErr.setText(mensaje);
            tvErr.setTextColor(getColor(R.color.tamalon_error));
            tvErr.setTextSize(12.5f);
            tvErr.setGravity(Gravity.CENTER);
            errorLayout.addView(tvErr);

            Button btnRetry = new Button(this);
            btnRetry.setText("Reintentar");
            btnRetry.setBackgroundTintList(null);
            btnRetry.setOnClickListener(v -> cargarCategoriasServidor());
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT);
            lp.topMargin = dp(8);
            errorLayout.addView(btnRetry, lp);

            categoryContainer.addView(errorLayout);
        }
        if (tvCategorySummary != null) tvCategorySummary.setText("Error al cargar categorías");
    }

    private void cargarProductoDesdeIntent() {
        String nom = getIntent().getStringExtra("producto_nombre");
        String desc = getIntent().getStringExtra("producto_descripcion");
        double pre = getIntent().getDoubleExtra("producto_precio", 0.0);
        String catNombre = getIntent().getStringExtra("producto_categoria");
        String img = getIntent().getStringExtra("producto_imagen");
        String est = getIntent().getStringExtra("producto_estado");

        Producto p = new Producto(catalogoLocalId, vendedorId, nom == null ? "" : nom, desc == null ? "" : desc, pre, catNombre == null ? "" : catNombre, est == null ? "Visible" : est);
        p.remoteId = productoRemoteId;
        p.imagenUri = img;
        productoEditando = p;

        if (tvTitle != null) tvTitle.setText("Editar producto");
        if (etName != null) etName.setText(p.nombre);
        if (etDescription != null) etDescription.setText(p.descripcion);
        if (etPrice != null && pre > 0) etPrice.setText(String.format(Locale.US, "%.2f", pre));
        boolean visible = !"Oculto".equalsIgnoreCase(p.getEstadoEfectivo()) && !"Inactivo".equalsIgnoreCase(p.getEstadoEfectivo());
        if (switchVisible != null) switchVisible.setChecked(visible);
        if (img != null && !img.trim().isEmpty()) {
            imagenSeleccionada = img.trim();
            ivPreview.setPadding(0, 0, 0, 0);
            ivPreview.setScaleType(ImageView.ScaleType.CENTER_CROP);
            ImagenPerfil.mostrarProducto(ivPreview, imagenSeleccionada, this);
            if (tvImageHint != null) tvImageHint.setText("Fotografía cargada");
            if (btnPickImage != null) btnPickImage.setText("Cambiar foto");
        }
        if (catNombre != null && !catNombre.trim().isEmpty()) {
            categoriaPendienteEditar = catNombre.trim();
            for (CategoriaDto c : listaCategorias) {
                if (c.nombre.equalsIgnoreCase(catNombre.trim())) {
                    idCategoriaSeleccionada = c.idCategoria;
                    nombreCategoriaSeleccionada = c.nombre;
                    break;
                }
            }
        }
        actualizarVistaChips();
        if (btnDelete != null) btnDelete.setVisibility(visible ? View.VISIBLE : View.GONE);
    }

    private void configurarContadores() {
        if (etName != null) {
            etName.addTextChangedListener(new TextWatcher() {
                @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
                @Override public void onTextChanged(CharSequence s, int start, int before, int count) {
                    if (tvNameCount != null) tvNameCount.setText(s.length() + "/60");
                    if (tvNameError != null) tvNameError.setVisibility(View.GONE);
                }
                @Override public void afterTextChanged(Editable s) {}
            });
        }
        if (etDescription != null) {
            etDescription.addTextChangedListener(new TextWatcher() {
                @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
                @Override public void onTextChanged(CharSequence s, int start, int before, int count) {
                    if (tvDescCount != null) tvDescCount.setText(s.length() + "/300");
                    if (tvDescError != null) tvDescError.setVisibility(View.GONE);
                }
                @Override public void afterTextChanged(Editable s) {}
            });
        }
        if (etPrice != null) {
            etPrice.addTextChangedListener(new TextWatcher() {
                @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
                @Override public void onTextChanged(CharSequence s, int start, int before, int count) {
                    if (tvPriceError != null) tvPriceError.setVisibility(View.GONE);
                }
                @Override public void afterTextChanged(Editable s) {}
            });
        }
    }

    private void construirChipsCategorias() {
        if (categoryContainer == null) return;
        categoryContainer.removeAllViews();
        chipButtons.clear();

        if (listaCategorias.isEmpty()) return;

        LinearLayout filaActual = null;
        for (int i = 0; i < listaCategorias.size(); i++) {
            if (i % 2 == 0) {
                filaActual = new LinearLayout(this);
                filaActual.setOrientation(LinearLayout.HORIZONTAL);
                LinearLayout.LayoutParams rowLp = new LinearLayout.LayoutParams(
                        LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
                rowLp.bottomMargin = dp(8);
                categoryContainer.addView(filaActual, rowLp);
            }

            final CategoriaDto catDto = listaCategorias.get(i);
            Button chip = new Button(this);
            chip.setTag(catDto.idCategoria);
            chip.setAllCaps(false);
            chip.setTextSize(10f);
            chip.setMinHeight(0);
            chip.setMinimumHeight(0);
            chip.setBackgroundTintList(null);
            chip.setPadding(dp(8), dp(6), dp(8), dp(6));
            chip.setGravity(Gravity.CENTER);

            LinearLayout.LayoutParams chipLp = new LinearLayout.LayoutParams(0, dp(40), 1f);
            if (i % 2 == 0) chipLp.setMarginEnd(dp(8));
            chip.setLayoutParams(chipLp);

            chip.setOnClickListener(v -> seleccionarCategoria(catDto.idCategoria, catDto.nombre));
            chipButtons.add(chip);
            filaActual.addView(chip);
        }
        actualizarVistaChips();
    }

    private void seleccionarCategoria(int idCategoria, String nombre) {
        idCategoriaSeleccionada = idCategoria;
        nombreCategoriaSeleccionada = nombre;
        if (tvCategoryError != null) tvCategoryError.setVisibility(View.GONE);
        actualizarVistaChips();
    }

    private void actualizarVistaChips() {
        for (Button chip : chipButtons) {
            Object tag = chip.getTag();
            boolean activa = tag instanceof Integer && (Integer) tag == idCategoriaSeleccionada;
            String catName = "";
            for (CategoriaDto c : listaCategorias) {
                if (tag instanceof Integer && c.idCategoria == (Integer) tag) {
                    catName = c.nombre;
                    break;
                }
            }
            chip.setText((activa ? "✓ " : "") + catName);
            chip.setTypeface(null, activa ? Typeface.BOLD : Typeface.NORMAL);

            GradientDrawable bg = new GradientDrawable();
            bg.setColor(getColor(activa ? R.color.tamalon_sand_strong : R.color.tamalon_surface));
            bg.setCornerRadius(dp(18));
            bg.setStroke(dp(1), getColor(activa ? R.color.tamalon_heading_muted : R.color.tamalon_line));
            chip.setBackground(bg);
            chip.setBackgroundTintList(null);
            chip.setTextColor(getColor(activa ? R.color.tamalon_ink : R.color.tamalon_field_text));
        }

        if (tvCategorySummary != null) {
            tvCategorySummary.setText(idCategoriaSeleccionada <= 0
                    ? "Elige una categoría para este producto"
                    : "Categoría seleccionada: " + nombreCategoriaSeleccionada);
        }
    }

    private void cargarProductoExistente() {
        executor.execute(() -> {
            Producto p = db.productoDao().obtenerPorId(productoLocalId);
            if (p == null) return;
            productoEditando = p;
            productoRemoteId = p.remoteId;
            runOnUiThread(() -> {
                if (tvTitle != null) tvTitle.setText("Editar producto");
                if (etName != null) etName.setText(p.nombre);
                if (etDescription != null) etDescription.setText(p.descripcion);
                if (etPrice != null) etPrice.setText(String.format(Locale.US, "%.2f", p.precioMxn));
                boolean visible = !"Oculto".equalsIgnoreCase(p.getEstadoEfectivo()) && !"Inactivo".equalsIgnoreCase(p.getEstadoEfectivo()) && p.visible;
                if (switchVisible != null) {
                    switchVisible.setChecked(visible);
                }
                if (p.imagenUri != null && !p.imagenUri.trim().isEmpty()) {
                    imagenSeleccionada = p.imagenUri.trim();
                    ivPreview.setPadding(0, 0, 0, 0);
                    ivPreview.setScaleType(ImageView.ScaleType.CENTER_CROP);
                    ImagenPerfil.mostrarProducto(ivPreview, imagenSeleccionada, this);
                    if (tvImageHint != null) tvImageHint.setText("Fotografía cargada");
                    if (btnPickImage != null) btnPickImage.setText("Cambiar foto");
                }
                if (p.categoria != null && !p.categoria.trim().isEmpty()) {
                    categoriaPendienteEditar = p.categoria.trim();
                    for (CategoriaDto c : listaCategorias) {
                        if (c.nombre.equalsIgnoreCase(p.categoria.trim())) {
                            idCategoriaSeleccionada = c.idCategoria;
                            nombreCategoriaSeleccionada = c.nombre;
                            break;
                        }
                    }
                }
                actualizarVistaChips();
                if (btnDelete != null) btnDelete.setVisibility(visible ? View.VISIBLE : View.GONE);
            });
        });
    }

    private void elegirImagen() {
        Intent picker = new Intent(Intent.ACTION_OPEN_DOCUMENT);
        picker.addCategory(Intent.CATEGORY_OPENABLE);
        picker.setType("image/*");
        startActivityForResult(picker, REQUEST_PRODUCT_IMAGE);
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode != REQUEST_PRODUCT_IMAGE || resultCode != RESULT_OK || data == null || data.getData() == null) return;
        Uri origen = data.getData();
        String nombreArchivo = obtenerNombreArchivo(origen);
        long tamanoBytes = obtenerTamanoArchivo(origen);
        String mime = getContentResolver().getType(origen);

        boolean formatoValido = (mime != null && (mime.equalsIgnoreCase("image/jpeg") || mime.equalsIgnoreCase("image/jpg") || mime.equalsIgnoreCase("image/png")))
                || (nombreArchivo != null && (nombreArchivo.toLowerCase().endsWith(".jpg") || nombreArchivo.toLowerCase().endsWith(".jpeg") || nombreArchivo.toLowerCase().endsWith(".png")));

        if (!formatoValido) {
            if (tvImageError != null) {
                tvImageError.setText("Dato inválido en Fotografía: solo se aceptan imágenes JPG o PNG.");
                tvImageError.setVisibility(View.VISIBLE);
            }
            return;
        }
        if (tamanoBytes > 5 * 1024 * 1024L) {
            if (tvImageError != null) {
                tvImageError.setText("Dato inválido en Fotografía: el peso máximo permitido es 5 MB.");
                tvImageError.setVisibility(View.VISIBLE);
            }
            return;
        }

        executor.execute(() -> {
            try {
                String dataUri = ImagenPerfil.codificarComoDataUri(this, origen);
                runOnUiThread(() -> {
                    imagenSeleccionada = dataUri;
                    imagenTamanoBytes = tamanoBytes;
                    imagenNombreArchivo = nombreArchivo != null ? nombreArchivo : "producto.jpg";
                    if (tvImageError != null) tvImageError.setVisibility(View.GONE);
                    ivPreview.setPadding(0, 0, 0, 0);
                    ivPreview.setScaleType(ImageView.ScaleType.CENTER_CROP);
                    ImagenPerfil.mostrarProducto(ivPreview, imagenSeleccionada, this);
                    if (tvImageHint != null) tvImageHint.setText(imagenNombreArchivo);
                    if (btnPickImage != null) btnPickImage.setText("Cambiar foto");
                });
            } catch (Exception e) {
                runOnUiThread(() -> Toast.makeText(this, "No se pudo procesar la foto seleccionada", Toast.LENGTH_LONG).show());
            }
        });
    }

    private String obtenerNombreArchivo(Uri uri) {
        try (Cursor cursor = getContentResolver().query(uri, null, null, null, null)) {
            if (cursor != null && cursor.moveToFirst()) {
                int idx = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME);
                if (idx >= 0) return cursor.getString(idx);
            }
        } catch (Exception ignored) { }
        return uri.getLastPathSegment();
    }

    private long obtenerTamanoArchivo(Uri uri) {
        try (Cursor cursor = getContentResolver().query(uri, null, null, null, null)) {
            if (cursor != null && cursor.moveToFirst()) {
                int idx = cursor.getColumnIndex(OpenableColumns.SIZE);
                if (idx >= 0) return cursor.getLong(idx);
            }
        } catch (Exception ignored) { }
        return 0L;
    }

    private void guardarProducto() {
        if (tvGlobalError != null) tvGlobalError.setVisibility(View.GONE);

        String nombre = etName != null ? etName.getText().toString().trim() : "";
        String descripcion = etDescription != null ? etDescription.getText().toString().trim() : "";
        String precioStr = etPrice != null ? etPrice.getText().toString().trim() : "";
        String estado = (switchVisible == null || switchVisible.isChecked()) ? "Visible" : "Oculto";

        boolean valido = true;
        if (imagenSeleccionada.isEmpty()) {
            if (tvImageError != null) {
                tvImageError.setText("Dato inválido en Fotografía: debes agregar una imagen JPG o PNG (máx. 5 MB).");
                tvImageError.setVisibility(View.VISIBLE);
            }
            valido = false;
        } else {
            if (!imagenSeleccionada.startsWith("data:") && !imagenSeleccionada.startsWith("http")) {
                ResultadoValidacion rImg = ValidadorProducto.validarImagen(imagenSeleccionada, imagenTamanoBytes);
                if (!rImg.esValido()) {
                    if (tvImageError != null) {
                        tvImageError.setText("Dato inválido en Fotografía: debe ser JPG o PNG de máximo 5 MB.");
                        tvImageError.setVisibility(View.VISIBLE);
                    }
                    valido = false;
                }
            }
        }

        ResultadoValidacion rName = ValidadorProducto.validarNombre(nombre);
        if (!rName.esValido()) {
            if (tvNameError != null) {
                tvNameError.setText("Dato inválido en Nombre: debe tener entre 2 y 60 caracteres.");
                tvNameError.setVisibility(View.VISIBLE);
            }
            valido = false;
        }
        ResultadoValidacion rDesc = ValidadorProducto.validarDescripcion(descripcion);
        if (!rDesc.esValido()) {
            if (tvDescError != null) {
                tvDescError.setText("Dato inválido en Descripción: debe tener entre 1 y 300 caracteres.");
                tvDescError.setVisibility(View.VISIBLE);
            }
            valido = false;
        }
        ResultadoValidacion rPrice = ValidadorProducto.validarPrecio(precioStr);
        if (!rPrice.esValido()) {
            if (tvPriceError != null) {
                tvPriceError.setText("Dato inválido en Precio: debe estar entre $0.01 y $99,999.99 MXN con máximo 2 decimales.");
                tvPriceError.setVisibility(View.VISIBLE);
            }
            valido = false;
        }
        if (idCategoriaSeleccionada <= 0 && !listaCategorias.isEmpty()) {
            idCategoriaSeleccionada = listaCategorias.get(0).idCategoria;
            nombreCategoriaSeleccionada = listaCategorias.get(0).nombre;
        }
        if (idCategoriaSeleccionada <= 0) {
            if (tvCategoryError != null) {
                tvCategoryError.setText("Dato inválido en Categoría: selecciona una categoría válida.");
                tvCategoryError.setVisibility(View.VISIBLE);
            }
            valido = false;
        }
        if (!valido) return;

        double precio = Double.parseDouble(precioStr);
        int remoteCatId = catalogoRemoteId > 0 ? catalogoRemoteId : catalogoLocalId;
        CrearProductoRequest req = new CrearProductoRequest(
                remoteCatId, nombre, descripcion, precio, imagenSeleccionada, estado, idCategoriaSeleccionada
        );

        if (productoEditando != null) {
            if (productoEditando.remoteId > 0) {
                apiService.actualizarProducto(productoEditando.remoteId, req).enqueue(new Callback<ProductoDto>() {
                    @Override
                    public void onResponse(@NonNull Call<ProductoDto> call, @NonNull Response<ProductoDto> response) {
                        guardarEdicionLocal(nombre, descripcion, precio, nombreCategoriaSeleccionada, estado);
                    }

                    @Override
                    public void onFailure(@NonNull Call<ProductoDto> call, @NonNull Throwable t) {
                        guardarEdicionLocal(nombre, descripcion, precio, nombreCategoriaSeleccionada, estado);
                    }
                });
            } else {
                guardarEdicionLocal(nombre, descripcion, precio, nombreCategoriaSeleccionada, estado);
            }
        } else {
            if (catalogoRemoteId > 0) {
                apiService.crearProducto(remoteCatId, req).enqueue(new Callback<ProductoDto>() {
                    @Override
                    public void onResponse(@NonNull Call<ProductoDto> call, @NonNull Response<ProductoDto> response) {
                        if (response.isSuccessful() && response.body() != null) {
                            guardarNuevoLocal(nombre, descripcion, precio, nombreCategoriaSeleccionada, estado, response.body().idProducto);
                        } else if (response.code() == 404 || response.code() == 405) {
                            guardarNuevoLocal(nombre, descripcion, precio, nombreCategoriaSeleccionada, estado, 0);
                        } else {
                            String err = ApiErrorHelper.parseError(ProductoFormActivity.this, null, response);
                            if (tvGlobalError != null) {
                                tvGlobalError.setText(err);
                                tvGlobalError.setVisibility(View.VISIBLE);
                            }
                        }
                    }

                    @Override
                    public void onFailure(@NonNull Call<ProductoDto> call, @NonNull Throwable t) {
                        guardarNuevoLocal(nombre, descripcion, precio, nombreCategoriaSeleccionada, estado, 0);
                    }
                });
            } else {
                if (tvGlobalError != null) {
                    tvGlobalError.setText("Este catálogo es solo local. Crea o selecciona un catálogo sincronizado para publicar productos.");
                    tvGlobalError.setVisibility(View.VISIBLE);
                }
            }
        }
    }

    private void guardarNuevoLocal(String nombre, String descripcion, double precio,
                                   String categoria, String estado, int remoteId) {
        executor.execute(() -> {
            Producto p = new Producto(catalogoLocalId, vendedorId, nombre, descripcion, precio, categoria, estado);
            p.imagenUri = imagenSeleccionada;
            p.visible = "Visible".equalsIgnoreCase(estado);
            p.remoteId = remoteId;
            db.productoDao().insertar(p);
            runOnUiThread(() -> {
                Toast.makeText(this, "Producto guardado", Toast.LENGTH_SHORT).show();
                finish();
            });
        });
    }

    private void guardarEdicionLocal(String nombre, String descripcion, double precio,
                                     String categoria, String estado) {
        executor.execute(() -> {
            productoEditando.nombre = nombre;
            productoEditando.descripcion = descripcion;
            productoEditando.precioMxn = precio;
            productoEditando.categoria = categoria;
            productoEditando.imagenUri = imagenSeleccionada;
            productoEditando.estado = estado;
            productoEditando.visible = "Visible".equalsIgnoreCase(estado);
            productoEditando.fechaActualizacion = System.currentTimeMillis();
            if (productoEditando.id > 0) {
                db.productoDao().actualizar(productoEditando);
            } else if (catalogoLocalId > 0) {
                db.productoDao().insertar(productoEditando);
            }
            runOnUiThread(() -> {
                Toast.makeText(this, "Producto actualizado", Toast.LENGTH_SHORT).show();
                finish();
            });
        });
    }

    private void confirmarEliminar() {
        if (productoEditando == null) return;
        new AlertDialog.Builder(this)
                .setTitle("Eliminar producto")
                .setMessage("¿Eliminar \"" + productoEditando.nombre + "\"?")
                .setNegativeButton("Cancelar", null)
                .setPositiveButton("Eliminar", (d, w) -> {
                    if (productoEditando.remoteId > 0) {
                        apiService.eliminarProducto(productoEditando.remoteId).enqueue(new Callback<Void>() {
                            @Override public void onResponse(@NonNull Call<Void> call, @NonNull Response<Void> response) {
                                eliminarLocal();
                            }
                            @Override public void onFailure(@NonNull Call<Void> call, @NonNull Throwable t) {
                                eliminarLocal();
                            }
                        });
                    } else {
                        eliminarLocal();
                    }
                })
                .show();
    }


    private void eliminarLocal() {
        executor.execute(() -> {
            if (productoEditando.id > 0) {
                db.productoDao().eliminarPorId(productoEditando.id);
            }
            runOnUiThread(() -> {
                Toast.makeText(this, "Producto eliminado", Toast.LENGTH_SHORT).show();
                finish();
            });
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
