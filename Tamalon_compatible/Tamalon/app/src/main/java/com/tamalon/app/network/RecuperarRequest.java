package com.tamalon.app.network;

import com.google.gson.annotations.SerializedName;

public class RecuperarRequest {
    @SerializedName("correo")
    public String correo;

    public RecuperarRequest(String correo) {
        this.correo = correo;
    }
}
