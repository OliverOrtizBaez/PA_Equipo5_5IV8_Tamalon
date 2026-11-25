package com.tamalon.app.ui;

import android.os.Bundle;
import android.view.View;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.Toast;
import android.widget.ImageView;
import android.net.Uri;
import android.widget.ArrayAdapter;
import android.app.AlertDialog;
import android.content.Intent;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import com.tamalon.app.R;
import com.tamalon.app.data.local.AppDatabase;
import com.tamalon.app.model.Usuario;
import com.tamalon.app.network.*;
import com.tamalon.app.service.AutenticacionService;
import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

/** Presenta las subsecciones de perfil conectadas a la API REST. */
public class PerfilSeccionActivity extends AppCompatActivity {
    private final ExecutorService executor = Executors.newSingleThreadExecutor();
    private ApiService apiService;
    private TokenStore tokenStore;
    private AppDatabase db;
    private String seccion;
    private int usuarioId = -1;
    private Usuario usuario;
    private ImageView personalAvatar;
    private Button btnRemovePhoto;
    private EditText personalNameInput, personalPhoneInput;
    private static final int REQUEST_PROFILE_PHOTO = 704;

    @Override protected void onCreate(Bundle state) {
        super.onCreate(state);
        seccion = getIntent().getStringExtra("seccion");
        if (seccion == null) seccion = "personal";
        usuarioId = getIntent().getIntExtra("usuario_id", -1);
        int layout;
        switch (seccion) {
            case "settings": case "preferences": layout = R.layout.activity_profile_preferences; seccion = "settings"; break;
            case "support": layout = R.layout.activity_profile_support; break;
            default: layout = R.layout.activity_profile_personal; seccion = "personal"; break;
        }
        setContentView(layout);
        apiService = ApiClient.getApiService(this);
        tokenStore = new TokenStore(this);
        db = AppDatabase.getInstance(this);

        View back = findViewById(R.id.back_to_profile);
        if (back != null) back.setOnClickListener(v -> finish());
        cargarNombreEncabezado();
        if ("personal".equals(seccion)) configurarDatosPersonales();
        if ("settings".equals(seccion)) configurarConfiguracion();
        if ("support".equals(seccion)) configurarSoporte();
    }

    private void cargarNombreEncabezado() {
        android.widget.TextView headerName = findViewById(R.id.profile_header_user_name);
        if (headerName == null) return;
        new AutenticacionService(this).obtenerUsuarioActivo(new AutenticacionService.Callback<Usuario>() {
            @Override public void onExito(Usuario u) { runOnUiThread(() -> headerName.setText(u.nombreCompleto)); }
            @Override public void onError(String m) { runOnUiThread(() -> headerName.setText("Tu cuenta")); }
        });
    }

    private void configurarDatosPersonales() {
        EditText name = findViewById(R.id.personal_name), email = findViewById(R.id.personal_email), phone = findViewById(R.id.personal_phone);
        personalNameInput = name;
        personalPhoneInput = phone;
        personalAvatar = findViewById(R.id.personal_avatar);
        btnRemovePhoto = findViewById(R.id.remove_profile_photo);
        View choosePhoto = findViewById(R.id.choose_profile_photo);
        if (choosePhoto instanceof Button) ((Button) choosePhoto).setBackgroundTintList(null);
        if (choosePhoto != null) choosePhoto.setOnClickListener(v -> {
            Intent picker = new Intent(Intent.ACTION_OPEN_DOCUMENT);
            picker.addCategory(Intent.CATEGORY_OPENABLE);
            picker.setType("image/*");
            startActivityForResult(picker, REQUEST_PROFILE_PHOTO);
        });
        if (btnRemovePhoto != null) {
            btnRemovePhoto.setBackgroundTintList(null);
            btnRemovePhoto.setOnClickListener(v -> quitarFotoPerfil());
        }
        email.setEnabled(false);
        new AutenticacionService(this).obtenerUsuarioActivo(new AutenticacionService.Callback<Usuario>() {
            @Override public void onExito(Usuario u) {
                usuario = u;
                runOnUiThread(() -> {
                    name.setText(u.nombreCompleto);
                    email.setText(u.correo);
                    phone.setText(u.telefono == null ? "" : u.telefono);
                    actualizarVistaFoto(u.fotoUri);
                    sincronizarPerfilDesdeServidor(name, email, phone);
                });
            }
            @Override public void onError(String m) { runOnUiThread(() -> Toast.makeText(PerfilSeccionActivity.this, "No se pudieron cargar tus datos", Toast.LENGTH_SHORT).show()); }
        });

        findViewById(R.id.save_personal).setOnClickListener(v -> {
            String n = name.getText().toString().trim(), tel = phone.getText().toString().trim();
            if (n.length() < 2 || n.length() > 100) { name.setError("El nombre debe tener de 2 a 100 caracteres"); return; }
            if (!tel.isEmpty() && !tel.matches("[0-9]{10}")) { phone.setError("El teléfono debe contener 10 dígitos"); return; }
            if (usuario == null) { Toast.makeText(this, "Espera a que carguen tus datos", Toast.LENGTH_SHORT).show(); return; }

            enviarActualizacionPerfil(n, tel, usuario.fotoUri, true);
        });
    }

