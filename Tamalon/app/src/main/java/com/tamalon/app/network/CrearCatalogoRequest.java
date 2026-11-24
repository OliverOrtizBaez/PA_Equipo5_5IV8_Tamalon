package com.tamalon.app.network;

import com.google.gson.annotations.SerializedName;

public class CrearCatalogoRequest {
    @SerializedName("nombre")
    public String nombre;

    public CrearCatalogoRequest(String nombre) {
        this.nombre = nombre;
    }
}
