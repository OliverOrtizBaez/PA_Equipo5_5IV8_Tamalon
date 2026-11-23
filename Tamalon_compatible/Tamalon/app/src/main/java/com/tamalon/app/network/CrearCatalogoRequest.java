package com.tamalon.app.network;

import com.google.gson.annotations.SerializedName;

public class CrearCatalogoRequest {
    @SerializedName("nombre")
    public String nombre;

    @SerializedName("estado")
    public String estado;

    @SerializedName("id_vendedor")
    public int idVendedor;

    public CrearCatalogoRequest(String nombre) {
        this.nombre = nombre;
        this.estado = "Activo";
    }

    public CrearCatalogoRequest(String nombre, String estado, int idVendedor) {
        this.nombre = nombre;
        this.estado = estado == null || estado.trim().isEmpty() ? "Activo" : estado.trim();
        this.idVendedor = idVendedor;
    }
}