    private void sincronizarPerfilDesdeServidor(EditText name, EditText email, EditText phone) {
        apiService.obtenerPerfil().enqueue(new Callback<DtoUser>() {
            @Override
            public void onResponse(@NonNull Call<DtoUser> call, @NonNull Response<DtoUser> response) {
                if (response.isSuccessful() && response.body() != null && usuario != null) {
                    DtoUser dto = response.body();
                    tokenStore.updateUserProfile(dto);
                    if (dto.nombreCompleto != null) usuario.nombreCompleto = dto.nombreCompleto.trim();
                    usuario.telefono = dto.telefono;
                    if (dto.fotoPerfil != null && !dto.fotoPerfil.trim().isEmpty()) {
                        usuario.fotoUri = dto.fotoPerfil.trim();
                    }
                    guardarUsuarioLocal(usuario.nombreCompleto, usuario.telefono, usuario.fotoUri);
                    runOnUiThread(() -> {
                        if (name != null && usuario.nombreCompleto != null) name.setText(usuario.nombreCompleto);
                        if (email != null && dto.correo != null) email.setText(dto.correo);
                        if (phone != null) phone.setText(usuario.telefono == null ? "" : usuario.telefono);
                        actualizarVistaFoto(usuario.fotoUri);
                    });
                }
            }

            @Override
            public void onFailure(@NonNull Call<DtoUser> call, @NonNull Throwable t) {
                // Conserva caché local/TokenStore
            }
        });
    }

    private void actualizarVistaFoto(String fotoRef) {
        if (personalAvatar != null) {
            ImagenPerfil.mostrar(personalAvatar, fotoRef, PerfilSeccionActivity.this);
        }
        if (btnRemovePhoto != null) {
            boolean tieneFoto = fotoRef != null && !fotoRef.trim().isEmpty();
            btnRemovePhoto.setVisibility(tieneFoto ? View.VISIBLE : View.GONE);
        }
    }

    private void quitarFotoPerfil() {
        if (usuario == null) return;
        usuario.fotoUri = "";
        actualizarVistaFoto("");
        String n = personalNameInput != null ? personalNameInput.getText().toString().trim() : usuario.nombreCompleto;
        String tel = personalPhoneInput != null ? personalPhoneInput.getText().toString().trim() : (usuario.telefono == null ? "" : usuario.telefono);
        if (n == null || n.length() < 2) n = usuario.nombreCompleto;
        enviarActualizacionPerfil(n, tel, "", false);
    }

    private void enviarActualizacionPerfil(String nombre, String telefono, String fotoPerfil, boolean mostrarExitoGeneral) {
        String fotoPayload = fotoPerfil == null ? "" : fotoPerfil.trim();
        ActualizarPerfilRequest req = new ActualizarPerfilRequest(nombre, telefono, fotoPayload);
        apiService.actualizarPerfil(req).enqueue(new Callback<DtoUser>() {
            @Override
            public void onResponse(@NonNull Call<DtoUser> call, @NonNull Response<DtoUser> response) {
                if (response.isSuccessful()) {
                    usuario.nombreCompleto = nombre;
                    usuario.telefono = telefono;
                    usuario.fotoUri = fotoPayload;
                    DtoUser body = response.body();
                    if (body != null) {
                        if (body.fotoPerfil == null && !fotoPayload.isEmpty()) {
                            body.fotoPerfil = fotoPayload;
                        }
                        tokenStore.updateUserProfile(body);
                    } else {
                        DtoUser current = tokenStore.getUser();
                        if (current != null) {
                            current.nombreCompleto = nombre;
                            current.telefono = telefono;
                            current.fotoPerfil = fotoPayload;
                            tokenStore.saveUser(current);
                        }
                    }
                    guardarUsuarioLocal(nombre, telefono, fotoPayload);
                    String msg = mostrarExitoGeneral ? "Datos guardados en tu cuenta" : (fotoPayload.isEmpty() ? "Foto eliminada" : "Foto de perfil guardada");
                    Toast.makeText(PerfilSeccionActivity.this, msg, Toast.LENGTH_SHORT).show();
                } else {
                    String err = ApiErrorHelper.parseError(PerfilSeccionActivity.this, null, response);
                    Toast.makeText(PerfilSeccionActivity.this, err, Toast.LENGTH_LONG).show();
                }
            }

            @Override
            public void onFailure(@NonNull Call<DtoUser> call, @NonNull Throwable t) {
                String err = ApiErrorHelper.parseError(PerfilSeccionActivity.this, t, null);
                Toast.makeText(PerfilSeccionActivity.this, err, Toast.LENGTH_LONG).show();
            }
        });
    }

