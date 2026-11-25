package com.tamalon.app.network;

import com.google.gson.annotations.SerializedName;

public class EventoRequest {
    @SerializedName("tipo")
    public String tipo;

    @SerializedName("id_referencia")
    public int idReferencia;

    public EventoRequest(String tipo, int idReferencia) {
        this.tipo = tipo;
        this.idReferencia = idReferencia;
    }
}
