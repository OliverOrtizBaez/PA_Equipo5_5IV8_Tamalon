package com.tamalon.app.ui;

import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;
import androidx.appcompat.app.AppCompatActivity;
import com.tamalon.app.R;
import com.tamalon.app.data.local.AppDatabase;
import com.tamalon.app.model.Usuario;
import com.tamalon.app.service.AutenticacionService;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

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
        Button catalog = findViewById(R.id.vendor_btn_catalog), report = findViewById(R.id.vendor_btn_report), back = findViewById(R.id.vendor_btn_back);
        catalog.setOnClickListener(v -> { Intent i = new Intent(this, CatalogoPublicoActivity.class); i.putExtra("vendedor_id", vendedorId); startActivity(i); });
        report.setOnClickListener(v -> { Intent i = new Intent(this, ReporteActivity.class); i.putExtra("reportado_id", vendedorId); i.putExtra("reportado_rol", "vendedor"); startActivity(i); });
        back.setOnClickListener(v -> finish());
        authService.obtenerUsuarioActivo(new AutenticacionService.Callback<Usuario>() {
            @Override public void onExito(Usuario u) { clienteId = u.id; }
            @Override public void onError(String r) {}
        });
        if (vendedorId > 0) executor.execute(() -> {
            Usuario u = AppDatabase.getInstance(this).usuarioDao().obtenerPorId(vendedorId);
            runOnUiThread(() -> {
                if (u == null) { Toast.makeText(this, "No se encontró este vendedor", Toast.LENGTH_SHORT).show(); return; }
                ((TextView)findViewById(R.id.vendor_name)).setText(u.nombreCompleto);
                ((TextView)findViewById(R.id.vendor_phone)).setText(u.telefono == null || u.telefono.trim().isEmpty() ? "No agregado" : u.telefono);
                ((TextView)findViewById(R.id.vendor_since)).setText(fecha(u.fechaRegistro));
                ((TextView)findViewById(R.id.vendor_categories)).setText("Puesto ambulante");
                View badge = findViewById(R.id.vendor_premium_badge);
                badge.setVisibility(u.esPremium() ? View.VISIBLE : View.GONE);
            });
        });
    }
    private String fecha(long millis) { return millis <= 0 ? "—" : new SimpleDateFormat("dd/MM/yyyy", new Locale("es", "MX")).format(new Date(millis)); }
    @Override protected void onDestroy() { super.onDestroy(); executor.shutdown(); }
}
