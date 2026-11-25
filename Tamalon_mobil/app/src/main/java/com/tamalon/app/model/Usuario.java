package com.tamalon.app.model;

import androidx.room.Entity;
import androidx.room.PrimaryKey;
import androidx.room.ColumnInfo;
import androidx.room.Ignore;
import androidx.annotation.NonNull;

@Entity(tableName = "usuarios")
public class Usuario {

    @PrimaryKey(autoGenerate = true)
    public int id;

    @NonNull
    @ColumnInfo(name = "correo")
    public String correo;

    @NonNull
    @ColumnInfo(name = "nombre_completo")
    public String nombreCompleto;

    @NonNull
    @ColumnInfo(name = "password_hash")
    public String passwordHash;

    @NonNull
    @ColumnInfo(name = "password_salt")
    public String passwordSalt;

    @NonNull
    @ColumnInfo(name = "rol")
    public String rol;

    @ColumnInfo(name = "es_premium")
    public boolean esPremiumField;

    @ColumnInfo(name = "telefono")
    public String telefono;

    @ColumnInfo(name = "foto_uri")
    public String fotoUri;

    @ColumnInfo(name = "fecha_registro")
    public long fechaRegistro;

    @Ignore
    public String plan;

    @ColumnInfo(name = "supabase_uid")
    public String supabaseUid;

    @Ignore
    public Double ubicacionLatitud;

    @Ignore
    public Double ubicacionLongitud;

    @Ignore
    public boolean disponibleEnRuta;

    @Ignore
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
        this.esPremiumField = false;
        this.fechaRegistro = System.currentTimeMillis();
    }

    public boolean esVendedor() {
        return "vendedor".equalsIgnoreCase(rol) || "Vendedor".equalsIgnoreCase(rol);
    }

    public boolean esCliente() {
        return "cliente".equalsIgnoreCase(rol) || "Cliente".equalsIgnoreCase(rol);
    }

    public boolean esAdministrador() {
        return "administrador".equalsIgnoreCase(rol) || "Administrador".equalsIgnoreCase(rol);
    }

    public boolean esPremium() {
        if (esCliente()) return false;
        return "premium".equalsIgnoreCase(plan) || esPremiumField;
    }

    public boolean esPremiumReal() {
        if (esCliente()) return false;
        return "premium".equalsIgnoreCase(plan) || esPremiumField;
    }
}
