package com.tamalon.app.network;

import com.google.gson.annotations.SerializedName;

public class JornadaResumenResponse {
    @SerializedName("minutos")
    public int minutos;

    @SerializedName("kilometros")
    public double kilometros;

    @SerializedName("mensaje")
    public String mensaje;
}
