package com.tamalon.app.network;

import com.google.gson.annotations.SerializedName;
import java.util.List;

public class CategoriasResponse {
    @SerializedName("categorias")
    public List<CategoriaDto> categorias;
}
