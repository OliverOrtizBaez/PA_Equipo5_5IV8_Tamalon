package com.tamalon.app.ui;

import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.util.Log;
import android.view.View;
import android.widget.*;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import com.tamalon.app.BuildConfig;
import com.tamalon.app.R;
import com.tamalon.app.network.*;
import com.tamalon.app.service.AutenticacionService;
import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

/**
 * Pantalla de reporte conectada a `POST /api/soporte/reportes`.
 */
public class ReporteActivity extends AppCompatActivity {

    private static final String TAG = "ReporteActivity";
    private static final int MOTIVO_MAX = 500;
    private static boolean loggedReporte = false;

    private TextView tvReportadoNombre, tvContador;
    private EditText etMotivo;
    private TextView tvEstado;
    private Button btnEnviar, btnCancelar;

    private int reportadoId;
    private String reportadoRol;

    private ApiService apiService;
    private AutenticacionService authService;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_report);

        apiService   = ApiClient.getApiService(this);
        authService  = new AutenticacionService(this);

        reportadoId  = getIntent().getIntExtra("reportado_id", -1);
        reportadoRol = getIntent().getStringExtra("reportado_rol");

        tvReportadoNombre = findViewById(R.id.report_target_name);
        tvContador        = findViewById(R.id.report_char_counter);
        etMotivo          = findViewById(R.id.report_reason);
        tvEstado          = findViewById(R.id.report_status);
        btnEnviar         = findViewById(R.id.report_submit);
        btnCancelar       = findViewById(R.id.report_cancel);

        String label = "vendedor".equalsIgnoreCase(reportadoRol)
                ? getString(R.string.badge_vendor)
                : getString(R.string.badge_client);
        tvReportadoNombre.setText(label + " — Cuenta #" + reportadoId);

        etMotivo.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int st, int c, int a) {}
            @Override public void onTextChanged(CharSequence s, int st, int b, int c)     {}
            @Override public void afterTextChanged(Editable s) {
                int count = s.length();
                tvContador.setText(getString(R.string.report_chars_count, count));
                btnEnviar.setEnabled(count >= 1 && count <= MOTIVO_MAX);
            }
        });

        btnEnviar.setEnabled(false);
        btnEnviar.setOnClickListener(v -> enviarReporte());
        btnCancelar.setOnClickListener(v -> finish());

        authService.obtenerUsuarioActivo(new AutenticacionService.Callback<com.tamalon.app.model.Usuario>() {
            @Override public void onExito(com.tamalon.app.model.Usuario usuario) {
            }
            @Override public void onError(String mensajeResId) {
                finish();
            }
        });
    }

    private void enviarReporte() {
        String motivo = etMotivo.getText().toString().trim();
        mostrarEstado(getString(R.string.report_sending), false);
        btnEnviar.setEnabled(false);

        CrearReporteRequest req = new CrearReporteRequest(reportadoId, reportadoRol, motivo);
        apiService.enviarReporte(req).enqueue(new Callback<ReporteResponse>() {
            @Override
            public void onResponse(@NonNull Call<ReporteResponse> call, @NonNull Response<ReporteResponse> response) {
                if (response.isSuccessful() && response.body() != null) {
                    if (BuildConfig.DEBUG && !loggedReporte) {
                        loggedReporte = true;
                        Log.d(TAG, "POST /api/soporte/reportes HTTP " + response.code() + " msg=" + response.body().mensaje);
                    }
                    mostrarEstado(getString(R.string.report_success), true);
                    etMotivo.setEnabled(false);
                    btnCancelar.setText(R.string.btn_back);
                } else {
                    String err = ApiErrorHelper.parseError(ReporteActivity.this, null, response);
                    mostrarEstado(err, false);
                    btnEnviar.setEnabled(true);
                }
            }

            @Override
            public void onFailure(@NonNull Call<ReporteResponse> call, @NonNull Throwable t) {
                String err = ApiErrorHelper.parseError(ReporteActivity.this, t, null);
                mostrarEstado(err, false);
                btnEnviar.setEnabled(true);
            }
        });
    }

    private void mostrarEstado(String mensaje, boolean exito) {
        tvEstado.setText(mensaje);
        tvEstado.setVisibility(View.VISIBLE);
        tvEstado.setTextColor(exito
                ? getColor(R.color.tamalon_green)
                : getColor(R.color.tamalon_red));
    }
}
