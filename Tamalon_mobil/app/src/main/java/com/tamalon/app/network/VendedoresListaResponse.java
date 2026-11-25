package com.tamalon.app.network;

import com.google.gson.annotations.SerializedName;
import java.util.List;

public class VendedoresListaResponse {
    @SerializedName("vendedores")
    public List<VendedorListaDto> vendedores;

    @SerializedName("pagina")
    public int pagina;

    @SerializedName("limite")
    public int limite;
}
