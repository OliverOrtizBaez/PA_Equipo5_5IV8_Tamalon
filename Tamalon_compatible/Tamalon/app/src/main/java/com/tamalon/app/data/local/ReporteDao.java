package com.tamalon.app.data.local;

import androidx.lifecycle.LiveData;
import androidx.room.*;
import com.tamalon.app.model.Reporte;
import java.util.List;

@Dao
public interface ReporteDao {

    @Insert(onConflict = OnConflictStrategy.ABORT)
    long insertar(Reporte reporte);

    @Query("SELECT * FROM reportes WHERE emisor_id = :emisorId ORDER BY fecha_creacion DESC")
    LiveData<List<Reporte>> obtenerPorEmisor(int emisorId);

    @Query("SELECT * FROM reportes WHERE sincronizado = 0")
    List<Reporte> obtenerPendientesSincronizacion();

    @Query("UPDATE reportes SET sincronizado = 1, supabase_id = :supabaseId WHERE id = :id")
    void marcarSincronizado(int id, String supabaseId);
}
