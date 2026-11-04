package com.tamalon.app.validation;

/**
 * Valida las precondiciones antes de abrir el flujo de pago.
 *
 * SEGURIDAD: Este validador nunca recibe ni verifica datos de tarjeta.
 * Los datos de tarjeta deben manejarse exclusivamente a través del SDK del
 * proveedor de pagos (Mercado Pago Checkout / Conekta tokenización).
 * Nunca almacenes números de tarjeta, CVC ni fechas en la base de datos local.
 */
public final class ValidadorPago {

    private ValidadorPago() {}

    public static ResultadoValidacion validarPlanSeleccionado(String plan) {
        if (!ValidadorComun.esCadenaNoVacia(plan))
            return ResultadoValidacion.error("validation_required_field");
        if (!"premium".equals(plan))
            return ResultadoValidacion.error("validation_required_field");
        return ResultadoValidacion.ok();
    }

    public static ResultadoValidacion validarUsuarioAutenticado(int usuarioId) {
        if (usuarioId <= 0)
            return ResultadoValidacion.error("validation_unexpected_error");
        return ResultadoValidacion.ok();
    }

    /**
     * La verificación real del pago requiere un backend.
     * Esta validación solo comprueba que el resultado local no sea nulo.
     */
    public static ResultadoValidacion validarResultadoSandbox(String resultadoEstado) {
        if (!ValidadorComun.esCadenaNoVacia(resultadoEstado))
            return ResultadoValidacion.error("validation_unexpected_error");
        return ResultadoValidacion.ok();
    }
}
