package com.tamalon.app.network;

import com.google.gson.annotations.SerializedName;

public class CrearReporteRequest {
    @SerializedName("id_reportado")
    public int idReportado;

    @SerializedName("rol_reportado")
    public String rolReportado;

    @SerializedName("motivo")
    public String motivo;

    public CrearReporteRequest(int idReportado, String rolReportado, String motivo) {
        this.idReportado = idReportado;
        this.rolReportado = rolReportado;
        this.motivo = motivo;
    }
}
