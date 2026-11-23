package com.tamalon.app.network;

import com.google.gson.annotations.SerializedName;

public class LoginRequest {
    @SerializedName("correo")
    public String correo;

    @SerializedName("contrasena")
    public String contrasena;

    @SerializedName("zona_horaria")
    public String zonaHoraria;

    public LoginRequest(String correo, String contrasena, String zonaHoraria) {
        this.correo = correo;
        this.contrasena = contrasena;
        this.zonaHoraria = zonaHoraria;
    }
}
