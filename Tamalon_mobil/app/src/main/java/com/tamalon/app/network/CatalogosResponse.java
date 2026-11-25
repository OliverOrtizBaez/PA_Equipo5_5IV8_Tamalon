package com.tamalon.app.network;

import com.google.gson.annotations.SerializedName;
import java.util.List;

public class CatalogosResponse {
    @SerializedName("catalogos")
    public List<CatalogoDto> catalogos;
}
