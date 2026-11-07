package com.tamalon.app.network;

import com.google.gson.annotations.SerializedName;

public class VendedorCercanoDto {
    @SerializedName("id_usuario")
    public int idUsuario;

    @SerializedName("nombre_completo")
    public String nombreCompleto;

    @SerializedName("foto_perfil")
    public String fotoPerfil;

    @SerializedName("rol")
    public String rol;

    @SerializedName("latitud")
    public double latitud;

    @SerializedName("longitud")
    public double longitud;

    @SerializedName("distancia_m")
    public double distanciaM;

    @SerializedName("ultima_actualizacion")
    public String ultimaActualizacion;
}
