package com.tamalon.app.ui;

import android.content.Intent;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.View;
import android.widget.*;
import androidx.appcompat.app.AppCompatActivity;
import com.tamalon.app.R;
import com.tamalon.app.repository.ReporteRepository;
import com.tamalon.app.service.AutenticacionService;

/**
 * RF23.1 — Vendedor reporta a Cliente.
 * RF23.2 — Cliente reporta a Vendedor.
 * Disponible para cuentas Gratuitas y Premium; no es función exclusiva Premium.
 */
public class ReporteActivity extends AppCompatActivity {

    private static final int MOTIVO_MAX = 500;

    private TextView tvReportadoNombre, tvContador;
    private EditText etMotivo;
    private TextView tvEstado;
    private Button btnEnviar, btnCancelar;

    private int reportadoId;
    private String reportadoRol;
    private int emisorId;
    private String emisorRol;

    private ReporteRepository reporteRepo;
    private AutenticacionService authService;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_report);

        reporteRepo  = new ReporteRepository(this);
        authService  = new AutenticacionService(this);

        // Los IDs vienen de la Activity anterior (nunca los escribe el usuario)
        reportadoId  = getIntent().getIntExtra("reportado_id", -1);
        reportadoRol = getIntent().getStringExtra("reportado_rol");   // "cliente" | "vendedor"

        tvReportadoNombre = findViewById(R.id.report_target_name);
        tvContador        = findViewById(R.id.report_char_counter);
        etMotivo          = findViewById(R.id.report_reason);
        tvEstado          = findViewById(R.id.report_status);
        btnEnviar         = findViewById(R.id.report_submit);
        btnCancelar       = findViewById(R.id.report_cancel);

        String label = "vendedor".equals(reportadoRol)
                ? getString(R.string.badge_vendor)
                : getString(R.string.badge_client);
        tvReportadoNombre.setText(label + " — cargando cuenta…");
        if (reportadoId > 0) new Thread(() -> {
            com.tamalon.app.model.Usuario reportado = com.tamalon.app.data.local.AppDatabase.getInstance(this).usuarioDao().obtenerPorId(reportadoId);
            runOnUiThread(() -> tvReportadoNombre.setText(label + " — " + (reportado == null ? "Cuenta no encontrada" : reportado.nombreCompleto)));
        }).start();

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
                emisorId  = usuario.id;
                emisorRol = usuario.rol;
            }
            @Override public void onError(String mensajeResId) {
                finish(); // Sin sesión activa, no se puede reportar
            }
        });
    }

    private void enviarReporte() {
        String motivo = etMotivo.getText().toString().trim();
        mostrarEstado(getString(R.string.report_sending), false);
        btnEnviar.setEnabled(false);

        reporteRepo.enviarReporte(emisorId, emisorRol, reportadoId, reportadoRol, motivo,
                new ReporteRepository.Callback() {
                    @Override public void onExito() {
                        runOnUiThread(() -> {
                            mostrarEstado(getString(R.string.report_success), true);
                            etMotivo.setEnabled(false);
                            btnCancelar.setText(R.string.btn_back);
                        });
                    }
                    @Override public void onError(String mensajeResId) {
                        runOnUiThread(() -> {
                            mostrarEstado(resolverString(mensajeResId), false);
                            btnEnviar.setEnabled(true);
                        });
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

    private String resolverString(String resIdNombre) {
        int id = getResources().getIdentifier(resIdNombre, "string", getPackageName());
        return id != 0 ? getString(id) : resIdNombre;
    }
}
