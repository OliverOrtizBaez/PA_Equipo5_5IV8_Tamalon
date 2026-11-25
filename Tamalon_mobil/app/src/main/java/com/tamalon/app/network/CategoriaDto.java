package com.tamalon.app.network;

import com.google.gson.annotations.SerializedName;

public class CategoriaDto {
    @SerializedName("id_categoria")
    public int idCategoria;

    @SerializedName("nombre")
    public String nombre;
}
