package com.tamalon.app.network;

import com.google.gson.annotations.SerializedName;
import java.util.List;

public class CercanosResponse {
    @SerializedName("vendedores")
    public List<VendedorCercanoDto> vendedores;
}
