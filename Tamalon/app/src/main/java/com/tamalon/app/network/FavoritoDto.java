package com.tamalon.app.network;

import com.google.gson.annotations.SerializedName;

public class FavoritoDto {
    @SerializedName("id_favorito")
    public int idFavorito;

    @SerializedName("id_cliente")
    public int idCliente;

    @SerializedName("id_vendedor")
    public int idVendedor;

    @SerializedName("nombre_vendedor")
    public String nombreVendedor;

    @SerializedName("vendedor")
    public DtoUser vendedor;
}
