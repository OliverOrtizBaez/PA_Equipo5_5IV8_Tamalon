package com.tamalon.app.validation;

public final class ValidadorRuta {

    public static final int RUTAS_MAX_PREMIUM = 3;
    public static final int PUNTOS_MIN        = 2;
    public static final int PUNTOS_MAX        = 50;

    private ValidadorRuta() {}

    public static ResultadoValidacion validarAccesoPremium(boolean esPremium) {
        if (!esPremium)
            return ResultadoValidacion.error("validation_premium_required");
        return ResultadoValidacion.ok();
    }

    public static ResultadoValidacion validarPermisoUbicacion(boolean tienePermiso) {
        if (!tienePermiso)
            return ResultadoValidacion.error("availability_no_permission");
        return ResultadoValidacion.ok();
    }

    public static ResultadoValidacion validarLimiteRutas(int rutasActuales) {
        if (rutasActuales >= RUTAS_MAX_PREMIUM)
            return ResultadoValidacion.error("routes_limit");
        return ResultadoValidacion.ok();
    }

    public static ResultadoValidacion validarDiaSinConflicto(boolean diaOcupado) {
        if (diaOcupado)
            return ResultadoValidacion.error("routes_day_conflict");
        return ResultadoValidacion.ok();
    }

    public static ResultadoValidacion validarCantidadPuntos(int puntos) {
        if (puntos < PUNTOS_MIN || puntos > PUNTOS_MAX)
            return ResultadoValidacion.errorLiteral(
                    "La ruta debe tener entre " + PUNTOS_MIN + " y " + PUNTOS_MAX + " puntos.");
        return ResultadoValidacion.ok();
    }

    public static ResultadoValidacion validarDiaSeleccionado(String dia) {
        if (!ValidadorComun.esCadenaNoVacia(dia))
            return ResultadoValidacion.error("validation_required_field");
        return ResultadoValidacion.ok();
    }
}
