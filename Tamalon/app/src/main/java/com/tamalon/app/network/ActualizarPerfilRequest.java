package com.tamalon.app.network;

import com.google.gson.annotations.SerializedName;

public class ActualizarPerfilRequest {
    @SerializedName("nombre_completo")
    public String nombreCompleto;

    @SerializedName("telefono")
    public String telefono;

    @SerializedName("foto_perfil")
    public String fotoPerfil;

    public ActualizarPerfilRequest(String nombreCompleto, String telefono, String fotoPerfil) {
        this.nombreCompleto = nombreCompleto;
        this.telefono = telefono;
        this.fotoPerfil = fotoPerfil;
    }
}
