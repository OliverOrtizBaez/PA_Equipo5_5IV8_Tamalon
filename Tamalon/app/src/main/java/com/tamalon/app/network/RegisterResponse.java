package com.tamalon.app.network;

import com.google.gson.annotations.SerializedName;

public class RegisterResponse {
    @SerializedName("mensaje")
    public String mensaje;

    @SerializedName("usuario")
    public DtoUser usuario;
}
