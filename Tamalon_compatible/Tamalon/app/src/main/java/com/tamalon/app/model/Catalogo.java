package com.tamalon.app.model;

import androidx.room.Entity;
import androidx.room.PrimaryKey;
import androidx.room.ColumnInfo;
import androidx.annotation.NonNull;

@Entity(tableName = "catalogos")
public class Catalogo {

    @PrimaryKey(autoGenerate = true)
    public int id;

    @ColumnInfo(name = "vendedor_id")
    public int vendedorId;         // FK → usuarios.id

    @NonNull
    @ColumnInfo(name = "nombre")
    public String nombre;          // 1–50 caracteres

    @ColumnInfo(name = "visible")
    public boolean visible;        // Gratuito: 1 visible; Premium: hasta 3

    @ColumnInfo(name = "estado")
    public String estado;          // Activo / Inactivo (según tamalon_db.sql)

    @ColumnInfo(name = "remote_id")
    public int remoteId;           // id_catalogo del backend cuando está sincronizado

    @ColumnInfo(name = "fecha_creacion")
    public long fechaCreacion;

    @ColumnInfo(name = "fecha_actualizacion")
    public long fechaActualizacion;

    public Catalogo() {}

    public Catalogo(int vendedorId, @NonNull String nombre) {
        this(vendedorId, nombre, "Activo");
    }

    public Catalogo(int vendedorId, @NonNull String nombre, String estado) {
        this.vendedorId = vendedorId;
        this.nombre = nombre.trim();
        this.estado = (estado == null || estado.trim().isEmpty()) ? "Activo" : estado.trim();
        this.visible = !"Inactivo".equalsIgnoreCase(this.estado);
        this.remoteId = 0;
        long now = System.currentTimeMillis();
        this.fechaCreacion = now;
        this.fechaActualizacion = now;
    }

    public String getEstadoEfectivo() {
        if (estado != null && !estado.trim().isEmpty()) return estado.trim();
        return visible ? "Activo" : "Inactivo";
    }
}
