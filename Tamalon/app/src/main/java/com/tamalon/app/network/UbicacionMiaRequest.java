package com.tamalon.app.network;

import com.google.gson.annotations.SerializedName;

public class UbicacionMiaRequest {
    @SerializedName("latitud")
    public double latitud;

    @SerializedName("longitud")
    public double longitud;

    public UbicacionMiaRequest(double latitud, double longitud) {
        this.latitud = latitud;
        this.longitud = longitud;
    }
}
