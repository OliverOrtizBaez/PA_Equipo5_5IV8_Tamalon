package com.tamalon.app.network;

import com.google.gson.annotations.SerializedName;

public class AgregarFavoritoRequest {
    @SerializedName("id_vendedor")
    public int idVendedor;

    public AgregarFavoritoRequest(int idVendedor) {
        this.idVendedor = idVendedor;
    }
}
