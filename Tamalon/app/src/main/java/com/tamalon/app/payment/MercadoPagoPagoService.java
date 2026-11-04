package com.tamalon.app.payment;

import android.app.Activity;

/**
 * ============================================================
 * INTEGRACIÓN MERCADO PAGO — INSTRUCCIONES DE CONFIGURACIÓN
 * ============================================================
 *
 * Mercado Pago es el proveedor de pagos primario para Tamalon (Android).
 *
 * DEPENDENCIA (app/build.gradle):
 *   implementation 'com.mercadopago.android.pt:checkout:5.X.X'
 *   (Consulta la versión más reciente en https://github.com/mercadopago/sdk-android)
 *
 * CREDENCIALES DE PRUEBA (Sandbox):
 *   - Obtén tu PUBLIC_KEY de prueba desde el Dashboard de Mercado Pago:
 *     https://www.mercadopago.com.mx/developers/panel/credentials
 *   - NO incluyas claves dentro del código fuente ni en los XML.
 *   - Guárdalas en local.properties y léelas en build.gradle como BuildConfig.
 *
 * FLUJO DE INTEGRACIÓN:
 *   1. El backend de Tamalon (Supabase Edge Function) crea una "preferencia" en MP
 *      con el precio, descripción y referencia del usuario.
 *   2. El backend devuelve el preference_id al app.
 *   3. El app lanza MercadoPagoCheckout con ese preference_id.
 *   4. MP maneja la UI de pago internamente (no se recopilan datos de tarjeta en el app).
 *   5. MP devuelve el resultado al app vía onActivityResult.
 *   6. El backend verifica el pago con la API de MP y actualiza la suscripción.
 *
 * TARJETAS DE PRUEBA (Sandbox):
 *   Número: 4509 9535 6623 3704  |  CVV: 123  |  Venc: 11/25  |  Titular: APRO
 *   Número: 4509 9535 6623 3704  |  CVV: 123  |  Venc: 11/25  |  Titular: OTHE (rechazado)
 *   Ver lista completa: https://www.mercadopago.com.mx/developers/es/docs/your-integrations/test
 *
 * ESTADO ACTUAL: PENDIENTE DE IMPLEMENTACIÓN.
 * Esta clase es un stub documentado. La integración real requiere:
 *   - Backend configurado para crear preferencias en MP.
 *   - PUBLIC_KEY de prueba en BuildConfig.
 *   - SDK de MP como dependencia en build.gradle.
 *
 * NOTA CONEKTA:
 *   Conekta ofrece una alternativa para México. Su SDK de Android es menos maduro.
 *   Se recomienda Conekta.js (web) para el checkout o su API REST con tokenización.
 *   Dependencia: implementation 'io.conekta:conekta-android:X.Y.Z'
 *   Documentación: https://developers.conekta.com/docs/android
 *   La integración seguiría el mismo patrón: backend crea orden → app abre checkout.
 *   Para activar Conekta, implementa PagoService en ConektaPagoService.java.
 * ============================================================
 */
public class MercadoPagoPagoService implements PagoService {

    // Reemplaza por BuildConfig.MP_PUBLIC_KEY cuando configures build.gradle
    private static final String PUBLIC_KEY_SANDBOX = "TU_PUBLIC_KEY_SANDBOX_AQUI";

    @Override
    public void iniciarPago(Activity activity, String planId, String precio, Callback callback) {
        /*
         * PASO 1: Solicitar preference_id al backend de Tamalon.
         *
         * Integración futura (pseudocódigo):
         *   SupabaseClient.crearPreferenciaMercadoPago(usuarioId, planId, precio,
         *       preferenceId -> {
         *           // PASO 2: Lanzar el checkout de MP
         *           new MercadoPagoCheckout.Builder(PUBLIC_KEY_SANDBOX, preferenceId)
         *               .build()
         *               .startPayment(activity, REQUEST_CODE_MP);
         *       },
         *       error -> callback.onResultado(ResultadoError("payment_backend_note"))
         *   );
         *
         * PASO 3: Capturar resultado en activity.onActivityResult(REQUEST_CODE_MP, ...)
         *   y llamar a callback.onResultado(resultado).
         */
        callback.onResultado(new ResultadoPago() {
            @Override public EstadoPago getEstado()     { return EstadoPago.ERROR; }
            @Override public String    getOperacionId() { return null; }
            @Override public String    getMensaje()     { return "payment_backend_note"; }
        });
    }
}
