package com.tamalon.app.validation;

import java.util.regex.Pattern;

/** Reglas de validación reutilizables entre módulos. */
public final class ValidadorComun {

    private static final Pattern EMAIL_RE =
            Pattern.compile("^[A-Za-z0-9+_.-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,}$");
    private static final Pattern SOLO_DIGITOS = Pattern.compile("^\\d+$");

    private ValidadorComun() {}

    public static boolean esCorreoValido(String correo) {
        return correo != null && EMAIL_RE.matcher(correo.trim()).matches();
    }

    public static boolean esCorreoLongitudValida(String correo) {
        return correo != null && correo.trim().length() <= 100;
    }

    public static boolean esNombreValido(String nombre) {
        if (nombre == null) return false;
        int len = nombre.trim().length();
        return len >= 2 && len <= 100;
    }

    public static boolean esTelefonoValido(String telefono) {
        if (telefono == null) return false;
        String t = telefono.replaceAll("[\\s\\-]", "");
        return t.length() == 10 && SOLO_DIGITOS.matcher(t).matches();
    }

    public static boolean esImagenFormatoValido(String nombreArchivo) {
        if (nombreArchivo == null) return false;
        String lower = nombreArchivo.toLowerCase();
        return lower.endsWith(".jpg") || lower.endsWith(".jpeg") || lower.endsWith(".png");
    }

    public static boolean esImagenTamanoValido(long bytesSize) {
        return bytesSize > 0 && bytesSize <= 5 * 1024 * 1024L; // 5 MB
    }

    public static boolean esCadenaNoVacia(String valor) {
        return valor != null && !valor.trim().isEmpty();
    }

    public static boolean esCadenaEnRango(String valor, int min, int max) {
        if (valor == null) return false;
        int len = valor.trim().length();
        return len >= min && len <= max;
    }
}
