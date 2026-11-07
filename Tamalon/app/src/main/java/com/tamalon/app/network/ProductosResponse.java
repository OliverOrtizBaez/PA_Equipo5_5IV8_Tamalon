package com.tamalon.app.network;

import com.google.gson.annotations.SerializedName;
import java.util.List;

public class ProductosResponse {
    @SerializedName("productos")
    public List<ProductoDto> productos;
}
