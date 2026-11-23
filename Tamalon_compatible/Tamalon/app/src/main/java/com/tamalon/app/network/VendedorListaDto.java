package com.tamalon.app.network;

import com.google.gson.annotations.SerializedName;

public class VendedorListaDto {
    @SerializedName("id_usuario")
    public int idUsuario;

    @SerializedName("nombre_completo")
    public String nombreCompleto;

    @SerializedName("foto_perfil")
    public String fotoPerfil;

    @SerializedName("rol")
    public String rol;

    @SerializedName("plan")
    public String plan;

    @SerializedName("en_ruta")
    public boolean enRuta;

    @SerializedName("categorias")
    public String categorias;

    @SerializedName("descripcion")
    public String descripcion;
}
