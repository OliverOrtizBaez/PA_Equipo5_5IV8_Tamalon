package com.tamalon.app.network;

import com.google.gson.annotations.SerializedName;

public class WaypointDto {
    @SerializedName("id_waypoint")
    public int idWaypoint;

    @SerializedName("id_ruta")
    public int idRuta;

    @SerializedName("orden")
    public int orden;

    @SerializedName("latitud")
    public double latitud;

    @SerializedName("longitud")
    public double longitud;

    public WaypointDto() {}

    public WaypointDto(int orden, double latitud, double longitud) {
        this.orden = orden;
        this.latitud = latitud;
        this.longitud = longitud;
    }
}
