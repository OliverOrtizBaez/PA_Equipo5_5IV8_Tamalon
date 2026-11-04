package com.tamalon.app.data.local;

import androidx.lifecycle.LiveData;
import androidx.room.*;
import com.tamalon.app.model.SolicitudSoporte;
import java.util.List;

@Dao
public interface SolicitudSoporteDao {

    @Insert(onConflict = OnConflictStrategy.ABORT)
    long insertar(SolicitudSoporte solicitud);

    @Update
    void actualizar(SolicitudSoporte solicitud);

    @Query("SELECT * FROM solicitudes_soporte WHERE emisor_id = :emisorId ORDER BY fecha_creacion DESC")
    LiveData<List<SolicitudSoporte>> obtenerPorEmisor(int emisorId);

    @Query("SELECT * FROM solicitudes_soporte WHERE sincronizado = 0")
    List<SolicitudSoporte> obtenerPendientesSincronizacion();
}
