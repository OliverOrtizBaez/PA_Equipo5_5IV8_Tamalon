package com.tamalon.app.model;

import androidx.room.Entity;
import androidx.room.PrimaryKey;
import androidx.room.ColumnInfo;
import androidx.annotation.NonNull;

/**
 * Solicitud general de ayuda, queja, petición o sugerencia enviada al equipo Tamalon.
 * Distinta de los reportes entre usuarios (RF23.1/RF23.2).
 */
@Entity(tableName = "solicitudes_soporte")
public class SolicitudSoporte {

    public static final String ESTADO_PENDIENTE   = "pendiente";
    public static final String ESTADO_EN_REVISION = "en_revision";
    public static final String ESTADO_ATENDIDA    = "atendida";

    public static final String CAT_QUEJA          = "queja";
    public static final String CAT_PETICION       = "peticion";
    public static final String CAT_SUGERENCIA     = "sugerencia";
    public static final String CAT_TECNICO        = "tecnico";
    public static final String CAT_OTRO           = "otro";

    @PrimaryKey(autoGenerate = true)
    public int id;

    @ColumnInfo(name = "emisor_id")
    public int emisorId;

    @ColumnInfo(name = "emisor_rol")
    public String emisorRol;       // "cliente" | "vendedor"

    @NonNull
    @ColumnInfo(name = "categoria")
    public String categoria;       // CAT_* constantes de arriba

    @NonNull
    @ColumnInfo(name = "asunto")
    public String asunto;          // 1–120 caracteres

    @NonNull
    @ColumnInfo(name = "descripcion")
    public String descripcion;     // 1–1000 caracteres

    @NonNull
    @ColumnInfo(name = "estado")
    public String estado;          // ESTADO_* constantes de arriba

    @ColumnInfo(name = "fecha_creacion")
    public long fechaCreacion;

    @ColumnInfo(name = "sincronizado")
    public boolean sincronizado;

    public SolicitudSoporte() {}

    public SolicitudSoporte(int emisorId, String emisorRol,
                            @NonNull String categoria,
                            @NonNull String asunto,
                            @NonNull String descripcion) {
        this.emisorId = emisorId;
        this.emisorRol = emisorRol;
        this.categoria = categoria;
        this.asunto = asunto.trim();
        this.descripcion = descripcion.trim();
        this.estado = ESTADO_PENDIENTE;
        this.fechaCreacion = System.currentTimeMillis();
        this.sincronizado = false;
    }
}
