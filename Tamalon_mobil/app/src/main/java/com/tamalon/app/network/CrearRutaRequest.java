package com.tamalon.app.network;

import com.google.gson.annotations.SerializedName;
import java.util.List;

public class CrearRutaRequest {
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

    public CrearRutaRequest(int idVendedor, String estado, String diaSemana,
                            String nombre, String horario, List<WaypointDto> waypoints) {
        this.idVendedor = idVendedor;
        this.estado = estado;
        this.diaSemana = diaSemana;
        this.nombre = nombre;
        this.horario = horario;
        this.waypoints = waypoints;
    }
}
