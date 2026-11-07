package com.tamalon.app.network;

import com.google.gson.annotations.SerializedName;

public class SuscripcionResponse {
    @SerializedName("plan")
    public String plan;

    @SerializedName("estado")
    public String estado;

    @SerializedName("mensaje")
    public String mensaje;
}
