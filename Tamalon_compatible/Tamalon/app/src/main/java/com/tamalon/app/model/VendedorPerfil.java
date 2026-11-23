package com.tamalon.app.model;

import androidx.room.ColumnInfo;
import androidx.room.Entity;
import androidx.room.ForeignKey;
import androidx.room.Index;
import androidx.room.PrimaryKey;

@Entity(
    tableName = "vendedor",
    foreignKeys = @ForeignKey(
        entity = Usuario.class,
        parentColumns = "id",
        childColumns = "id_usuario",
        onDelete = ForeignKey.CASCADE
    ),
    indices = {@Index("id_usuario")}
)
public class VendedorPerfil {

    @PrimaryKey
    @ColumnInfo(name = "id_usuario")
    public int idUsuario;

    @ColumnInfo(name = "plan")
    public String plan;

    @ColumnInfo(name = "ubicacion_latitud")
    public Double ubicacionLatitud;

    @ColumnInfo(name = "ubicacion_longitud")
    public Double ubicacionLongitud;

    @ColumnInfo(name = "disponible_en_ruta", defaultValue = "0")
    public boolean disponibleEnRuta;

    @ColumnInfo(name = "ultima_ubicacion", defaultValue = "0")
    public long ultimaUbicacion;

    public VendedorPerfil() {}

    public VendedorPerfil(int idUsuario, String plan) {
        this.idUsuario = idUsuario;
        this.plan = plan != null ? plan : "gratuito";
    }
}
