package com.tamalon.app.validation;

/**
 * Resultado inmutable de una operación de validación.
 * Cada validador devuelve este objeto para separar la lógica de validación
 * de la presentación del mensaje de error en la Activity.
 */
public final class ResultadoValidacion {

    private final boolean valido;
    private final String mensajeErrorResId;  // nombre del recurso string, e.g. "validation_required_field"
    private final String mensajeErrorLiteral; // mensaje ya resuelto cuando no hay contexto de recursos

    private ResultadoValidacion(boolean valido, String mensajeErrorResId, String literal) {
        this.valido = valido;
        this.mensajeErrorResId = mensajeErrorResId;
        this.mensajeErrorLiteral = literal;
    }

    public static ResultadoValidacion ok() {
        return new ResultadoValidacion(true, null, null);
    }

    public static ResultadoValidacion error(String resIdNombre) {
        return new ResultadoValidacion(false, resIdNombre, null);
    }

    public static ResultadoValidacion errorLiteral(String mensaje) {
        return new ResultadoValidacion(false, null, mensaje);
    }

    public boolean esValido() { return valido; }

    public String getMensajeErrorResId() { return mensajeErrorResId; }

    public String getMensajeErrorLiteral() { return mensajeErrorLiteral; }

    public boolean tieneResId() {
        return mensajeErrorResId != null && !mensajeErrorResId.isEmpty();
    }
}
