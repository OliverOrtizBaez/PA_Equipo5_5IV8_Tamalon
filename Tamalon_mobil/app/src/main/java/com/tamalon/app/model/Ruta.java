package com.tamalon.app.model;

import androidx.annotation.NonNull;
import androidx.room.ColumnInfo;
import androidx.room.Entity;
import androidx.room.PrimaryKey;

@Entity(tableName = "rutas")
public class Ruta {

    @PrimaryKey(autoGenerate = true)
    public int id;

    @ColumnInfo(name = "vendedor_id")
    public int vendedorId;

    @NonNull
    @ColumnInfo(name = "nombre")
    public String nombre;

    @NonNull
    @ColumnInfo(name = "dia_semana")
    public String diaSemana;

    @ColumnInfo(name = "horario")
    public String horario;

    @ColumnInfo(name = "puntos_json")
    public String puntosJson;

    @ColumnInfo(name = "estado")
    public String estado;

    @ColumnInfo(name = "remote_id")
    public int remoteId;

    @ColumnInfo(name = "fecha_creacion")
    public long fechaCreacion;

    public Ruta() {}

    public Ruta(int vendedorId, @NonNull String nombre, @NonNull String diaSemana,
                String horario, String puntosJson) {
        this.vendedorId = vendedorId;
        this.nombre = nombre.trim();
        this.diaSemana = diaSemana.trim();
        this.horario = horario == null ? "" : horario.trim();
        this.puntosJson = puntosJson == null ? "[]" : puntosJson;
        this.estado = "Activa";
        this.remoteId = 0;
        this.fechaCreacion = System.currentTimeMillis();
    }
}
