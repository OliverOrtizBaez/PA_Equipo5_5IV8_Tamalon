package com.tamalon.app.ui;

import android.os.Bundle;
import android.view.View;
import android.widget.Button;
import android.widget.TextView;
import androidx.appcompat.app.AppCompatActivity;
import com.tamalon.app.R;
import com.tamalon.app.payment.MercadoPagoPagoService;
import com.tamalon.app.payment.PagoService;
import com.tamalon.app.data.local.AppDatabase;
import com.tamalon.app.model.Suscripcion;
import java.util.concurrent.Executors;
import com.tamalon.app.service.AutenticacionService;
import com.tamalon.app.validation.ResultadoValidacion;
import com.tamalon.app.validation.ValidadorPago;

/** Checkout seguro: no permite activar Premium mediante simulaciones locales. */
public class PagoActivity extends AppCompatActivity {
    private TextView tvPlanNombre, tvPrecio, tvBeneficiosList, tvEstado;
    private Button btnPagar, btnCancelar;
    private PagoService pagoService;
    private AutenticacionService authService;
    private int usuarioId = -1;
    private final String planId = "premium";
    private String precio = "49.00";

    @Override protected void onCreate(Bundle state) {
        super.onCreate(state); setContentView(R.layout.activity_pago);
        authService = new AutenticacionService(this);
        pagoService = new MercadoPagoPagoService();
        tvPlanNombre = findViewById(R.id.pago_plan_name);
        tvPrecio = findViewById(R.id.pago_total);
        tvEstado = findViewById(R.id.pago_status);
        tvBeneficiosList = findViewById(R.id.pago_benefits);
        btnPagar = findViewById(R.id.pago_btn_mercadopago);
        btnCancelar = findViewById(R.id.pago_btn_back);
        precio = getIntent().getStringExtra("precio");
        if (precio == null || precio.trim().isEmpty()) precio = "49.00";
        tvPlanNombre.setText(getString(R.string.premium_label));
        tvPrecio.setText("$" + precio + " " + getString(R.string.premium_currency));
        tvBeneficiosList.setText("• " + getString(R.string.nav_catalogs) + "\n• " + getString(R.string.nav_routes) + "\n• " + getString(R.string.favorites_title));
        tvEstado.setVisibility(View.VISIBLE);
        tvEstado.setText("Los pagos aún no están habilitados. La integración con Mercado Pago y la verificación del pago en el servidor deben configurarse antes de activar Premium.");
        btnPagar.setVisibility(View.VISIBLE);
        btnPagar.setText("Pago no disponible todavía");
        btnPagar.setOnClickListener(v -> iniciarPagoReal());
        btnPagar.setEnabled(false);
        btnCancelar.setOnClickListener(v -> finish());
        authService.obtenerUsuarioActivo(new AutenticacionService.Callback<com.tamalon.app.model.Usuario>() {
            @Override public void onExito(com.tamalon.app.model.Usuario usuario) {
                usuarioId = usuario.id;
                if (usuario.esPremium()) {
                    tvEstado.setText("Tu cuenta ya tiene Premium.");
                    btnPagar.setText("Premium activo");
                }
            }
            @Override public void onError(String m) { finish(); }
        });
    }

    /** Se conserva como punto de entrada para la integración real. Solo debe llamarse tras verificar el pago en servidor. */
    private void iniciarPagoReal() {
        ResultadoValidacion rv = ValidadorPago.validarUsuarioAutenticado(usuarioId);
        if (!rv.esValido()) { tvEstado.setText(getString(R.string.validation_unexpected_error)); return; }
        btnPagar.setEnabled(false);
        pagoService.iniciarPago(this, planId, precio, resultado -> {
            if (resultado.getEstado() == PagoService.EstadoPago.APROBADO
                    && resultado.getOperacionId() != null
                    && !resultado.getOperacionId().startsWith("DEMO-")) {
                // Este callback solo puede representar una operación APROBADA tras verificación del backend.
                guardarSuscripcionVerificada(resultado.getOperacionId());
                tvEstado.setText("Pago aprobado y verificado. Tu cuenta Premium se está activando.");
            } else {
                tvEstado.setText("No se pudo verificar un pago aprobado. Tu cuenta no fue modificada.");
            }
            tvEstado.setVisibility(View.VISIBLE);
            btnPagar.setEnabled(false);
        });
    }

    private void guardarSuscripcionVerificada(String operacionId) {
        Executors.newSingleThreadExecutor().execute(() -> {
            AppDatabase db = AppDatabase.getInstance(getApplicationContext());
            Suscripcion sus = db.suscripcionDao().obtenerPorUsuario(usuarioId);
            long ahora = System.currentTimeMillis();
            if (sus == null) {
                sus = new Suscripcion(usuarioId);
                sus.plan = Suscripcion.PLAN_PREMIUM;
                sus.estadoOperacion = "aprobado_verificado";
                sus.operacionIdSandbox = operacionId;
                sus.fechaActualizacion = ahora;
                db.suscripcionDao().insertar(sus);
            } else {
                db.suscripcionDao().actualizarPlan(usuarioId, Suscripcion.PLAN_PREMIUM, "aprobado_verificado", ahora);
            }
            db.usuarioDao().actualizarPlan(usuarioId, Suscripcion.PLAN_PREMIUM);
            runOnUiThread(() -> { tvEstado.setText("Tu cuenta Premium está activa."); btnPagar.setText("Premium activo"); });
        });
    }
}
