package com.tamalon.app.network;

import com.google.gson.annotations.SerializedName;

public class CatalogoDto {
    @SerializedName("id_catalogo")
    public int idCatalogo;

    @SerializedName("id_usuario")
    public int idUsuario;

    @SerializedName("nombre")
    public String nombre;

    @SerializedName("visible")
    public boolean visible = true;
}
