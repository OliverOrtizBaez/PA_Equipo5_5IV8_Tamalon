package com.tamalon.app.config;

/**
 * Parámetros de configuración de Tamalon.
 *
 * SEGURIDAD: No almacenes claves privadas, Access Tokens ni credenciales de producción
 * en este archivo. Usa BuildConfig (leído desde local.properties) para valores sensibles.
 *
 * Las URL de Supabase y las API keys de Mercado Pago / Conekta deben definirse en
 * local.properties y exponerse a través de BuildConfig.SUPABASE_URL, etc.
 */
public final class AppConfig {

    private AppConfig() {}

    // --- Mapa ---
    public static final float RADIO_KM                    = 3.0f;
    public static final long  INTERVALO_ACTUALIZACION_MS  = 15_000L;
    public static final long  TIMEOUT_FUERA_DE_RUTA_MS    = 60_000L;

    // --- Límites por plan ---
    public static final int CATALOGOS_GRATUITO  = 1;
    public static final int CATALOGOS_PREMIUM   = 3;
    public static final int PRODUCTOS_GRATUITO  = 10;
    public static final int PRODUCTOS_PREMIUM   = 50;
    public static final int RUTAS_PREMIUM       = 3;
    public static final int FAVORITOS_PREMIUM   = 5;

    // --- Límites de texto ---
    public static final int MAX_CORREO          = 100;
    public static final int MAX_NOMBRE          = 100;
    public static final int MIN_NOMBRE          = 2;
    public static final int MIN_PASSWORD        = 8;
    public static final int MAX_PASSWORD        = 64;
    public static final int MAX_CATALOGO_NOMBRE = 50;
    public static final int MAX_PRODUCTO_NOMBRE = 60;
    public static final int MIN_PRODUCTO_NOMBRE = 2;
    public static final int MAX_DESCRIPCION     = 300;
    public static final int MAX_REPORTE_MOTIVO  = 500;
    public static final int MAX_SOPORTE_ASUNTO  = 120;
    public static final int MAX_SOPORTE_DESC    = 1000;

    // --- Imágenes ---
    public static final long MAX_IMAGEN_BYTES   = 5 * 1024 * 1024L; // 5 MB

    // --- Pago ---
    // Cambiar a false cuando el backend de pagos esté disponible

    // Integración futura — leer desde BuildConfig:
    // public static final String SUPABASE_URL     = BuildConfig.SUPABASE_URL;
    // public static final String SUPABASE_ANON    = BuildConfig.SUPABASE_ANON_KEY;
    // public static final String MP_PUBLIC_KEY    = BuildConfig.MP_PUBLIC_KEY_SANDBOX;
}
