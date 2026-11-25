package com.tamalon.app.network;

import com.google.gson.annotations.SerializedName;
import java.util.List;

public class EstadisticasResponse {
    @SerializedName("total_aperturas")
    public int totalAperturas;

    @SerializedName("total_favoritos")
    public int totalFavoritos;

    @SerializedName("visitas_por_dia")
    public List<VisitaDia> visitasPorDia;

    public static class VisitaDia {
        @SerializedName("fecha")
        public String fecha;
        @SerializedName("visitas")
        public int visitas;
    }
}
