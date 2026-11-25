package com.tamalon.app.ui;

import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;
import androidx.appcompat.app.AppCompatActivity;
import com.tamalon.app.R;
import com.tamalon.app.model.Usuario;
import com.tamalon.app.network.ApiClient;
import com.tamalon.app.network.VendedorListaDto;
import com.tamalon.app.network.VendedoresListaResponse;
import com.tamalon.app.repository.UsuarioRepository;
import com.tamalon.app.service.AutenticacionService;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

/** El Cliente ve el perfil del Vendedor y puede reportarlo (RF23.2). */
public class VendedorDetailActivity extends AppCompatActivity {
    private final ExecutorService executor = Executors.newSingleThreadExecutor();
    private int vendedorId;
    private int clienteId;
    private String planCliente = "gratuito";
    private AutenticacionService authService;
    @Override protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState); setContentView(R.layout.activity_vendor_detail);
        authService = new AutenticacionService(this);
        vendedorId = getIntent().getIntExtra("vendedor_id", -1);
        planCliente = getIntent().getStringExtra("plan_cliente") == null ? "gratuito" : getIntent().getStringExtra("plan_cliente");
        String nombreExtra = getIntent().getStringExtra("vendedor_nombre");
        String fotoExtra = getIntent().getStringExtra("vendedor_foto");
        ImageView avatarView = findViewById(R.id.vendor_avatar);
        if (nombreExtra != null && !nombreExtra.trim().isEmpty()) {
            ((TextView) findViewById(R.id.vendor_name)).setText(nombreExtra);
        }
        if (avatarView != null) {
            ImagenPerfil.mostrar(avatarView, fotoExtra, this);
        }
        Button catalog = findViewById(R.id.vendor_btn_catalog), report = findViewById(R.id.vendor_btn_report), back = findViewById(R.id.vendor_btn_back);
        catalog.setOnClickListener(v -> {
            Intent i = new Intent(this, CatalogoPublicoActivity.class);
            i.putExtra("vendedor_id", vendedorId);
            i.putExtra("plan_cliente", planCliente);
            i.putExtra("vendedor_nombre", ((TextView) findViewById(R.id.vendor_name)).getText().toString());
            startActivity(i);
        });
        report.setOnClickListener(v -> { Intent i = new Intent(this, ReporteActivity.class); i.putExtra("reportado_id", vendedorId); i.putExtra("reportado_rol", "vendedor"); startActivity(i); });
        back.setOnClickListener(v -> finish());
        authService.obtenerUsuarioActivo(new AutenticacionService.Callback<Usuario>() {
            @Override public void onExito(Usuario u) { clienteId = u.id; }
            @Override public void onError(String r) {}
        });
        if (vendedorId > 0) executor.execute(() -> {
            Usuario u = new UsuarioRepository(this).obtenerPorId(vendedorId);
            runOnUiThread(() -> {
                if (u != null) {
                    ((TextView)findViewById(R.id.vendor_name)).setText(u.nombreCompleto);
                    ((TextView)findViewById(R.id.vendor_phone)).setText(u.telefono == null || u.telefono.trim().isEmpty() ? "No agregado" : u.telefono);
                    ((TextView)findViewById(R.id.vendor_since)).setText(fecha(u.fechaRegistro));
                    ((TextView)findViewById(R.id.vendor_categories)).setText("Puesto ambulante");
                    View badge = findViewById(R.id.vendor_premium_badge);
                    badge.setVisibility(u.esPremium() ? View.VISIBLE : View.GONE);
                    if (avatarView != null && u.fotoUri != null && !u.fotoUri.trim().isEmpty()) {
                        ImagenPerfil.mostrar(avatarView, u.fotoUri, this);
                    }
                }
                cargarFotoDesdeApi(avatarView);
            });
        });
    }

    private void cargarFotoDesdeApi(ImageView avatarView) {
        ApiClient.getApiService(this).obtenerVendedores(null, null, 1, 50).enqueue(new Callback<VendedoresListaResponse>() {
            @Override
            public void onResponse(Call<VendedoresListaResponse> call, Response<VendedoresListaResponse> response) {
                if (response.isSuccessful() && response.body() != null && response.body().vendedores != null) {
                    for (VendedorListaDto v : response.body().vendedores) {
                        if (v.idUsuario == vendedorId) {
                            if (v.nombreCompleto != null) {
                                ((TextView) findViewById(R.id.vendor_name)).setText(v.nombreCompleto);
                            }
                            if (avatarView != null && v.fotoPerfil != null && !v.fotoPerfil.trim().isEmpty()) {
                                ImagenPerfil.mostrar(avatarView, v.fotoPerfil, VendedorDetailActivity.this);
                            }
                            View badge = findViewById(R.id.vendor_premium_badge);
                            if (badge != null) {
                                badge.setVisibility("premium".equalsIgnoreCase(v.plan) ? View.VISIBLE : View.GONE);
                            }
                            break;
                        }
                    }
                }
            }
            @Override public void onFailure(Call<VendedoresListaResponse> call, Throwable t) { }
        });
    }
    private String fecha(long millis) { return millis <= 0 ? "—" : new SimpleDateFormat("dd/MM/yyyy", new Locale("es", "MX")).format(new Date(millis)); }
    @Override protected void onDestroy() { super.onDestroy(); executor.shutdown(); }
}
