package com.tamalon.app.network;

import com.google.gson.annotations.SerializedName;
import java.util.List;

public class FavoritosResponse {
    @SerializedName("favoritos")
    public List<FavoritoDto> favoritos;
}
