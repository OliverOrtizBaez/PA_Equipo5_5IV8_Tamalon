package com.tamalon.app.network;

import com.google.gson.annotations.SerializedName;

public class RegisterRequest {
    @SerializedName("nombre_completo")
    public String nombreCompleto;

    @SerializedName("correo")
    public String correo;

    @SerializedName("contrasena")
    public String contrasena;

    @SerializedName("confirmar_contrasena")
    public String confirmarContrasena;

    public RegisterRequest(String nombreCompleto, String correo, String contrasena, String confirmarContrasena) {
        this.nombreCompleto = nombreCompleto;
        this.correo = correo;
        this.contrasena = contrasena;
        this.confirmarContrasena = confirmarContrasena;
    }
}
