package com.tamalon.app.service;

import android.content.Context;
import android.util.Log;
import androidx.annotation.NonNull;
import com.tamalon.app.data.local.AppDatabase;
import com.tamalon.app.data.local.UsuarioDao;
import com.tamalon.app.model.Usuario;
import com.tamalon.app.network.*;
import com.tamalon.app.validation.*;
import java.util.TimeZone;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import retrofit2.Call;
import retrofit2.Response;

/**
 * Servicio de autenticación conectado al backend REST (Render).
 * Mantiene la interfaz pública y el Callback<T> para compatibilidad con las pantallas.
 */
public class AutenticacionService {

    private static final String TAG = "AutenticacionService";

    private final Context context;
    private final AppDatabase db;
    private final TokenStore tokenStore;
    private final ApiService apiService;
    private final ExecutorService executor;

    public interface Callback<T> {
        void onExito(T resultado);
        void onError(String mensaje);
    }

    public AutenticacionService(Context context) {
        this.context = context.getApplicationContext();
        this.db = AppDatabase.getInstance(this.context);
        this.tokenStore = new TokenStore(this.context);
        this.apiService = ApiClient.getApiService(this.context);
        this.executor = Executors.newSingleThreadExecutor();
    }

    private String resolverResId(String resIdNombre) {
        if (resIdNombre == null) return "";
        int id = context.getResources().getIdentifier(resIdNombre, "string", context.getPackageName());
        return id != 0 ? context.getString(id) : resIdNombre;
    }

    public void registrar(String nombre, String correo, String password,
                          String confirmacion, String rol, boolean terminos,
                          Callback<Usuario> callback) {
        ResultadoValidacion r;
        r = ValidadorRegistro.validarNombre(nombre);
        if (!r.esValido()) { callback.onError(resolverResId(r.getMensajeErrorResId())); return; }
        r = ValidadorRegistro.validarCorreo(correo);
        if (!r.esValido()) { callback.onError(resolverResId(r.getMensajeErrorResId())); return; }
        r = ValidadorRegistro.validarPassword(password);
        if (!r.esValido()) { callback.onError(resolverResId(r.getMensajeErrorResId())); return; }
        r = ValidadorRegistro.validarConfirmacion(password, confirmacion);
        if (!r.esValido()) { callback.onError(resolverResId(r.getMensajeErrorResId())); return; }
        r = ValidadorRegistro.validarRol(rol);
        if (!r.esValido()) { callback.onError(resolverResId(r.getMensajeErrorResId())); return; }
        r = ValidadorRegistro.validarTerminos(terminos);
        if (!r.esValido()) { callback.onError(resolverResId(r.getMensajeErrorResId())); return; }

        String correoNorm = correo.trim().toLowerCase();
        RegisterRequest req = new RegisterRequest(nombre.trim(), correoNorm, password, confirmacion);

        Call<RegisterResponse> call;
        if ("vendedor".equalsIgnoreCase(rol)) {
            call = apiService.registroVendedor(req);
        } else {
            call = apiService.registroCliente(req);
        }

        call.enqueue(new retrofit2.Callback<RegisterResponse>() {
            @Override
            public void onResponse(@NonNull Call<RegisterResponse> call, @NonNull Response<RegisterResponse> response) {
                if (response.isSuccessful() && response.body() != null) {
                    iniciarSesion(correoNorm, password, callback);
                } else {
                    String err = ApiErrorHelper.parseError(context, null, response);
                    callback.onError(err);
                }
            }

            @Override
            public void onFailure(@NonNull Call<RegisterResponse> call, @NonNull Throwable t) {
                String err = ApiErrorHelper.parseError(context, t, null);
                callback.onError(err);
            }
        });
    }

    public void iniciarSesion(String correo, String password, Callback<Usuario> callback) {
        ResultadoValidacion r;
        r = ValidadorLogin.validarCorreo(correo);
        if (!r.esValido()) { callback.onError(resolverResId(r.getMensajeErrorResId())); return; }
        r = ValidadorLogin.validarPassword(password);
        if (!r.esValido()) { callback.onError(resolverResId(r.getMensajeErrorResId())); return; }

        String correoNorm = correo.trim().toLowerCase();
        String zonaHoraria = TimeZone.getDefault().getID();
        LoginRequest req = new LoginRequest(correoNorm, password, zonaHoraria);

        apiService.login(req).enqueue(new retrofit2.Callback<LoginResponse>() {
            @Override
            public void onResponse(@NonNull Call<LoginResponse> call, @NonNull Response<LoginResponse> response) {
                if (response.isSuccessful() && response.body() != null) {
                    LoginResponse lr = response.body();
                    tokenStore.saveSession(lr.token, lr.expira, lr.usuario);

                    Usuario localUser = dtoToLocalUsuario(lr.usuario);
                    executor.execute(() -> {
                        try {
                            UsuarioDao dao = db.usuarioDao();
                            Usuario existing = dao.obtenerPorCorreo(correoNorm);
                            if (existing != null) {
                                localUser.id = existing.id;
                                dao.actualizar(localUser);
                            } else {
                                dao.insertar(localUser);
                            }
                        } catch (Exception e) {
                            Log.e(TAG, "Error syncing user to Room", e);
                        }
                    });

                    callback.onExito(localUser);
                } else {
                    String err = ApiErrorHelper.parseError(context, null, response);
                    callback.onError(err);
                }
            }

            @Override
            public void onFailure(@NonNull Call<LoginResponse> call, @NonNull Throwable t) {
                String err = ApiErrorHelper.parseError(context, t, null);
                callback.onError(err);
            }
        });
    }

    public void cerrarSesion() {
        try {
            apiService.logout().enqueue(new retrofit2.Callback<Void>() {
                @Override public void onResponse(@NonNull Call<Void> call, @NonNull Response<Void> response) {}
                @Override public void onFailure(@NonNull Call<Void> call, @NonNull Throwable t) {}
            });
        } catch (Exception e) {
            // ignore
        }
        tokenStore.clearSession();
    }

    public boolean haySesionActiva() {
        return tokenStore.isSessionActive();
    }

    public void obtenerUsuarioActivo(Callback<Usuario> callback) {
        if (!haySesionActiva()) {
            callback.onError("No hay sesión activa");
            return;
        }
        DtoUser dto = tokenStore.getUser();
        if (dto == null) {
            callback.onError("Usuario no encontrado en sesión");
            return;
        }
        Usuario u = dtoToLocalUsuario(dto);
        callback.onExito(u);
    }

    public static Usuario dtoToLocalUsuario(DtoUser dto) {
        if (dto == null) return null;
        Usuario u = new Usuario();
        u.id = dto.idUsuario;
        u.correo = dto.correo != null ? dto.correo.trim().toLowerCase() : "";
        u.nombreCompleto = dto.nombreCompleto != null ? dto.nombreCompleto.trim() : "";
        u.telefono = dto.telefono;
        u.fotoUri = dto.fotoPerfil;

        String roleLower = dto.rol != null ? dto.rol.toLowerCase() : "";
        if (roleLower.contains("vendedor")) {
            u.rol = "vendedor";
        } else if (roleLower.contains("cliente")) {
            u.rol = "cliente";
        } else {
            u.rol = "cliente";
        }

        String planLower = dto.plan != null ? dto.plan.toLowerCase() : "";
        if (roleLower.contains("premium") || planLower.contains("premium")) {
            u.plan = "premium";
        } else {
            u.plan = "gratuito";
        }
        return u;
    }
}
