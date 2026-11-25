package com.tamalon.app.data.local;

import androidx.room.*;
import com.tamalon.app.model.ClientePerfil;

@Dao
public interface ClienteDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    void guardar(ClientePerfil perfil);

    @Query("SELECT * FROM cliente WHERE id_usuario = :idUsuario LIMIT 1")
    ClientePerfil obtenerPorIdUsuario(int idUsuario);

    @Query("UPDATE cliente SET `plan` = :plan WHERE id_usuario = :idUsuario")
    void actualizarPlan(int idUsuario, String plan);
}
