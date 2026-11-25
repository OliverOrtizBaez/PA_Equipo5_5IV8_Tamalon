package com.tamalon.app.network;

import com.google.gson.annotations.SerializedName;

public class TipoVendedorDto {
    @SerializedName("id_tipo_vendedor")
    public int idTipoVendedor;

    @SerializedName("nombre")
    public String nombre;

    @SerializedName("clave")
    public String clave;

    @SerializedName("icono_vendedor")
    public String iconoVendedor;
}
