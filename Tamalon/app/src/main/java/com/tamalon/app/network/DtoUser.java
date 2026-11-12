package com.tamalon.app.network;

import com.google.gson.annotations.SerializedName;

public class DtoUser {
    @SerializedName("id_usuario")
    public int idUsuario;

    @SerializedName("nombre_completo")
    public String nombreCompleto;

    @SerializedName("correo")
    public String correo;

    @SerializedName("telefono")
    public String telefono;

    @SerializedName("foto_perfil")
    public String fotoPerfil;

    @SerializedName("rol")
    public String rol;

    @SerializedName("plan")
    public String plan;

    @SerializedName("estado_cuenta")
    public String estadoCuenta;

    @SerializedName("zona_horaria")
    public String zonaHoraria;
}
