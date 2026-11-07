package com.tamalon.app.network;

import com.google.gson.annotations.SerializedName;

public class LoginResponse {
    @SerializedName("token")
    public String token;

    @SerializedName("expira")
    public String expira;

    @SerializedName("usuario")
    public DtoUser usuario;
}