    private void guardarUsuarioLocal(String nombre, String telefono, String foto) {
        if (usuario == null) return;
        executor.execute(() -> {
            db.usuarioDao().actualizarPerfil(usuario.id, nombre, telefono, foto);
            if (usuario.correo != null && !usuario.correo.isEmpty()) {
                Usuario uRoom = db.usuarioDao().obtenerPorCorreo(usuario.correo);
                if (uRoom != null && uRoom.id != usuario.id) {
                    db.usuarioDao().actualizarPerfil(uRoom.id, nombre, telefono, foto);
                }
            }
        });
    }

    @Override protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode != REQUEST_PROFILE_PHOTO || resultCode != RESULT_OK || data == null || data.getData() == null) return;
        if (usuario == null) { Toast.makeText(this, "Espera a que cargue tu cuenta", Toast.LENGTH_SHORT).show(); return; }
        Uri origen = data.getData();
        executor.execute(() -> {
            try {
                String dataUri = ImagenPerfil.codificarComoDataUri(this, origen);
                runOnUiThread(() -> {
                    usuario.fotoUri = dataUri;
                    actualizarVistaFoto(dataUri);
                    String n = personalNameInput != null ? personalNameInput.getText().toString().trim() : usuario.nombreCompleto;
                    String tel = personalPhoneInput != null ? personalPhoneInput.getText().toString().trim() : (usuario.telefono == null ? "" : usuario.telefono);
                    if (n == null || n.length() < 2) n = usuario.nombreCompleto;
                    if (!tel.isEmpty() && !tel.matches("[0-9]{10}")) tel = usuario.telefono == null ? "" : usuario.telefono;
                    enviarActualizacionPerfil(n, tel, dataUri, false);
                });
            } catch (Exception e) {
                runOnUiThread(() -> Toast.makeText(PerfilSeccionActivity.this, "No se pudo procesar la foto", Toast.LENGTH_LONG).show());
            }
        });
    }

    private void configurarConfiguracion() {
        CheckBox notifications = findViewById(R.id.nearby_notifications);
        View appPermissions = findViewById(R.id.open_app_permissions);
        if (appPermissions != null) appPermissions.setOnClickListener(v -> {
            try {
                Intent intent = new Intent(android.provider.Settings.ACTION_APPLICATION_DETAILS_SETTINGS);
                intent.setData(android.net.Uri.parse("package:" + getPackageName()));
                startActivity(intent);
            } catch (Exception e) {
                Toast.makeText(this, "No se pudieron abrir los permisos del dispositivo", Toast.LENGTH_LONG).show();
            }
        });
        android.content.SharedPreferences prefs = getSharedPreferences("tamalon_preferences_" + usuarioId, MODE_PRIVATE);
        new AutenticacionService(this).obtenerUsuarioActivo(new AutenticacionService.Callback<Usuario>() {
            @Override public void onExito(Usuario u) {
                runOnUiThread(() -> {
                    notifications.setChecked(prefs.getBoolean("notificaciones_cercanas", false));
                    notifications.setEnabled(u.esPremium());
                    notifications.setText(u.esPremium() ? "Activar notificaciones de vendedores favoritos" : "Notificaciones (exclusivo Premium)");
                    notifications.setOnCheckedChangeListener((button, checked) -> {
                        if (checked && !u.esPremium()) {
                            button.setChecked(false);
                            Toast.makeText(PerfilSeccionActivity.this, "Esta función es exclusiva de cuentas Premium", Toast.LENGTH_LONG).show();
                            return;
                        }
                        prefs.edit().putBoolean("notificaciones_cercanas", checked).apply();
                    });
                });
            }
            @Override public void onError(String m) { runOnUiThread(() -> { notifications.setChecked(false); notifications.setEnabled(false); }); }
        });
    }

    private void configurarSoporte() {
        findViewById(R.id.open_privacy).setOnClickListener(v -> new AlertDialog.Builder(this)
                .setTitle("Privacidad de tus datos")
                .setMessage("Tamalón utiliza los datos de tu perfil para identificar tu cuenta y la ubicación para mostrar vendedores cercanos o actualizar la posición de un puesto cuando el vendedor inicia una ruta. Puedes revisar los permisos del dispositivo desde Perfil > Configuración.")
                .setPositiveButton("Entendido", (d,w) -> d.dismiss()).show());
        findViewById(R.id.open_terms).setOnClickListener(v -> new AlertDialog.Builder(this)
                .setTitle("Términos y condiciones")
                .setMessage("Usa Tamalón de forma respetuosa y proporciona información veraz sobre tu cuenta y puesto. No publiques contenido engañoso ni uses la aplicación para acosar o perjudicar a otras personas.")
                .setPositiveButton("Entendido", (d,w) -> d.dismiss()).show());
        findViewById(R.id.open_help).setOnClickListener(v -> new AlertDialog.Builder(this)
                .setTitle("¿Cómo reportar una cuenta?")
                .setMessage("Pulsa “Reportar cliente o vendedor”, selecciona la cuenta y escribe un motivo de entre 1 y 500 caracteres. Los reportes están disponibles para cuentas gratuitas y Premium.")
                .setPositiveButton("Entendido", (d,w) -> d.dismiss()).show());
        findViewById(R.id.report_account).setOnClickListener(v -> elegirCuentaParaReportar());
    }

    private void elegirCuentaParaReportar() {
        new AutenticacionService(this).obtenerUsuarioActivo(new AutenticacionService.Callback<Usuario>() {
            @Override public void onExito(Usuario actual) {
                String rolObjetivo = actual.esVendedor() ? "cliente" : "vendedor";
                apiService.obtenerVendedores(null, null, 1, 50).enqueue(new Callback<VendedoresListaResponse>() {
                    @Override
                    public void onResponse(@NonNull Call<VendedoresListaResponse> call, @NonNull Response<VendedoresListaResponse> response) {
                        if (response.isSuccessful() && response.body() != null && response.body().vendedores != null) {
                            List<VendedorListaDto> filtradas = response.body().vendedores;
                            if (filtradas.isEmpty()) {
                                Toast.makeText(PerfilSeccionActivity.this, "No hay cuentas disponibles para reportar", Toast.LENGTH_SHORT).show();
                                return;
                            }
                            String[] nombres = new String[filtradas.size()];
                            for (int x = 0; x < filtradas.size(); x++) {
                                nombres[x] = filtradas.get(x).nombreCompleto;
                            }
                            new AlertDialog.Builder(PerfilSeccionActivity.this)
                                    .setTitle("Selecciona la cuenta")
                                    .setAdapter(new ArrayAdapter<>(PerfilSeccionActivity.this, android.R.layout.simple_list_item_1, nombres), (dialog, which) -> {
                                        VendedorListaDto reportado = filtradas.get(which);
                                        Intent i = new Intent(PerfilSeccionActivity.this, ReporteActivity.class);
                                        i.putExtra("reportado_id", reportado.idUsuario);
                                        i.putExtra("reportado_rol", rolObjetivo);
                                        startActivity(i);
                                    })
                                    .setNegativeButton("Cancelar", null)
                                    .show();
                        }
                    }

                    @Override
                    public void onFailure(@NonNull Call<VendedoresListaResponse> call, @NonNull Throwable t) {
                        Toast.makeText(PerfilSeccionActivity.this, "Error cargando cuentas", Toast.LENGTH_SHORT).show();
                    }
                });
            }

            @Override public void onError(String m) {
                Toast.makeText(PerfilSeccionActivity.this, "Inicia sesión para reportar una cuenta", Toast.LENGTH_SHORT).show();
            }
        });
    }
    @Override
    protected void onDestroy() {
        super.onDestroy();
        executor.shutdown();
    }
}
