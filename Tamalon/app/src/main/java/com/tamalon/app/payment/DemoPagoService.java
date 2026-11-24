package com.tamalon.app.payment;

import android.app.Activity;

/**
 * Modo demostración: simula aprobación o rechazo sin llamadas reales al proveedor.
 * Úsalo durante el desarrollo para probar el flujo completo de la UI de pago.
 * La activación de DemoPagoService NUNCA debe presentarse como un pago real.
 */
public class DemoPagoService implements PagoService {

    private final boolean simularAprobado;

    public DemoPagoService(boolean simularAprobado) {
        this.simularAprobado = simularAprobado;
    }

    @Override
    public void iniciarPago(Activity activity, String planId, String precio, Callback callback) {
        // Simula un retardo de 1.5 s para dar feedback visual en la UI
        activity.runOnUiThread(() -> {
            new android.os.Handler(android.os.Looper.getMainLooper()).postDelayed(() -> {
                callback.onResultado(new PagoService.ResultadoPago() {
                    @Override public EstadoPago getEstado() {
                        return simularAprobado ? EstadoPago.APROBADO : EstadoPago.RECHAZADO;
                    }
                    @Override public String getOperacionId() {
                        return simularAprobado ? "DEMO-APROBADO-001" : "DEMO-RECHAZADO-001";
                    }
                    @Override public String getMensaje() {
                        return simularAprobado
                                ? "payment_success"   // res ID de strings.xml
                                : "payment_error";
                    }
                });
            }, 1500);
        });
    }
}
