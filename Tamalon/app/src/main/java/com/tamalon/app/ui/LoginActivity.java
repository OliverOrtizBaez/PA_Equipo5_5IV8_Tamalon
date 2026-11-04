package com.tamalon.app.ui;

import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;
import androidx.appcompat.app.AppCompatActivity;
import com.tamalon.app.R;
import com.tamalon.app.model.Usuario;
import com.tamalon.app.service.AutenticacionService;

/**
 * Pantalla de inicio de sesión.
 * No inicia sesión automáticamente con ningún usuario predeterminado.
 * Si ya hay una sesión activa, MainActivity redirige aquí o al mapa directamente.
 */
public class LoginActivity extends AppCompatActivity {

    private EditText etCorreo, etPassword;
    private TextView tvError;
    private Button btnEntrar, btnRegistrar, btnOlvide;
    private AutenticacionService authService;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_login);

        authService = new AutenticacionService(this);

        etCorreo   = findViewById(R.id.login_email);
        etPassword = findViewById(R.id.login_password);
        tvError    = findViewById(R.id.login_error);
        btnEntrar    = findViewById(R.id.login_submit);
        btnRegistrar = findViewById(R.id.show_register);
        btnOlvide    = findViewById(R.id.login_forgot);

        btnEntrar.setOnClickListener(v -> intentarLogin());
        btnRegistrar.setOnClickListener(v -> abrirRegistro());
        btnOlvide.setOnClickListener(v -> abrirRecuperacion());
    }

    private void intentarLogin() {
        String correo    = etCorreo.getText().toString();
        String password  = etPassword.getText().toString();

        mostrarCargando(true);
        ocultarError();

        authService.iniciarSesion(correo, password, new AutenticacionService.Callback<Usuario>() {
            @Override public void onExito(Usuario usuario) {
                runOnUiThread(() -> {
                    mostrarCargando(false);
                    abrirMapa(usuario);
                });
            }
            @Override public void onError(String mensajeResId) {
                runOnUiThread(() -> {
                    mostrarCargando(false);
                    mostrarError(getString(getResources().getIdentifier(
                            mensajeResId, "string", getPackageName())));
                });
            }
        });
    }

    private void abrirMapa(Usuario usuario) {
        Intent intent = new Intent(this, MapaActivity.class);
        intent.putExtra("usuario_id", usuario.id);
        intent.putExtra("rol", usuario.rol);
        intent.putExtra("plan", usuario.plan);
        intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_NEW_TASK);
        startActivity(intent);
        finish();
    }

    private void abrirRegistro() {
        startActivity(new Intent(this, RegistroActivity.class));
    }

    private void abrirRecuperacion() {
        startActivity(new Intent(this, RecuperacionActivity.class));
    }

    private void mostrarCargando(boolean cargando) {
        btnEntrar.setEnabled(!cargando);
        btnEntrar.setText(cargando
                ? getString(R.string.login_loading)
                : getString(R.string.login_button));
    }

    private void mostrarError(String mensaje) {
        tvError.setText(mensaje);
        tvError.setVisibility(View.VISIBLE);
    }

    private void ocultarError() {
        tvError.setVisibility(View.GONE);
    }
}
