package com.tamalon.app.ui;

import android.os.Bundle;
import android.view.View;
import android.widget.*;
import androidx.appcompat.app.AppCompatActivity;
import com.tamalon.app.R;

/**
 * Pantalla de recuperación de contraseña.
 *
 * ESTADO ACTUAL: Solo interfaz. La recuperación real requiere:
 *   1. Backend (Supabase Auth) para enviar el enlace de restablecimiento.
 *   2. Una Cloud Function que genere un token de un solo uso con vigencia de 30 min.
 *   3. Una pantalla de "Nueva contraseña" que reciba el token desde el deep link.
 *
 * En esta versión local no se envía correo ni se restablecen contraseñas.
 */
public class RecuperacionActivity extends AppCompatActivity {

    private EditText etCorreo;
    private Button btnSolicitar, btnVolver;
    private TextView tvMensaje;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_recover_password);

        etCorreo     = findViewById(R.id.recover_email);
        btnSolicitar = findViewById(R.id.recover_submit);
        btnVolver = findViewById(R.id.recover_btn_back);
        tvMensaje = findViewById(R.id.recover_status);
        tvMensaje.setText(R.string.recover_pending_note);
        tvMensaje.setVisibility(View.VISIBLE);

        btnSolicitar.setOnClickListener(v -> {
            // Punto de integración futura: llamar a Supabase Auth resetPasswordForEmail()
            Toast.makeText(this, getString(R.string.recover_pending_note), Toast.LENGTH_LONG).show();
        });
        btnVolver.setOnClickListener(v -> finish());
    }
}
