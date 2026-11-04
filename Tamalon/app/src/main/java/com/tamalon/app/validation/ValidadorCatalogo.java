package com.tamalon.app.validation;

public final class ValidadorCatalogo {

    public static final int LIMITE_GRATUITO = 1;
    public static final int LIMITE_PREMIUM  = 3;

    private ValidadorCatalogo() {}

    public static ResultadoValidacion validarNombre(String nombre) {
        if (!ValidadorComun.esCadenaNoVacia(nombre))
            return ResultadoValidacion.error("validation_required_field");
        if (!ValidadorComun.esCadenaEnRango(nombre, 1, 50))
            return ResultadoValidacion.error("validation_catalog_name_length");
        return ResultadoValidacion.ok();
    }

    /**
     * Comprueba si el vendedor puede crear un catálogo adicional según su plan.
     * @param catalogosActuales número de catálogos visibles del vendedor
     * @param esPremium         true si el vendedor tiene plan Premium
     */
    public static ResultadoValidacion validarLimite(int catalogosActuales, boolean esPremium) {
        int limite = esPremium ? LIMITE_PREMIUM : LIMITE_GRATUITO;
        if (catalogosActuales >= limite) {
            String resId = esPremium ? "catalog_limit_premium" : "catalog_limit_free";
            return ResultadoValidacion.error(resId);
        }
        return ResultadoValidacion.ok();
    }
}
