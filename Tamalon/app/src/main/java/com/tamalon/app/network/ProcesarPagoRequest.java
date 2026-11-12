package com.tamalon.app.network;

import com.google.gson.annotations.SerializedName;

public class ProcesarPagoRequest {
    @SerializedName("resultado")
    public String resultado;

    public ProcesarPagoRequest(String resultado) {
        this.resultado = resultado;
    }
}
