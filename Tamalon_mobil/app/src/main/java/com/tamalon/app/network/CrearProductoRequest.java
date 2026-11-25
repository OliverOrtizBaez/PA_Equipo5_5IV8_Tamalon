package com.tamalon.app.network;

import com.google.gson.annotations.SerializedName;

public class CrearProductoRequest {
    @SerializedName("id_catalogo")
    public int idCatalogo;

    @SerializedName("nombre")
    public String nombre;

    @SerializedName("descripcion")
    public String descripcion;

    @SerializedName("precio")
    public double precio;

    @SerializedName("imagen")
    public String imagen;

    @SerializedName("estado")
    public String estado;

    @SerializedName("id_categoria")
    public int idCategoria;

    public CrearProductoRequest(int idCatalogo, String nombre, String descripcion,
                                double precio, String imagen, String estado, int idCategoria) {
        this.idCatalogo = idCatalogo;
        this.nombre = nombre;
        this.descripcion = descripcion;
        this.precio = precio;
        this.imagen = imagen;
        this.estado = estado;
        this.idCategoria = idCategoria;
    }
}
