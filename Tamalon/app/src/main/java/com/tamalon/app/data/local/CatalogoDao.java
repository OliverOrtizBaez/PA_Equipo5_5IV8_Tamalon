package com.tamalon.app.data.local;

import androidx.lifecycle.LiveData;
import androidx.room.*;
import com.tamalon.app.model.Catalogo;
import java.util.List;

@Dao
public interface CatalogoDao {

    @Insert(onConflict = OnConflictStrategy.ABORT)
    long insertar(Catalogo catalogo);

    @Update
    void actualizar(Catalogo catalogo);

    @Delete
    void eliminar(Catalogo catalogo);

    @Query("SELECT * FROM catalogos WHERE id = :id LIMIT 1")
    Catalogo obtenerPorId(int id);

    @Query("SELECT * FROM catalogos WHERE vendedor_id = :vendedorId ORDER BY fecha_creacion DESC")
    LiveData<List<Catalogo>> obtenerPorVendedor(int vendedorId);

    @Query("SELECT COUNT(*) FROM catalogos WHERE vendedor_id = :vendedorId AND visible = 1")
    int contarVisiblesPorVendedor(int vendedorId);

    @Query("SELECT * FROM catalogos WHERE vendedor_id = :vendedorId AND visible = 1 ORDER BY fecha_creacion DESC")
    List<Catalogo> obtenerVisiblesPorVendedor(int vendedorId);
}
