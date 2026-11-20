package com.tamalon.app.network;

import com.google.gson.annotations.SerializedName;

public class VendedorEstadoResponse {
    @SerializedName("en_ruta")
    public boolean enRuta;

    @SerializedName("latitud")
    public Double latitud;

    @SerializedName("longitud")
    public Double longitud;

    @SerializedName("ultima_actualizacion")
    public String ultimaActualizacion;

    @SerializedName("mensaje")
    public String mensaje;
}
