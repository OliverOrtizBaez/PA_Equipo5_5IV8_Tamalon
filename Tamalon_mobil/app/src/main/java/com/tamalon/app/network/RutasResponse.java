package com.tamalon.app.network;

import com.google.gson.annotations.SerializedName;
import java.util.List;

public class RutasResponse {
    @SerializedName("rutas")
    public List<RutaDto> rutas;
}
