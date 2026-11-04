package com.tamalon.app.model;

import androidx.room.Entity;
import androidx.room.PrimaryKey;
import androidx.room.ColumnInfo;
import androidx.annotation.NonNull;

/**
 * Entidad Room que representa una cuenta de usuario local.
 * Las contraseñas se almacenan como hash SHA-256 + salt.
 * NOTA: Este mecanismo es adecuado para prototipos locales.
 * La autenticación definitiva debe delegarse al servidor (Supabase).
 */
@Entity(tableName = "usuarios")
public class Usuario {

    @PrimaryKey(autoGenerate = true)
    public int id;

    @NonNull
    @ColumnInfo(name = "correo")
    public String correo;          // max 100, normalizado a minúsculas

    @NonNull
    @ColumnInfo(name = "nombre_completo")
    public String nombreCompleto;  // 2–100 caracteres

    @NonNull
    @ColumnInfo(name = "password_hash")
    public String passwordHash;    // SHA-256(password + salt) en hexadecimal

    @NonNull
    @ColumnInfo(name = "password_salt")
    public String passwordSalt;    // 16 bytes aleatorios en hexadecimal

    @NonNull
    @ColumnInfo(name = "rol")
    public String rol;             // "cliente" | "vendedor"

    @ColumnInfo(name = "telefono")
    public String telefono;        // 10 dígitos, nullable hasta que se complete el perfil

    @ColumnInfo(name = "foto_uri")
    public String fotoUri;         // URI/ruta local ahora; URL HTTPS del almacenamiento remoto en la futura integración

    @ColumnInfo(name = "fecha_registro")
    public long fechaRegistro;     // epoch millis

    @ColumnInfo(name = "plan")
    public String plan;            // "gratuito" | "premium"

    // Integración futura: supabase_uid se poblará al sincronizar con el backend
    @ColumnInfo(name = "supabase_uid")
    public String supabaseUid;

    // Ubicación y disponibilidad del vendedor. Las coordenadas son nulas hasta compartir ubicación.
    @ColumnInfo(name = "ubicacion_latitud")
    public Double ubicacionLatitud;

    @ColumnInfo(name = "ubicacion_longitud")
    public Double ubicacionLongitud;

    @ColumnInfo(name = "disponible_en_ruta", defaultValue = "0")
    public boolean disponibleEnRuta;

    @ColumnInfo(name = "ultima_ubicacion", defaultValue = "0")
    public long ultimaUbicacion;

    public Usuario() {}

    public Usuario(@NonNull String correo,
                   @NonNull String nombreCompleto,
                   @NonNull String passwordHash,
                   @NonNull String passwordSalt,
                   @NonNull String rol) {
        this.correo = correo.trim().toLowerCase();
        this.nombreCompleto = nombreCompleto.trim();
        this.passwordHash = passwordHash;
        this.passwordSalt = passwordSalt;
        this.rol = rol;
        this.plan = "gratuito";
        this.fechaRegistro = System.currentTimeMillis();
    }

    public boolean esVendedor() {
        return "vendedor".equals(rol);
    }

    public boolean esCliente() {
        return "cliente".equals(rol);
    }

    public boolean esPremium() {
        return "premium".equals(plan);
    }

    public boolean esPremiumReal() {
        return "premium".equals(plan);
    }

}
