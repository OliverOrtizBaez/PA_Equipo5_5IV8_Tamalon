package com.tamalon.app.data.local;

import androidx.lifecycle.LiveData;
import androidx.room.*;
import com.tamalon.app.model.Favorito;
import java.util.List;

@Dao
public interface FavoritoDao {

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    long insertar(Favorito favorito);

    @Delete
    void eliminar(Favorito favorito);

    @Query("SELECT * FROM favoritos WHERE cliente_id = :clienteId ORDER BY fecha_guardado DESC")
    LiveData<List<Favorito>> obtenerPorCliente(int clienteId);

    @Query("SELECT COUNT(*) FROM favoritos WHERE cliente_id = :clienteId")
    int contarPorCliente(int clienteId);

    @Query("SELECT COUNT(*) FROM favoritos WHERE cliente_id = :clienteId AND vendedor_id = :vendedorId")
    int esFavorito(int clienteId, int vendedorId);

    @Query("DELETE FROM favoritos WHERE cliente_id = :clienteId AND vendedor_id = :vendedorId")
    void eliminarPorIds(int clienteId, int vendedorId);
}
