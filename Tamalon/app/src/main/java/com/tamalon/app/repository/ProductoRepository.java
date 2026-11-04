package com.tamalon.app.repository;

import android.content.Context;
import androidx.lifecycle.LiveData;
import com.tamalon.app.data.local.AppDatabase;
import com.tamalon.app.model.Producto;
import com.tamalon.app.validation.ResultadoValidacion;
import com.tamalon.app.validation.ValidadorProducto;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class ProductoRepository {

    private final AppDatabase db;
    private final ExecutorService executor;

    public interface Callback {
        void onExito(long nuevoId);
        void onError(String mensajeResId);
    }

    public ProductoRepository(Context context) {
        this.db       = AppDatabase.getInstance(context);
        this.executor = Executors.newSingleThreadExecutor();
    }

    public void crearProducto(int catalogoId, int vendedorId,
                              String nombre, String descripcion,
                              String precioTexto, String categoria,
                              String imagenUri, long imagenTamano,
                              boolean esPremium, Callback callback) {
        // Validaciones síncronas
        ResultadoValidacion rv;
        rv = ValidadorProducto.validarNombre(nombre);
        if (!rv.esValido()) { callback.onError(rv.getMensajeErrorResId()); return; }
        rv = ValidadorProducto.validarDescripcion(descripcion);
        if (!rv.esValido()) { callback.onError(rv.getMensajeErrorResId()); return; }
        rv = ValidadorProducto.validarPrecio(precioTexto);
        if (!rv.esValido()) { callback.onError(rv.getMensajeErrorResId()); return; }
        rv = ValidadorProducto.validarCategoria(categoria);
        if (!rv.esValido()) { callback.onError(rv.getMensajeErrorResId()); return; }
        if (imagenUri != null && !imagenUri.isEmpty()) {
            rv = ValidadorProducto.validarImagen(imagenUri, imagenTamano);
            if (!rv.esValido()) { callback.onError(rv.getMensajeErrorResId()); return; }
        }

        double precio = Double.parseDouble(precioTexto.trim());
        executor.execute(() -> {
            int actuales = db.productoDao().contarVisiblesPorCatalogo(catalogoId);
            ResultadoValidacion limite = ValidadorProducto.validarLimite(actuales, esPremium);
            if (!limite.esValido()) { callback.onError(limite.getMensajeErrorResId()); return; }
            try {
                Producto p = new Producto(catalogoId, vendedorId, nombre, descripcion, precio, categoria);
                p.imagenUri = imagenUri;
                long id = db.productoDao().insertar(p);
                callback.onExito(id);
            } catch (Exception e) {
                callback.onError("validation_storage_error");
            }
        });
    }

    public void eliminarProducto(Producto producto, Runnable onExito) {
        executor.execute(() -> {
            db.productoDao().eliminar(producto);
            onExito.run();
        });
    }

    public LiveData<List<Producto>> obtenerProductosPorCatalogo(int catalogoId) {
        return db.productoDao().obtenerPorCatalogo(catalogoId);
    }
}
