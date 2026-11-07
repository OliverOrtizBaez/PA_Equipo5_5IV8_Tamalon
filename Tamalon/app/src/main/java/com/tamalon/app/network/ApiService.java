package com.tamalon.app.network;

import retrofit2.Call;
import retrofit2.http.Body;
import retrofit2.http.DELETE;
import retrofit2.http.GET;
import retrofit2.http.POST;
import retrofit2.http.PUT;
import retrofit2.http.Path;
import retrofit2.http.Query;

public interface ApiService {

    // Auth
    @POST("api/auth/login")
    Call<LoginResponse> login(@Body LoginRequest request);

    @POST("api/auth/registro/cliente")
    Call<RegisterResponse> registroCliente(@Body RegisterRequest request);

    @POST("api/auth/registro/vendedor")
    Call<RegisterResponse> registroVendedor(@Body RegisterRequest request);

    @POST("api/auth/recuperar")
    Call<RecuperarResponse> recuperar(@Body RecuperarRequest request);

    @POST("api/auth/logout")
    Call<Void> logout();

    // Ubicación y Vendedores
    @PUT("api/ubicacion/mia")
    Call<UbicacionMiaResponse> actualizarUbicacionMia(@Body UbicacionMiaRequest request);

    @PUT("api/ubicacion/mia/fuera-de-ruta")
    Call<FueraDeRutaResponse> fueraDeRuta();

    @GET("api/ubicacion/cercanos")
    Call<CercanosResponse> obtenerCercanos(
            @Query("lat") double lat,
            @Query("lng") double lng,
            @Query("radio_km") float radioKm,
            @Query("categoria") String categoria,
            @Query("buscar") String buscar
    );

    @GET("api/ubicacion/vendedores/{id}")
    Call<VendedorEstadoResponse> obtenerEstadoVendedor(@Path("id") int id);

    @GET("api/usuarios/vendedores")
    Call<VendedoresListaResponse> obtenerVendedores(
            @Query("buscar") String buscar,
            @Query("pagina") int pagina,
            @Query("limite") int limite
    );

    // Catálogos
    @GET("api/catalogos")
    Call<CatalogosResponse> obtenerMisCatalogos();

    @GET("api/catalogos/vendedor/{id}")
    Call<CatalogosResponse> obtenerCatalogosVendedor(@Path("id") int id);

    @POST("api/catalogos")
    Call<CatalogoDto> crearCatalogo(@Body CrearCatalogoRequest request);

    @DELETE("api/catalogos/{id}")
    Call<Void> eliminarCatalogo(@Path("id") int id);

    // Productos
    @GET("api/productos/vendedor/{id}")
    Call<ProductosResponse> obtenerProductosVendedor(@Path("id") int id);

    @GET("api/productos/catalogo/{id}")
    Call<ProductosResponse> obtenerProductosCatalogo(@Path("id") int id);

    // Favoritos
    @GET("api/favoritos")
    Call<FavoritosResponse> obtenerFavoritos();

    @POST("api/favoritos")
    Call<Void> agregarFavorito(@Body AgregarFavoritoRequest request);

    @DELETE("api/favoritos/{vendedor_id}")
    Call<Void> eliminarFavorito(@Path("vendedor_id") int vendedorId);

    // Soporte y Reportes
    @POST("api/soporte/reportes")
    Call<ReporteResponse> enviarReporte(@Body CrearReporteRequest request);

    // Perfil
    @GET("api/usuarios/perfil")
    Call<DtoUser> obtenerPerfil();

    @PUT("api/usuarios/perfil")
    Call<DtoUser> actualizarPerfil(@Body ActualizarPerfilRequest request);

    // Suscripciones
    @GET("api/suscripciones/mi-plan")
    Call<SuscripcionResponse> obtenerMiPlan();
}
