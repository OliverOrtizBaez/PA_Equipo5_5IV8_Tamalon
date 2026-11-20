package com.tamalon.app.ui;

import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.widget.Button;
import android.widget.ProgressBar;
import android.widget.TextView;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import com.tamalon.app.BuildConfig;
import com.tamalon.app.R;
import com.tamalon.app.data.local.AppDatabase;
import com.tamalon.app.model.Suscripcion;
import com.tamalon.app.network.ApiClient;
import com.tamalon.app.network.ApiErrorHelper;
import com.tamalon.app.network.ApiService;
import com.tamalon.app.network.DtoUser;
import com.tamalon.app.network.MiPlanResponse;
import com.tamalon.app.network.TokenStore;
import com.tamalon.app.payment.PagoService;
import com.tamalon.app.payment.PagoServiceFactory;
import com.tamalon.app.service.AutenticacionService;
import java.util.concurrent.Executors;
import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

public class PagoActivity extends AppCompatActivity {
    private TextView tvPlanNombre, tvPrecio, tvBeneficiosList, tvEstado, tvAvisoSimulado;
    private Button btnPagar, btnRechazar, btnCancelar;
    private ProgressBar progressBar;
    private PagoService pagoService;
    private AutenticacionService authService;
    private TokenStore tokenStore;
    private ApiService apiService;
    private int usuarioId = -1;
    private final String planId = "premium";
    private String precio = "49.00";

    @Override
    protected void onCreate(Bundle state) {
        super.onCreate(state);
        setContentView(R.layout.activity_pago);

        authService = new AutenticacionService(this);
        tokenStore = new TokenStore(this);
        apiService = ApiClient.getApiService(this);

        tvPlanNombre = findViewById(R.id.pago_plan_name);
        tvPrecio = findViewById(R.id.pago_total);
        tvEstado = findViewById(R.id.pago_status);
        tvBeneficiosList = findViewById(R.id.pago_benefits);
        tvAvisoSimulado = findViewById(R.id.pago_tv_aviso_simulado);
        progressBar = findViewById(R.id.pago_progress_bar);
        btnPagar = findViewById(R.id.pago_btn_mercadopago);
        btnRechazar = findViewById(R.id.pago_btn_simular_rechazo);
        btnCancelar = findViewById(R.id.pago_btn_back);

        precio = getIntent().getStringExtra("precio");
        if (precio == null || precio.trim().isEmpty()) precio = "49.00";
        usuarioId = getIntent().getIntExtra("usuario_id", -1);

        tvPlanNombre.setText(getString(R.string.premium_label));
        tvPrecio.setText("$" + precio + " " + getString(R.string.premium_currency));
        tvBeneficiosList.setText("• " + getString(R.string.nav_catalogs) + "\n• " + getString(R.string.nav_routes) + "\n• " + getString(R.string.favorites_title));

        btnCancelar.setOnClickListener(v -> finish());
        btnPagar.setOnClickListener(v -> iniciarPagoSimulado("aprobado"));
        btnRechazar.setOnClickListener(v -> iniciarPagoSimulado("rechazado"));

        consultarMiPlan();
    }

    private void consultarMiPlan() {
        if (progressBar != null) progressBar.setVisibility(View.VISIBLE);
        btnPagar.setEnabled(false);
        if (btnRechazar != null) btnRechazar.setEnabled(false);

        apiService.obtenerMiPlan().enqueue(new Callback<MiPlanResponse>() {
            @Override
            public void onResponse(@NonNull Call<MiPlanResponse> call, @NonNull Response<MiPlanResponse> response) {
                if (isFinishing() || isDestroyed()) return;
                if (progressBar != null) progressBar.setVisibility(View.GONE);

                if (response.isSuccessful() && response.body() != null) {
                    MiPlanResponse miPlan = response.body();
                    Boolean habilitado = miPlan.pagoSimuladoHabilitado;

                    if (Boolean.FALSE.equals(habilitado)) {
                        btnPagar.setVisibility(View.GONE);
                        btnRechazar.setVisibility(View.GONE);
                        if (tvAvisoSimulado != null) tvAvisoSimulado.setVisibility(View.GONE);
                        tvEstado.setVisibility(View.VISIBLE);
                        tvEstado.setText(R.string.pago_simulado_deshabilitado_info);
                    } else {
                        if (tvAvisoSimulado != null) tvAvisoSimulado.setVisibility(View.VISIBLE);
                        btnPagar.setVisibility(View.VISIBLE);

                        if ("Premium".equalsIgnoreCase(miPlan.plan) || "premium".equalsIgnoreCase(miPlan.plan)) {
                            btnPagar.setEnabled(false);
                            btnPagar.setText("Premium activo");
                            btnRechazar.setVisibility(View.GONE);
                            tvEstado.setVisibility(View.VISIBLE);
                            tvEstado.setText("Tu cuenta ya cuenta con la suscripción Premium.");
                        } else {
                            btnPagar.setEnabled(true);
                            btnPagar.setText(R.string.btn_probar_premium_simulado);
                            if (BuildConfig.DEBUG) {
                                btnRechazar.setVisibility(View.VISIBLE);
                                btnRechazar.setEnabled(true);
                            } else {
                                btnRechazar.setVisibility(View.GONE);
                            }
                            tvEstado.setVisibility(View.GONE);
                        }
                    }
                } else if (response.code() == 401) {
                    tvEstado.setVisibility(View.VISIBLE);
                    tvEstado.setText(R.string.pago_error_401);
                    authService.cerrarSesion();
                    irALogin();
                } else {
                    tvEstado.setVisibility(View.VISIBLE);
                    tvEstado.setText(ApiErrorHelper.parseError(PagoActivity.this, null, response));
                    btnPagar.setEnabled(true);
                }
            }

            @Override
            public void onFailure(@NonNull Call<MiPlanResponse> call, @NonNull Throwable t) {
                if (isFinishing() || isDestroyed()) return;
                if (progressBar != null) progressBar.setVisibility(View.GONE);
                tvEstado.setVisibility(View.VISIBLE);
                tvEstado.setText(ApiErrorHelper.parseError(PagoActivity.this, t, null));
                btnPagar.setEnabled(true);
            }
        });
    }

