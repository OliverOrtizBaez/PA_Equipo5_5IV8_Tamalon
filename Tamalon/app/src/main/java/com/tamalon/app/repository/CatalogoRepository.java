package com.tamalon.app.repository;

import android.content.Context;
import androidx.lifecycle.LiveData;
import com.tamalon.app.data.local.AppDatabase;
import com.tamalon.app.model.Catalogo;
import com.tamalon.app.validation.ResultadoValidacion;
import com.tamalon.app.validation.ValidadorCatalogo;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class CatalogoRepository {

    private final AppDatabase db;
    private final ExecutorService executor;

    public interface Callback {
        void onExito(long nuevoId);
        void onError(String mensajeResId);
    }

    public CatalogoRepository(Context context) {
        this.db       = AppDatabase.getInstance(context);
        this.executor = Executors.newSingleThreadExecutor();
    }

    public void crearCatalogo(int vendedorId, String nombre, boolean esPremium, Callback callback) {
        ResultadoValidacion rv = ValidadorCatalogo.validarNombre(nombre);
        if (!rv.esValido()) { callback.onError(rv.getMensajeErrorResId()); return; }

        executor.execute(() -> {
            int actuales = db.catalogoDao().contarVisiblesPorVendedor(vendedorId);
            ResultadoValidacion limite = ValidadorCatalogo.validarLimite(actuales, esPremium);
            if (!limite.esValido()) { callback.onError(limite.getMensajeErrorResId()); return; }
            try {
                long id = db.catalogoDao().insertar(new Catalogo(vendedorId, nombre));
                callback.onExito(id);
            } catch (Exception e) {
                callback.onError("validation_storage_error");
            }
        });
    }

    public void actualizarNombre(Catalogo catalogo, String nuevoNombre, Runnable onExito, Runnable onError) {
        ResultadoValidacion rv = ValidadorCatalogo.validarNombre(nuevoNombre);
        if (!rv.esValido()) { onError.run(); return; }
        catalogo.nombre = nuevoNombre.trim();
        catalogo.fechaActualizacion = System.currentTimeMillis();
        executor.execute(() -> {
            db.catalogoDao().actualizar(catalogo);
            onExito.run();
        });
    }

    public void eliminarCatalogo(Catalogo catalogo, Runnable onExito) {
        executor.execute(() -> {
            db.productoDao().eliminarPorCatalogo(catalogo.id);
            db.catalogoDao().eliminar(catalogo);
            onExito.run();
        });
    }

    public LiveData<List<Catalogo>> obtenerCatalogosPorVendedor(int vendedorId) {
        return db.catalogoDao().obtenerPorVendedor(vendedorId);
    }
}
