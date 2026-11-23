package com.tamalon.app.validation;

public final class ValidadorPerfil {

    private ValidadorPerfil() {}

    public static ResultadoValidacion validarNombre(String nombre) {
        if (!ValidadorComun.esCadenaNoVacia(nombre))
            return ResultadoValidacion.error("validation_required_field");
        if (!ValidadorComun.esNombreValido(nombre))
            return ResultadoValidacion.error("validation_name_length");
        return ResultadoValidacion.ok();
    }

    public static ResultadoValidacion validarTelefono(String telefono) {
        if (telefono == null || telefono.trim().isEmpty())
            return ResultadoValidacion.ok(); // Teléfono es opcional en el perfil inicial
        if (!ValidadorComun.esTelefonoValido(telefono))
            return ResultadoValidacion.error("validation_phone_format");
        return ResultadoValidacion.ok();
    }

    public static ResultadoValidacion validarFotografia(String nombreArchivo, long tamanoBytes) {
        if (nombreArchivo == null || nombreArchivo.isEmpty())
            return ResultadoValidacion.ok(); // La foto es opcional
        if (!ValidadorComun.esImagenFormatoValido(nombreArchivo))
            return ResultadoValidacion.error("validation_image_format");
        if (!ValidadorComun.esImagenTamanoValido(tamanoBytes))
            return ResultadoValidacion.error("validation_image_size");
        return ResultadoValidacion.ok();
    }
}
