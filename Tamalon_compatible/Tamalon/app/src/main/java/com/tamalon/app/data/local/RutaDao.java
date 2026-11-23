package com.tamalon.app.data.local;

import androidx.room.Dao;
import androidx.room.Delete;
import androidx.room.Insert;
import androidx.room.OnConflictStrategy;
import androidx.room.Query;
import androidx.room.Update;
import com.tamalon.app.model.Ruta;
import java.util.List;

@Dao
public interface RutaDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    long insertar(Ruta ruta);

    @Update
    void actualizar(Ruta ruta);

    @Delete
    void eliminar(Ruta ruta);

    @Query("SELECT * FROM rutas WHERE id = :id LIMIT 1")
    Ruta obtenerPorId(int id);

    @Query("SELECT * FROM rutas WHERE vendedor_id = :vendedorId ORDER BY fecha_creacion DESC")
    List<Ruta> obtenerPorVendedor(int vendedorId);

    @Query("SELECT * FROM rutas WHERE vendedor_id = :vendedorId AND lower(dia_semana) = lower(:dia) LIMIT 1")
    Ruta obtenerPorVendedorYDia(int vendedorId, String dia);

    @Query("SELECT * FROM rutas WHERE vendedor_id = :vendedorId AND remote_id = :remoteId LIMIT 1")
    Ruta obtenerPorRemoteId(int vendedorId, int remoteId);

    @Query("SELECT COUNT(*) FROM rutas WHERE vendedor_id = :vendedorId")
    int contarPorVendedor(int vendedorId);

    @Query("DELETE FROM rutas WHERE id = :id")
    void eliminarPorId(int id);
}
