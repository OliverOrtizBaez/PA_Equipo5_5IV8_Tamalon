package com.tamalon.app.network;

import com.google.gson.annotations.SerializedName;

public class UbicacionMiaResponse {
    @SerializedName("mensaje")
    public String mensaje;

    @SerializedName("ubicacion")
    public UbicacionInfo ubicacion;

    public static class UbicacionInfo {
        @SerializedName("latitud")
        public double latitud;

        @SerializedName("longitud")
        public double longitud;

        @SerializedName("disponibilidad")
        public String disponibilidad;

        @SerializedName("ultima_actualizacion")
        public String ultimaActualizacion;
    }
}
