package com.tamalon.app.model;

import androidx.room.ColumnInfo;
import androidx.room.Entity;
import androidx.room.ForeignKey;
import androidx.room.Index;
import androidx.room.PrimaryKey;

@Entity(
    tableName = "cliente",
    foreignKeys = @ForeignKey(
        entity = Usuario.class,
        parentColumns = "id",
        childColumns = "id_usuario",
        onDelete = ForeignKey.CASCADE
    ),
    indices = {@Index("id_usuario")}
)
public class ClientePerfil {

    @PrimaryKey
    @ColumnInfo(name = "id_usuario")
    public int idUsuario;

    @ColumnInfo(name = "plan")
    public String plan;

    public ClientePerfil() {}

    public ClientePerfil(int idUsuario, String plan) {
        this.idUsuario = idUsuario;
        this.plan = plan != null ? plan : "gratuito";
    }
}
