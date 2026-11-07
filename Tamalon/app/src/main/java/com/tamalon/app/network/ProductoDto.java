package com.tamalon.app.network;

import com.google.gson.annotations.SerializedName;

public class ProductoDto {
    @SerializedName("id_producto")
    public int idProducto;

    @SerializedName("id_catalogo")
    public int idCatalogo;

    @SerializedName("nombre")
    public String nombre;

    @SerializedName("descripcion")
    public String descripcion;

    @SerializedName("precio")
    public double precio;

    @SerializedName("categoria")
    public String categoria;

    @SerializedName("foto_url")
    public String fotoUrl;

    @SerializedName("visible")
    public boolean visible = true;
}
