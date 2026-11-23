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

    @SerializedName("imagen")
    public String imagen;

    @SerializedName("foto_url")
    public String fotoUrl;

    @SerializedName("estado")
    public String estado;

    @SerializedName("visible")
    public boolean visible = true;

    @SerializedName("fech_creacion")
    public String fechCreacion;

    public String getImagenEfectiva() {
        if (imagen != null && !imagen.trim().isEmpty()) return imagen.trim();
        return fotoUrl != null ? fotoUrl.trim() : "";
    }

    public String getEstadoEfectivo() {
        if (estado != null && !estado.trim().isEmpty()) return estado.trim();
        return visible ? "Activo" : "Inactivo";
    }
}
