package com.tamalon.app.payment;

import android.app.Activity;

/**
 * Interfaz de pago de Tamalon.
 * Permite cambiar de proveedor (Mercado Pago → Conekta → otro) sin modificar las Activities.
 *
 * Implementaciones disponibles:
 *   - MercadoPagoPagoService  (implementación con SDK de Mercado Pago — ver clase homónima)
 *   - ConektaPagoService       (stub documentado — ver clase homónima)
 *
 * SEGURIDAD: Ninguna implementación debe recibir ni almacenar datos de tarjeta.
 * Los datos de tarjeta deben tokenizarse dentro del SDK del proveedor.
 */
public interface PagoService {

    /** Estados posibles del resultado de una operación de pago. */
    enum EstadoPago {
        APROBADO,
        RECHAZADO,
        CANCELADO,
        EN_PROCESO,
        ERROR
    }

    interface ResultadoPago {
        EstadoPago getEstado();
        String getOperacionId();   // ID de la operación en el proveedor (sandbox o producción)
        String getMensaje();       // Mensaje de resultado para mostrar al usuario
    }

    interface Callback {
        void onResultado(ResultadoPago resultado);
    }

    /**
     * Abre el flujo de pago del proveedor.
     * @param activity  Activity actual (necesaria para startActivityForResult o CheckoutBuilder)
     * @param planId    Identificador del plan a contratar ("premium")
     * @param precio    Precio en MXN como String (ej. "49.00")
     * @param callback  Recibe el resultado cuando el proveedor regresa a la app
     */
    void iniciarPago(Activity activity, String planId, String precio, Callback callback);
}
