package com.tamalon.app.service;

import android.content.Context;
import android.content.SharedPreferences;
import com.tamalon.app.data.local.AppDatabase;
import com.tamalon.app.data.local.UsuarioDao;
import com.tamalon.app.model.Suscripcion;
import com.tamalon.app.model.Usuario;
import com.tamalon.app.util.PasswordUtil;
import com.tamalon.app.validation.*;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Servicio de autenticación local.
 * Gestiona registro, inicio de sesión, sesión activa y cierre de sesión.
 *
 * La sesión se persiste en SharedPreferences (solo el ID del usuario autenticado).
 * Las operaciones de base de datos se ejecutan en un hilo de fondo para no bloquear la UI.
 *
 * Integración futura: reemplazar el hash local por Supabase Auth.
 * El método registrar() deberá llamar a supabase.auth.signUp() y guardar el supabase_uid.
 */
public class AutenticacionService {

    private static final String PREFS_NAME     = "tamalon_session";
    private static final String KEY_USER_ID    = "usuario_id_activo";
    private static final int    SIN_SESION     = -1;

    private final AppDatabase db;
    private final SharedPreferences prefs;
    private final ExecutorService executor;

    public interface Callback<T> {
        void onExito(T resultado);
        void onError(String mensajeResId);
    }

    public AutenticacionService(Context context) {
        this.db       = AppDatabase.getInstance(context);
        this.prefs    = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        this.executor = Executors.newSingleThreadExecutor();
    }

    /** Registra un nuevo usuario y crea su suscripción gratuita. Sin usuario predeterminado. */
    public void registrar(String nombre, String correo, String password,
                          String confirmacion, String rol, boolean terminos,
                          Callback<Usuario> callback) {
        // Validaciones síncronas antes de tocar la BD
        ResultadoValidacion r;
        r = ValidadorRegistro.validarNombre(nombre);
        if (!r.esValido()) { callback.onError(r.getMensajeErrorResId()); return; }
        r = ValidadorRegistro.validarCorreo(correo);
        if (!r.esValido()) { callback.onError(r.getMensajeErrorResId()); return; }
        r = ValidadorRegistro.validarPassword(password);
        if (!r.esValido()) { callback.onError(r.getMensajeErrorResId()); return; }
        r = ValidadorRegistro.validarConfirmacion(password, confirmacion);
        if (!r.esValido()) { callback.onError(r.getMensajeErrorResId()); return; }
        r = ValidadorRegistro.validarRol(rol);
        if (!r.esValido()) { callback.onError(r.getMensajeErrorResId()); return; }
        r = ValidadorRegistro.validarTerminos(terminos);
        if (!r.esValido()) { callback.onError(r.getMensajeErrorResId()); return; }

        String correoNorm = correo.trim().toLowerCase();

        executor.execute(() -> {
            UsuarioDao dao = db.usuarioDao();
            int count = dao.contarPorCorreo(correoNorm);
            ResultadoValidacion unicidad = ValidadorRegistro.validarCorreoUnico(count);
            if (!unicidad.esValido()) {
                callback.onError(unicidad.getMensajeErrorResId());
                return;
            }
            String salt = PasswordUtil.generarSalt();
            String hash = PasswordUtil.hashPassword(password, salt);
            Usuario nuevo = new Usuario(correoNorm, nombre, hash, salt, rol);
            long id = dao.insertar(nuevo);
            nuevo.id = (int) id;
            // Crear suscripción gratuita
            Suscripcion sus = new Suscripcion(nuevo.id);
            db.suscripcionDao().insertar(sus);
            // Iniciar sesión automáticamente tras el registro
            guardarSesion(nuevo.id);
            callback.onExito(nuevo);
        });
    }

    /** Inicia sesión con correo y contraseña. */
    public void iniciarSesion(String correo, String password, Callback<Usuario> callback) {
        ResultadoValidacion r;
        r = ValidadorLogin.validarCorreo(correo);
        if (!r.esValido()) { callback.onError(r.getMensajeErrorResId()); return; }
        r = ValidadorLogin.validarPassword(password);
        if (!r.esValido()) { callback.onError(r.getMensajeErrorResId()); return; }

        String correoNorm = correo.trim().toLowerCase();
        executor.execute(() -> {
            Usuario usuario = db.usuarioDao().obtenerPorCorreo(correoNorm);
            ResultadoValidacion existe = ValidadorLogin.validarExistencia(usuario);
            if (!existe.esValido()) {
                callback.onError(existe.getMensajeErrorResId());
                return;
            }
            boolean coincide = PasswordUtil.verificar(password, usuario.passwordSalt, usuario.passwordHash);
            ResultadoValidacion credenciales = ValidadorLogin.validarCredenciales(coincide);
            if (!credenciales.esValido()) {
                callback.onError(credenciales.getMensajeErrorResId());
                return;
            }
            normalizarPlanAnterior(usuario);
            guardarSesion(usuario.id);
            callback.onExito(usuario);
        });
    }

    /** Cierra la sesión activa. */
    public void cerrarSesion() {
        prefs.edit().remove(KEY_USER_ID).apply();
    }

    /** Devuelve el ID del usuario activo, o SIN_SESION si no hay sesión. */
    public int obtenerIdSesionActiva() {
        return prefs.getInt(KEY_USER_ID, SIN_SESION);
    }

    public boolean haySesionActiva() {
        return obtenerIdSesionActiva() != SIN_SESION;
    }

    /** Carga el usuario de la sesión activa en segundo plano. */
    public void obtenerUsuarioActivo(Callback<Usuario> callback) {
        int id = obtenerIdSesionActiva();
        if (id == SIN_SESION) { callback.onError("validation_unexpected_error"); return; }
        executor.execute(() -> {
            Usuario u = db.usuarioDao().obtenerPorId(id);
            if (u == null) { callback.onError("validation_account_not_found"); return; }
            normalizarPlanAnterior(u);
            callback.onExito(u);
        });
    }

    /** Revoca el antiguo plan de prueba para que no conserve permisos Premium. */
    private void normalizarPlanAnterior(Usuario usuario) {
        if (usuario != null && "premium_demo".equals(usuario.plan)) {
            usuario.plan = "gratuito";
            db.usuarioDao().actualizarPlan(usuario.id, "gratuito");
            db.suscripcionDao().actualizarPlan(usuario.id, Suscripcion.PLAN_GRATUITO,
                    Suscripcion.ESTADO_GRATUITO, System.currentTimeMillis());
        }
    }

    private void guardarSesion(int usuarioId) {
        prefs.edit().putInt(KEY_USER_ID, usuarioId).apply();
    }
}
