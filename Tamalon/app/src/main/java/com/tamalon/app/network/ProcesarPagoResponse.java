package com.tamalon.app.network;

import com.google.gson.annotations.SerializedName;

public class ProcesarPagoResponse {
    @SerializedName("mensaje")
    public String mensaje;

    @SerializedName("simulado")
    public Boolean simulado;

    @SerializedName("estado")
    public String estado;

    @SerializedName("referencia")
    public Integer referencia;

    @SerializedName("codigo_transaccion")
    public String codigoTransaccion;

    @SerializedName("rol")
    public String rol;

    @SerializedName("plan")
    public String plan;

    @SerializedName("fecha_corte")
    public String fechaCorte;
}