    private void iniciarPagoSimulado(String resultadoDeseado) {
        // Bloqueo de doble toque
        btnPagar.setEnabled(false);
        if (btnRechazar != null) btnRechazar.setEnabled(false);
        if (progressBar != null) progressBar.setVisibility(View.VISIBLE);

        tvEstado.setVisibility(View.VISIBLE);
        tvEstado.setText(R.string.pago_procesando);

        pagoService = PagoServiceFactory.getPagoService(resultadoDeseado);
        pagoService.iniciarPago(this, planId, precio, resultado -> {
            if (isFinishing() || isDestroyed()) return;
            if (progressBar != null) progressBar.setVisibility(View.GONE);

            if (resultado.getEstado() == PagoService.EstadoPago.APROBADO) {
                tvEstado.setText(R.string.pago_exito_simulado); // "Premium activado (simulado)"
                btnPagar.setEnabled(false);
                btnPagar.setText("Premium activo");
                btnRechazar.setVisibility(View.GONE);

                actualizarEstadoLocalTrasAprobacion(resultado.getOperacionId());
            } else if (resultado.getEstado() == PagoService.EstadoPago.RECHAZADO) {
                String msg = resultado.getMensaje() != null ? resultado.getMensaje() : getString(R.string.pago_rechazado_msg);
                tvEstado.setText(msg);
                btnPagar.setEnabled(true);
                if (BuildConfig.DEBUG) btnRechazar.setEnabled(true);
            } else {
                String msg = resultado.getMensaje() != null ? resultado.getMensaje() : getString(R.string.pago_simulado_deshabilitado);
                tvEstado.setText(msg);
                if (msg.contains("expirada") || msg.contains("401")) {
                    authService.cerrarSesion();
                    irALogin();
                } else {
                    btnPagar.setEnabled(true);
                    if (BuildConfig.DEBUG) btnRechazar.setEnabled(true);
                }
            }
        });
    }

    private void actualizarEstadoLocalTrasAprobacion(String operacionId) {
        // Re-consultar mi-plan para actualizar la sesión y base de datos local
        apiService.obtenerMiPlan().enqueue(new Callback<MiPlanResponse>() {
            @Override
            public void onResponse(@NonNull Call<MiPlanResponse> call, @NonNull Response<MiPlanResponse> response) {
                if (response.isSuccessful() && response.body() != null) {
                    MiPlanResponse planResp = response.body();
                    tokenStore.updatePlanAndRol(planResp.plan, planResp.rol);
                } else {
                    tokenStore.updatePlanAndRol("premium", null);
                }
                guardarSuscripcionEnRoom(operacionId);
            }

            @Override
            public void onFailure(@NonNull Call<MiPlanResponse> call, @NonNull Throwable t) {
                tokenStore.updatePlanAndRol("premium", null);
                guardarSuscripcionEnRoom(operacionId);
            }
        });
    }

    private void guardarSuscripcionEnRoom(String operacionId) {
        Executors.newSingleThreadExecutor().execute(() -> {
            AppDatabase db = AppDatabase.getInstance(getApplicationContext());
            DtoUser dto = tokenStore.getUser();
            int id = dto != null ? dto.idUsuario : usuarioId;
            if (id > 0) {
                Suscripcion sus = db.suscripcionDao().obtenerPorUsuario(id);
                long ahora = System.currentTimeMillis();
                if (sus == null) {
                    sus = new Suscripcion(id);
                    sus.plan = Suscripcion.PLAN_PREMIUM;
                    sus.estadoOperacion = "aprobado_simulado";
                    sus.operacionIdSandbox = operacionId;
                    sus.fechaActualizacion = ahora;
                    db.suscripcionDao().insertar(sus);
                } else {
                    db.suscripcionDao().actualizarPlan(id, Suscripcion.PLAN_PREMIUM, "aprobado_simulado", ahora);
                }
                db.usuarioDao().actualizarPlan(id, "premium");
            }
        });
    }

    private void irALogin() {
        Intent intent = new Intent(PagoActivity.this, LoginActivity.class);
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
        startActivity(intent);
        finish();
    }
}
