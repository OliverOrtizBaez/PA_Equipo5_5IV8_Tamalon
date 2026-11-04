package com.tamalon.app.validation;

public final class ValidadorLogin {

    private ValidadorLogin() {}

    public static ResultadoValidacion validarCorreo(String correo) {
        if (!ValidadorComun.esCadenaNoVacia(correo))
            return ResultadoValidacion.error("validation_required_field");
        if (!ValidadorComun.esCorreoValido(correo))
            return ResultadoValidacion.error("validation_invalid_email");
        return ResultadoValidacion.ok();
    }

    public static ResultadoValidacion validarPassword(String password) {
        if (!ValidadorComun.esCadenaNoVacia(password))
            return ResultadoValidacion.error("validation_required_field");
        return ResultadoValidacion.ok();
    }

    /**
     * Valida que el usuario exista localmente. La verificación de hash se realiza
     * en AutenticacionService; aquí solo se comprueba la existencia.
     */
    public static ResultadoValidacion validarExistencia(Object usuario) {
        if (usuario == null)
            return ResultadoValidacion.error("validation_account_not_found");
        return ResultadoValidacion.ok();
    }

    public static ResultadoValidacion validarCredenciales(boolean coinciden) {
        if (!coinciden)
            return ResultadoValidacion.error("validation_invalid_credentials");
        return ResultadoValidacion.ok();
    }
}
