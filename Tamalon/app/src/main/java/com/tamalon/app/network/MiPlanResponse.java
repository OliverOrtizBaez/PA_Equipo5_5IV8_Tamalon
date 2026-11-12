package com.tamalon.app.network;

import com.google.gson.annotations.SerializedName;
import java.util.List;

public class MiPlanResponse {
    @SerializedName("rol")
    public String rol;

    @SerializedName("plan")
    public String plan;

    @SerializedName("fecha_corte")
    public String fechaCorte;

    @SerializedName("renovacion")
    public Boolean renovacion;

    @SerializedName("pago_simulado_habilitado")
    public Boolean pagoSimuladoHabilitado;

    @SerializedName("pagos")
    public List<Object> pagos;
}
