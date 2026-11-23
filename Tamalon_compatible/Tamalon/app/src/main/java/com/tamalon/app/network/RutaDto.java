package com.tamalon.app.network;

import com.google.gson.annotations.SerializedName;
import java.util.List;

public class RutaDto {
    @SerializedName("id_ruta")
    public int idRuta;

    @SerializedName("id_vendedor")
    public int idVendedor;

    @SerializedName("estado")
    public String estado;

    @SerializedName("dia_semana")
    public String diaSemana;

    @SerializedName("nombre")
    public String nombre;

    @SerializedName("horario")
    public String horario;

    @SerializedName("waypoints")
    public List<WaypointDto> waypoints;
}
