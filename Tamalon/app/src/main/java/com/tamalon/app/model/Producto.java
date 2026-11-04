package com.tamalon.app.model;

import androidx.room.Entity;
import androidx.room.PrimaryKey;
import androidx.room.ColumnInfo;
import androidx.annotation.NonNull;

@Entity(tableName = "productos")
public class Producto {

    @PrimaryKey(autoGenerate = true)
    public int id;

    @ColumnInfo(name = "catalogo_id")
    public int catalogoId;         // FK → catalogos.id

    @ColumnInfo(name = "vendedor_id")
    public int vendedorId;         // FK → usuarios.id (desnormalizado para consultas rápidas)

    @NonNull
    @ColumnInfo(name = "nombre")
    public String nombre;          // 2–60 caracteres

    @NonNull
    @ColumnInfo(name = "descripcion")
    public String descripcion;     // 1–300 caracteres

    @ColumnInfo(name = "precio_mxn")
    public double precioMxn;       // 0.01–99999.99, máx 2 decimales

    @ColumnInfo(name = "categoria")
    public String categoria;       // valor de la lista definida por el proyecto

    @ColumnInfo(name = "imagen_uri")
    public String imagenUri;       // URI local; JPG o PNG ≤ 5 MB

    @ColumnInfo(name = "visible")
    public boolean visible;

    @ColumnInfo(name = "fecha_creacion")
    public long fechaCreacion;

    @ColumnInfo(name = "fecha_actualizacion")
    public long fechaActualizacion;

    public Producto() {}

    public Producto(int catalogoId, int vendedorId,
                    @NonNull String nombre, @NonNull String descripcion,
                    double precioMxn, String categoria) {
        this.catalogoId = catalogoId;
        this.vendedorId = vendedorId;
        this.nombre = nombre.trim();
        this.descripcion = descripcion.trim();
        this.precioMxn = precioMxn;
        this.categoria = categoria;
        this.visible = true;
        long now = System.currentTimeMillis();
        this.fechaCreacion = now;
        this.fechaActualizacion = now;
    }
}
