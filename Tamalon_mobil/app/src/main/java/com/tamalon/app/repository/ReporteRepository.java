package com.tamalon.app.repository;

import android.content.Context;
import androidx.lifecycle.LiveData;
import com.tamalon.app.data.local.AppDatabase;
import com.tamalon.app.model.Reporte;
import com.tamalon.app.validation.ResultadoValidacion;
import com.tamalon.app.validation.ValidadorReporte;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * RF23.1 / RF23.2 — Repositorio de reportes entre usuarios.
 * Guarda reportes localmente y los marca para sincronización futura con Supabase.
 */
public class ReporteRepository {

    private final AppDatabase db;
    private final ExecutorService executor;

    public interface Callback {
        void onExito();
        void onError(String mensajeResId);
    }

    public ReporteRepository(Context context) {
        this.db       = AppDatabase.getInstance(context);
        this.executor = Executors.newSingleThreadExecutor();
    }

    public void enviarReporte(int emisorId, String emisorRol,
                              int reportadoId, String reportadoRol,
                              String motivo, Callback callback) {
        ResultadoValidacion rv;
        rv = ValidadorReporte.validarMotivo(motivo);
        if (!rv.esValido()) { callback.onError(rv.getMensajeErrorResId()); return; }
        rv = ValidadorReporte.validarParticipantes(emisorId, reportadoId);
        if (!rv.esValido()) { callback.onError(rv.getMensajeErrorResId()); return; }
        rv = ValidadorReporte.validarRoles(emisorRol, reportadoRol);
        if (!rv.esValido()) { callback.onError(rv.getMensajeErrorResId()); return; }
        Reporte r = new Reporte(emisorId, emisorRol, reportadoId, reportadoRol, motivo);
        executor.execute(() -> {
            try {
                db.reporteDao().insertar(r);
                // Integración futura: llamar a SupabaseReporteService.enviar(r)
                callback.onExito();
            } catch (Exception e) {
                callback.onError("validation_storage_error");
            }
        });
    }

    public LiveData<List<Reporte>> obtenerReportesEmisor(int emisorId) {
        return db.reporteDao().obtenerPorEmisor(emisorId);
    }
}
