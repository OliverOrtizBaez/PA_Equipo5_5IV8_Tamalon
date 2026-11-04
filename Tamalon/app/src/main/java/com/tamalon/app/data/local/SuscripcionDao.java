package com.tamalon.app.data.local;

import androidx.lifecycle.LiveData;
import androidx.room.*;
import com.tamalon.app.model.Suscripcion;

@Dao
public interface SuscripcionDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    long insertar(Suscripcion suscripcion);

    @Update
    void actualizar(Suscripcion suscripcion);

    @Query("SELECT * FROM suscripciones WHERE usuario_id = :usuarioId LIMIT 1")
    Suscripcion obtenerPorUsuario(int usuarioId);

    @Query("SELECT * FROM suscripciones WHERE usuario_id = :usuarioId LIMIT 1")
    LiveData<Suscripcion> observarPorUsuario(int usuarioId);

    @Query("UPDATE suscripciones SET plan = :plan, estado_operacion = :estado, fecha_actualizacion = :ts WHERE usuario_id = :usuarioId")
    void actualizarPlan(int usuarioId, String plan, String estado, long ts);
}
