package com.tamalon.app.validation;

import java.util.regex.Pattern;

/** Valida los campos del formulario de registro. */
public final class ValidadorRegistro {

    private static final Pattern PASS_TIENE_LETRA  = Pattern.compile(".*[A-Za-z].*");
    private static final Pattern PASS_TIENE_NUMERO = Pattern.compile(".*[0-9].*");

    private ValidadorRegistro() {}

    public static ResultadoValidacion validarNombre(String nombre) {
        if (!ValidadorComun.esCadenaNoVacia(nombre))
            return ResultadoValidacion.error("validation_required_field");
        if (!ValidadorComun.esNombreValido(nombre))
            return ResultadoValidacion.error("validation_name_length");
        return ResultadoValidacion.ok();
    }

    public static ResultadoValidacion validarCorreo(String correo) {
        if (!ValidadorComun.esCadenaNoVacia(correo))
            return ResultadoValidacion.error("validation_required_field");
        if (!ValidadorComun.esCorreoLongitudValida(correo))
            return ResultadoValidacion.error("validation_email_too_long");
        if (!ValidadorComun.esCorreoValido(correo))
            return ResultadoValidacion.error("validation_invalid_email");
        return ResultadoValidacion.ok();
    }

    public static ResultadoValidacion validarPassword(String password) {
        if (!ValidadorComun.esCadenaNoVacia(password))
            return ResultadoValidacion.error("validation_required_field");
        int len = password.length();
        if (len < 8 || len > 64)
            return ResultadoValidacion.error("validation_password_length");
        if (!PASS_TIENE_LETRA.matcher(password).matches() ||
            !PASS_TIENE_NUMERO.matcher(password).matches())
            return ResultadoValidacion.error("validation_password_format");
        return ResultadoValidacion.ok();
    }

    public static ResultadoValidacion validarConfirmacion(String password, String confirmacion) {
        if (!ValidadorComun.esCadenaNoVacia(confirmacion))
            return ResultadoValidacion.error("validation_required_field");
        if (!password.equals(confirmacion))
            return ResultadoValidacion.error("validation_passwords_mismatch");
        return ResultadoValidacion.ok();
    }

    public static ResultadoValidacion validarRol(String rol) {
        if (rol == null || (!rol.equals("cliente") && !rol.equals("vendedor")))
            return ResultadoValidacion.error("validation_select_role");
        return ResultadoValidacion.ok();
    }

    public static ResultadoValidacion validarTerminos(boolean aceptados) {
        if (!aceptados)
            return ResultadoValidacion.error("validation_accept_terms");
        return ResultadoValidacion.ok();
    }

    public static ResultadoValidacion validarCorreoUnico(int countExistente) {
        if (countExistente > 0)
            return ResultadoValidacion.error("validation_email_taken");
        return ResultadoValidacion.ok();
    }
}
