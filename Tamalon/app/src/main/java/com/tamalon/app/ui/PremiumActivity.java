package com.tamalon.app.ui;

import android.content.Intent;
import android.os.Bundle;
import android.widget.Button;
import android.widget.TextView;
import androidx.appcompat.app.AppCompatActivity;
import com.tamalon.app.R;
import com.tamalon.app.model.Usuario;
import com.tamalon.app.service.AutenticacionService;

/** Pantalla informativa de Premium; el pago real depende de configurar la pasarela en backend. */
public class PremiumActivity extends AppCompatActivity {
    private int usuarioId = -1;
    @Override protected void onCreate(Bundle state) {
        super.onCreate(state); setContentView(R.layout.activity_profile_premium);
        Button back = findViewById(R.id.back_to_profile), subscribe = findViewById(R.id.subscribe_premium);
        back.setOnClickListener(v -> finish());
        subscribe.setOnClickListener(v -> {
            Intent i = new Intent(this, PagoActivity.class); i.putExtra("usuario_id", usuarioId); startActivity(i);
        });
        new AutenticacionService(this).obtenerUsuarioActivo(new AutenticacionService.Callback<Usuario>() {
            @Override public void onExito(Usuario u) {
                usuarioId = u.id;
                runOnUiThread(() -> {
                    if (u.esPremium()) { subscribe.setText("Ya tienes Premium"); subscribe.setEnabled(false); }
                    TextView tagline = findViewById(R.id.premium_tagline);
                    if (tagline != null) tagline.setText("Beneficios para tu cuenta de " + (u.esVendedor() ? "vendedor" : "cliente") + ".");
                });
            }
            @Override public void onError(String m) { runOnUiThread(() -> { subscribe.setEnabled(false); subscribe.setText("Inicia sesión para continuar"); }); }
        });
    }
}
