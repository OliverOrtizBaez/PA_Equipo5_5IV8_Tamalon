package com.tamalon.app.model;

import androidx.room.Entity;
import androidx.room.PrimaryKey;
import androidx.room.ColumnInfo;
import androidx.annotation.NonNull;

/**
 * RF23.1 Vendedor reporta a Cliente.
 * RF23.2 Cliente reporta a Vendedor.
 * El emisor y el reportado se identifican por su ID local; los IDs de Supabase
 * se poblarán cuando se implemente la sincronización con el backend.
 */
@Entity(tableName = "reportes")
public class Reporte {

    @PrimaryKey(autoGenerate = true)
    public int id;

    @ColumnInfo(name = "emisor_id")
    public int emisorId;           // ID local del usuario que envía el reporte

    @ColumnInfo(name = "emisor_rol")
    public String emisorRol;       // "cliente" | "vendedor"

    @ColumnInfo(name = "reportado_id")
    public int reportadoId;        // ID local del usuario reportado

    @ColumnInfo(name = "reportado_rol")
    public String reportadoRol;    // "cliente" | "vendedor"

    @NonNull
    @ColumnInfo(name = "motivo")
    public String motivo;          // 1–500 caracteres

    @ColumnInfo(name = "fecha_creacion")
    public long fechaCreacion;

    // Integración futura: se enviará al backend y se registrará aquí el ID remoto
    @ColumnInfo(name = "supabase_id")
    public String supabaseId;

    @ColumnInfo(name = "sincronizado")
    public boolean sincronizado;

    public Reporte() {}

    public Reporte(int emisorId, String emisorRol,
                   int reportadoId, String reportadoRol,
                   @NonNull String motivo) {
        this.emisorId = emisorId;
        this.emisorRol = emisorRol;
        this.reportadoId = reportadoId;
        this.reportadoRol = reportadoRol;
        this.motivo = motivo.trim();
        this.fechaCreacion = System.currentTimeMillis();
        this.sincronizado = false;
    }
}
