package com.tamalon.app.ui;

import android.content.Intent;
import android.os.Bundle;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;
import androidx.appcompat.app.AppCompatActivity;
import com.tamalon.app.R;
import com.tamalon.app.data.local.AppDatabase;
import com.tamalon.app.model.Usuario;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/** El Vendedor ve el perfil de un Cliente y puede reportarlo (RF23.1). */
public class ClienteDetailActivity extends AppCompatActivity {
    private final ExecutorService executor = Executors.newSingleThreadExecutor();
    private int clienteId;
    @Override protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState); setContentView(R.layout.activity_client_detail);
        clienteId = getIntent().getIntExtra("cliente_id", -1);
        Button report = findViewById(R.id.client_btn_report), back = findViewById(R.id.client_btn_back);
        report.setOnClickListener(v -> { Intent i = new Intent(this, ReporteActivity.class); i.putExtra("reportado_id", clienteId); i.putExtra("reportado_rol", "cliente"); startActivity(i); });
        back.setOnClickListener(v -> finish());
        if (clienteId > 0) executor.execute(() -> {
            Usuario u = AppDatabase.getInstance(this).usuarioDao().obtenerPorId(clienteId);
            runOnUiThread(() -> {
                if (u == null) { Toast.makeText(this, "No se encontró este perfil", Toast.LENGTH_SHORT).show(); return; }
                ((TextView)findViewById(R.id.client_name)).setText(u.nombreCompleto);
                ((TextView)findViewById(R.id.client_plan)).setText(u.plan == null ? "gratuito" : u.plan);
                ((TextView)findViewById(R.id.client_phone)).setText(u.telefono == null || u.telefono.trim().isEmpty() ? "No agregado" : u.telefono);
                ((TextView)findViewById(R.id.client_since)).setText(fecha(u.fechaRegistro));
            });
        });
    }
    private String fecha(long millis) { return millis <= 0 ? "—" : new SimpleDateFormat("dd/MM/yyyy", new Locale("es", "MX")).format(new Date(millis)); }
    @Override protected void onDestroy() { super.onDestroy(); executor.shutdown(); }
}
