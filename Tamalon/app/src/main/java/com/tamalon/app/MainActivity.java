package com.tamalon.app;

import android.content.Intent;
import android.os.Bundle;
import androidx.appcompat.app.AppCompatActivity;
import com.tamalon.app.model.Usuario;
import com.tamalon.app.service.AutenticacionService;
import com.tamalon.app.ui.LoginActivity;
import com.tamalon.app.ui.MapaActivity;

public class MainActivity extends AppCompatActivity {
    private AutenticacionService auth;
    @Override protected void onCreate(Bundle savedInstanceState) {

        super.onCreate(savedInstanceState);
        auth = new AutenticacionService(this);
        if (!auth.haySesionActiva()) { abrirLogin(); return; }
        auth.obtenerUsuarioActivo(new AutenticacionService.Callback<Usuario>() {
            @Override public void onExito(Usuario usuario) {
                runOnUiThread(() -> {
                    if (usuario == null || (!usuario.esCliente() && !usuario.esVendedor())) { auth.cerrarSesion(); abrirLogin(); }
                    else abrirMapa(usuario);
                });
            }
            @Override public void onError(String mensaje) { runOnUiThread(() -> { auth.cerrarSesion(); abrirLogin(); }); }
        });
    }
    private void abrirLogin() { startActivity(new Intent(this, LoginActivity.class)); finish(); }
    private void abrirMapa(Usuario usuario) {
        Intent i = new Intent(this, MapaActivity.class);
        i.putExtra("usuario_id", usuario.id); i.putExtra("rol", usuario.rol); i.putExtra("plan", usuario.plan);
        startActivity(i); finish();
    }
}
