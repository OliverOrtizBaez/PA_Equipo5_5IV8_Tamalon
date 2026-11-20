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

    @ColumnInfo(name = "fecha_creacion")
    public long fechaCreacion;

    @ColumnInfo(name = "fecha_actualizacion")
    public long fechaActualizacion;

    public Catalogo() {}

    public Catalogo(int vendedorId, @NonNull String nombre) {
        this.vendedorId = vendedorId;
        this.nombre = nombre.trim();
        this.visible = true;
        long now = System.currentTimeMillis();
        this.fechaCreacion = now;
        this.fechaActualizacion = now;
    }
}
