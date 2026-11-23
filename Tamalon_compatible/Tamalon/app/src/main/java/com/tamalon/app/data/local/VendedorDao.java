package com.tamalon.app.data.local;

import androidx.room.*;
import com.tamalon.app.model.VendedorPerfil;

@Dao
public interface VendedorDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    void guardar(VendedorPerfil perfil);

    @Query("SELECT * FROM vendedor WHERE id_usuario = :idUsuario LIMIT 1")
    VendedorPerfil obtenerPorIdUsuario(int idUsuario);

    @Query("UPDATE vendedor SET `plan` = :plan WHERE id_usuario = :idUsuario")
    void actualizarPlan(int idUsuario, String plan);

    @Query("UPDATE vendedor SET ubicacion_latitud = :latitud, ubicacion_longitud = :longitud, ultima_ubicacion = :timestamp WHERE id_usuario = :id")
    void actualizarUbicacionVendedor(int id, double latitud, double longitud, long timestamp);

    @Query("UPDATE vendedor SET disponible_en_ruta = :disponible, ultima_ubicacion = :timestamp WHERE id_usuario = :id")
    void actualizarDisponibilidadVendedor(int id, boolean disponible, long timestamp);

    @Query("UPDATE vendedor SET disponible_en_ruta = 0 WHERE disponible_en_ruta = 1 AND ultima_ubicacion < :limiteTimestamp")
    void desactivarVendedoresSinSenal(long limiteTimestamp);
}
