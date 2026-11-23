package com.tamalon.app.validation;

public final class ValidadorReporte {

    public static final int MOTIVO_MAX = 500;

    private ValidadorReporte() {}

    public static ResultadoValidacion validarMotivo(String motivo) {
        if (!ValidadorComun.esCadenaNoVacia(motivo))
            return ResultadoValidacion.error("report_reason_required");
        if (!ValidadorComun.esCadenaEnRango(motivo, 1, MOTIVO_MAX))
            return ResultadoValidacion.error("report_reason_length");
        return ResultadoValidacion.ok();
    }

    /** El emisor y el reportado deben ser cuentas válidas con IDs positivos. */
    public static ResultadoValidacion validarParticipantes(int emisorId, int reportadoId) {
        if (emisorId <= 0 || reportadoId <= 0)
            return ResultadoValidacion.error("validation_unexpected_error");
        if (emisorId == reportadoId)
            return ResultadoValidacion.errorLiteral("Un usuario no puede reportarse a sí mismo.");
        return ResultadoValidacion.ok();
    }

    /** Vendedor reporta Cliente y Cliente reporta Vendedor; no el mismo rol. */
    public static ResultadoValidacion validarRoles(String rolEmisor, String rolReportado) {
        if (rolEmisor == null || rolReportado == null)
            return ResultadoValidacion.error("validation_unexpected_error");
        if (rolEmisor.equals(rolReportado))
            return ResultadoValidacion.errorLiteral("Solo puedes reportar a usuarios del rol opuesto.");
        return ResultadoValidacion.ok();
    }
}
