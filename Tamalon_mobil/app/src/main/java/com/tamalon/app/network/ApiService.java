package com.tamalon.app.network;

import java.util.List;
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

    @POST("api/ubicacion/jornada/iniciar")
    Call<Void> iniciarJornada();

    @POST("api/ubicacion/jornada/finalizar")
    Call<JornadaResumenResponse> finalizarJornada();

    @PUT("api/ubicacion/mia/fuera-de-ruta")
    Call<FueraDeRutaResponse> fueraDeRuta();

    @GET("api/ubicacion/cercanos")
    Call<CercanosResponse> obtenerCercanos(
            @Query("lat") double lat,
            @Query("lng") double lng,
            @Query("radio_km") float radioKm,
            @Query("id_tipo_vendedor") Integer idTipoVendedor,
            @Query("buscar") String buscar
    );

    @GET("api/ubicacion/vendedores/{id}")
    Call<VendedorEstadoResponse> obtenerEstadoVendedor(@Path("id") int id);

    @GET("api/usuarios/vendedores")
    Call<VendedoresListaResponse> obtenerVendedores(
            @Query("id_tipo_vendedor") Integer idTipoVendedor,
            @Query("buscar") String buscar,
            @Query("pagina") int pagina,
            @Query("limite") int limite
    );

    // Tipos de vendedor y categorías
    @GET("api/tipos-vendedor")
    Call<List<TipoVendedorDto>> obtenerTiposVendedor();

    @GET("api/tipos-vendedor/{id}/categorias")
    Call<List<String>> obtenerCategoriasTipoVendedor(@Path("id") int idTipoVendedor);

    // Catálogos
    @GET("api/catalogos/mios")
    Call<CatalogosResponse> obtenerMisCatalogos();

    @GET("api/catalogos/vendedor/{id}")
    Call<CatalogosResponse> obtenerCatalogosVendedor(@Path("id") int id);

    @POST("api/catalogos")
    Call<CatalogoDto> crearCatalogo(@Body CrearCatalogoRequest request);

    @PUT("api/catalogos/{id}")
    Call<CatalogoDto> actualizarCatalogo(@Path("id") int id, @Body CrearCatalogoRequest request);

    @DELETE("api/catalogos/{id}")
    Call<Void> eliminarCatalogo(@Path("id") int id);

    // Productos
    @GET("api/catalogos/{id}/productos")
    Call<ProductosResponse> obtenerProductosCatalogo(@Path("id") int idCatalogo);

    @GET("api/productos/vendedor/{id}")
    Call<ProductosResponse> obtenerProductosVendedor(@Path("id") int id);

    @POST("api/catalogos/{id}/productos")
    Call<ProductoDto> crearProducto(@Path("id") int idCatalogo, @Body CrearProductoRequest request);

    @PUT("api/catalogos/productos/{id}")
    Call<ProductoDto> actualizarProducto(@Path("id") int idProducto, @Body CrearProductoRequest request);

    @DELETE("api/catalogos/productos/{id}")
    Call<Void> eliminarProducto(@Path("id") int idProducto);

    // Rutas y Waypoints
    @GET("api/rutas")
    Call<RutasResponse> obtenerMisRutas();

    @GET("api/rutas/vendedor/{id}")
    Call<RutasResponse> obtenerRutasVendedor(@Path("id") int id);

    @POST("api/rutas")
    Call<RutaDto> crearRuta(@Body CrearRutaRequest request);

    @PUT("api/rutas/{id}")
    Call<RutaDto> actualizarRuta(@Path("id") int id, @Body CrearRutaRequest request);

    @DELETE("api/rutas/{id}")
    Call<Void> eliminarRuta(@Path("id") int id);

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

    // Suscripciones / Premium
    @GET("api/suscripciones/mia")
    Call<MiPlanResponse> obtenerMiPlan();

    @POST("api/suscripciones/pagar")
    Call<ProcesarPagoResponse> procesarPago(@Body ProcesarPagoRequest request);

    // Estadísticas
    @GET("api/estadisticas/vendedor")
    Call<EstadisticasResponse> obtenerEstadisticasVendedor();

    // Eventos
    @POST("api/eventos")
    Call<Void> registrarEvento(@Body EventoRequest request);
}
