package com.tamalon.app.ui;

import android.os.Bundle;
import android.view.View;
import android.widget.*;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import com.tamalon.app.R;
import com.tamalon.app.network.*;
import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

/**
 * Pantalla de recuperación de contraseña conectada al backend (`POST /api/auth/recuperar`).
 */
public class RecuperacionActivity extends AppCompatActivity {

    private EditText etCorreo;
    private Button btnSolicitar, btnVolver;
    private TextView tvMensaje;
    private ApiService apiService;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_recover_password);

        apiService = ApiClient.getApiService(this);

        etCorreo     = findViewById(R.id.recover_email);
        btnSolicitar = findViewById(R.id.recover_submit);
        btnVolver = findViewById(R.id.recover_btn_back);
        tvMensaje = findViewById(R.id.recover_status);
        tvMensaje.setVisibility(View.GONE);

        btnSolicitar.setOnClickListener(v -> intentarRecuperacion());
        btnVolver.setOnClickListener(v -> finish());
    }

    private void intentarRecuperacion() {
        String correo = etCorreo.getText().toString().trim();
        if (correo.isEmpty()) {
            tvMensaje.setText("Ingresa tu correo electrónico.");
            tvMensaje.setVisibility(View.VISIBLE);
            return;
        }

        btnSolicitar.setEnabled(false);
        tvMensaje.setVisibility(View.GONE);

        RecuperarRequest req = new RecuperarRequest(correo);
        apiService.recuperar(req).enqueue(new Callback<RecuperarResponse>() {
            @Override
            public void onResponse(@NonNull Call<RecuperarResponse> call, @NonNull Response<RecuperarResponse> response) {
                btnSolicitar.setEnabled(true);
                if (response.isSuccessful() && response.body() != null) {
                    String mensaje = response.body().mensaje;
                    tvMensaje.setText(mensaje != null ? mensaje : "Se han enviado las instrucciones a tu correo.");
                    tvMensaje.setVisibility(View.VISIBLE);
                } else {
                    String err = ApiErrorHelper.parseError(RecuperacionActivity.this, null, response);
                    tvMensaje.setText(err);
                    tvMensaje.setVisibility(View.VISIBLE);
                }
            }

            @Override
            public void onFailure(@NonNull Call<RecuperarResponse> call, @NonNull Throwable t) {
                btnSolicitar.setEnabled(true);
                String err = ApiErrorHelper.parseError(RecuperacionActivity.this, t, null);
                tvMensaje.setText(err);
                tvMensaje.setVisibility(View.VISIBLE);
            }
        });
    }
}
