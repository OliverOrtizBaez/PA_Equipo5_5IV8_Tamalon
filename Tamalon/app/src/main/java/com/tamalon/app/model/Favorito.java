package com.tamalon.app.model;

import androidx.room.Entity;
import androidx.room.PrimaryKey;
import androidx.room.ColumnInfo;

/** Vendedor guardado como favorito por un Cliente Premium. Máximo 5. */
@Entity(tableName = "favoritos",
        indices = {@androidx.room.Index(value = {"cliente_id", "vendedor_id"}, unique = true)})
public class Favorito {

    @PrimaryKey(autoGenerate = true)
    public int id;

    @ColumnInfo(name = "cliente_id")
    public int clienteId;

    @ColumnInfo(name = "vendedor_id")
    public int vendedorId;

    @ColumnInfo(name = "fecha_guardado")
    public long fechaGuardado;

    public Favorito() {}

    public Favorito(int clienteId, int vendedorId) {
        this.clienteId = clienteId;
        this.vendedorId = vendedorId;
        this.fechaGuardado = System.currentTimeMillis();
    }
}
