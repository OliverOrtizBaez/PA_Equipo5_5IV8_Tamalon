package com.tamalon.app.data.local;

import androidx.lifecycle.LiveData;
import androidx.room.*;
import com.tamalon.app.model.Producto;
import java.util.List;

@Dao
public interface ProductoDao {

    @Insert(onConflict = OnConflictStrategy.ABORT)
    long insertar(Producto producto);

    @Update
    void actualizar(Producto producto);

    @Delete
    void eliminar(Producto producto);

    @Query("SELECT * FROM productos WHERE id = :id LIMIT 1")
    Producto obtenerPorId(int id);

    @Query("SELECT * FROM productos WHERE catalogo_id = :catalogoId AND visible = 1 ORDER BY fecha_creacion DESC")
    LiveData<List<Producto>> obtenerPorCatalogo(int catalogoId);

    @Query("SELECT COUNT(*) FROM productos WHERE catalogo_id = :catalogoId AND visible = 1")
    int contarVisiblesPorCatalogo(int catalogoId);

    @Query("SELECT COUNT(*) FROM productos WHERE vendedor_id = :vendedorId AND visible = 1 AND lower(categoria) LIKE '%' || lower(:categoria) || '%'")
    int contarVisiblesPorVendedorYCategoria(int vendedorId, String categoria);

    @Query("DELETE FROM productos WHERE catalogo_id = :catalogoId")
    void eliminarPorCatalogo(int catalogoId);
}
