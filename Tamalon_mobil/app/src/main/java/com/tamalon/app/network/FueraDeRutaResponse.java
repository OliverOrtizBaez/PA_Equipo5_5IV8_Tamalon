package com.tamalon.app.network;

import com.google.gson.annotations.SerializedName;

public class FueraDeRutaResponse {
    @SerializedName("mensaje")
    public String mensaje;

    @SerializedName("disponibilidad")
    public String disponibilidad;
}
