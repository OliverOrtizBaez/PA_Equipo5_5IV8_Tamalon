package com.tamalon.app.util;

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;

/**
 * Utilidad para hash de contraseñas locales con SHA-256 + salt.
 *
 * LIMITACIÓN: Este mecanismo es adecuado para autenticación local de prototipo.
 * Para producción, la autenticación debe delegarse a Supabase Auth, que utiliza
 * bcrypt del lado del servidor. No reutilices estas credenciales locales en el backend.
 */
public final class PasswordUtil {

    private static final int SALT_BYTES = 16;

    private PasswordUtil() {}

    /** Genera un salt aleatorio en hexadecimal. */
    public static String generarSalt() {
        byte[] salt = new byte[SALT_BYTES];
        new SecureRandom().nextBytes(salt);
        return bytesAHex(salt);
    }

    /** Genera el hash SHA-256 de (password + salt). */
    public static String hashPassword(String password, String salt) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            md.update((password + salt).getBytes("UTF-8"));
            return bytesAHex(md.digest());
        } catch (Exception e) {
            throw new RuntimeException("Error al generar hash de contraseña", e);
        }
    }

    /** Verifica si la contraseña coincide con el hash almacenado. */
    public static boolean verificar(String password, String salt, String hashAlmacenado) {
        if (password == null || salt == null || hashAlmacenado == null) return false;
        String hash = hashPassword(password, salt);
        return hash.equalsIgnoreCase(hashAlmacenado);
    }

    private static String bytesAHex(byte[] bytes) {
        StringBuilder sb = new StringBuilder(bytes.length * 2);
        for (byte b : bytes) {
            sb.append(String.format("%02x", b));
        }
        return sb.toString();
    }
}
