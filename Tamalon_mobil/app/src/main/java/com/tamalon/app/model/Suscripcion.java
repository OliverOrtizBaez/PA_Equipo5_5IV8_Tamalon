package com.tamalon.app.model;

import androidx.room.Entity;
import androidx.room.PrimaryKey;
import androidx.room.ColumnInfo;

/**
 * Registro local del estado de la suscripción de un usuario.
 * En esta versión solo persiste el resultado de la operación de prueba.
 * La suscripción definitiva debe verificarse con el backend y el proveedor de pagos.
 */
@Entity(tableName = "suscripciones")
public class Suscripcion {

    public static final String PLAN_GRATUITO         = "gratuito";
    public static final String PLAN_PREMIUM          = "premium";

    public static final String ESTADO_SIN_SUSCRIPCION = "sin_suscripcion";
    public static final String ESTADO_GRATUITO         = "gratuito";
    public static final String ESTADO_EN_PROCESO       = "en_proceso";
    public static final String ESTADO_APROBADO_SANDBOX = "aprobado_sandbox";
    public static final String ESTADO_RECHAZADO        = "rechazado";
    public static final String ESTADO_CANCELADO        = "cancelado";

    @PrimaryKey(autoGenerate = true)
    public int id;

    @ColumnInfo(name = "usuario_id")
    public int usuarioId;

    @ColumnInfo(name = "plan")
    public String plan;

    @ColumnInfo(name = "estado_operacion")
    public String estadoOperacion;

    // ID de la operación devuelto por el proveedor de pagos en Sandbox
    @ColumnInfo(name = "operacion_id_sandbox")
    public String operacionIdSandbox;

    @ColumnInfo(name = "fecha_actualizacion")
    public long fechaActualizacion;

    // Integración futura: ID de suscripción en Supabase / proveedor de pagos
    @ColumnInfo(name = "proveedor_suscripcion_id")
    public String proveedorSuscripcionId;

    public Suscripcion() {}

    public Suscripcion(int usuarioId) {
        this.usuarioId = usuarioId;
        this.plan = PLAN_GRATUITO;
        this.estadoOperacion = ESTADO_GRATUITO;
        this.fechaActualizacion = System.currentTimeMillis();
    }

    public boolean esPremium() {
        return PLAN_PREMIUM.equals(plan);
    }
}
