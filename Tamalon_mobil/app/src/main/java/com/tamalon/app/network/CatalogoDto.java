package com.tamalon.app.network;

import com.google.gson.annotations.SerializedName;

public class CatalogoDto {
    @SerializedName("id_catalogo")
    public int idCatalogo;

    @SerializedName("id_vendedor")
    public int idVendedor;

    @SerializedName("id_usuario")
    public int idUsuario;

    @SerializedName("nombre")
    public String nombre;

    @SerializedName("estado")
    public String estado;

    @SerializedName("visible")
    public boolean visible = true;

    @SerializedName("total_productos")
    public int totalProductos;

    @SerializedName("fech_creacion")
    public String fechCreacion;

    public int getIdVendedorEfectivo() {
        return idVendedor > 0 ? idVendedor : idUsuario;
    }
}
