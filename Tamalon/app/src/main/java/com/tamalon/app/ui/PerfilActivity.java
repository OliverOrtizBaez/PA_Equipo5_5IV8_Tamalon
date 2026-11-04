package com.tamalon.app.ui;

import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.widget.Button;
import android.widget.TextView;
import android.widget.ImageView;
import android.widget.Toast;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import com.tamalon.app.R;
import com.tamalon.app.data.local.AppDatabase;
import com.tamalon.app.model.Usuario;
import com.tamalon.app.service.AutenticacionService;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class PerfilActivity extends AppCompatActivity {
    private final ExecutorService executor = Executors.newSingleThreadExecutor();
    private AutenticacionService auth;
    private int usuarioId = -1;
    private String rol = "cliente", plan = "gratuito";
    @Override protected void onCreate(Bundle state) {
        super.onCreate(state); setContentView(R.layout.activity_profile);
        auth = new AutenticacionService(this);
        usuarioId = getIntent().getIntExtra("usuario_id", -1);
        if (getIntent().getStringExtra("rol") != null) rol = getIntent().getStringExtra("rol");
        if (getIntent().getStringExtra("plan") != null) plan = getIntent().getStringExtra("plan");
        BottomNavigation.configurar(this, findViewById(android.R.id.content), usuarioId, rol, plan);
        Button premium = findViewById(R.id.open_premium);
        premium.setOnClickListener(v -> { Intent i = new Intent(this, PremiumActivity.class); i.putExtra("usuario_id", usuarioId); startActivity(i); });
        findViewById(R.id.open_personal).setOnClickListener(v -> abrirSeccion("personal"));
        findViewById(R.id.open_preferences).setOnClickListener(v -> abrirSeccion("settings"));
        findViewById(R.id.open_support).setOnClickListener(v -> abrirSeccion("support"));
        findViewById(R.id.profile_favorites_card).setOnClickListener(v -> {
            if (!"cliente".equals(rol)) { aviso("Los favoritos son para cuentas de cliente."); return; }
            if (!esPremium(plan)) { aviso("Esta función es exclusiva de cuentas Premium."); return; }
            Intent i = new Intent(this, FavoritosActivity.class); i.putExtra("usuario_id", usuarioId); i.putExtra("rol", rol); i.putExtra("plan", plan); startActivity(i);
        });
        findViewById(R.id.profile_logout).setOnClickListener(v -> confirmarCerrarSesion());
        View catalogs = findViewById(R.id.open_catalogs);
        if (catalogs != null) { catalogs.setVisibility("vendedor".equals(rol) ? View.VISIBLE : View.GONE); catalogs.setOnClickListener(v -> abrirCatalogos()); }
        auth.obtenerUsuarioActivo(new AutenticacionService.Callback<Usuario>() {
            @Override public void onExito(Usuario u) { runOnUiThread(() -> actualizarPerfil(u)); }
            @Override public void onError(String m) { runOnUiThread(() -> { aviso("No se pudo cargar el perfil. Vuelve a iniciar sesión."); auth.cerrarSesion(); irLogin(); }); }
        });
    }
    @Override protected void onResume() {
        super.onResume();
        if (auth != null) auth.obtenerUsuarioActivo(new AutenticacionService.Callback<Usuario>() {
            @Override public void onExito(Usuario u) { runOnUiThread(() -> actualizarPerfil(u)); }
            @Override public void onError(String m) { /* MainActivity gestiona sesiones inválidas al iniciar la app. */ }
        });
    }
    private void actualizarPerfil(Usuario u) {
        usuarioId = u.id; rol = u.rol; plan = u.plan == null ? "gratuito" : u.plan;
        ((TextView)findViewById(R.id.profile_user_name)).setText(u.nombreCompleto);
        ((TextView)findViewById(R.id.profile_name_card)).setText(u.nombreCompleto + "\n" + (u.esVendedor() ? "Vendedor" : "Cliente") + " · Plan " + plan);
        ImageView avatar = findViewById(R.id.profile_avatar);
        if (avatar != null) ImagenPerfil.mostrar(avatar, u.fotoUri, this);
        View catalogs = findViewById(R.id.open_catalogs);
        if (catalogs != null) catalogs.setVisibility(u.esVendedor() ? View.VISIBLE : View.GONE);
        View favCard = findViewById(R.id.profile_favorites_card);
        favCard.setVisibility(u.esCliente() ? View.VISIBLE : View.GONE);
        if (u.esCliente()) executor.execute(() -> {
            int total = AppDatabase.getInstance(this).favoritoDao().contarPorCliente(u.id);
            runOnUiThread(() -> ((TextView)findViewById(R.id.profile_favorites_count)).setText(String.valueOf(total)));
        });
    }
    private boolean esPremium(String p) { return "premium".equals(p); }
    private void confirmarCerrarSesion() {
        new AlertDialog.Builder(this).setTitle("Cerrar sesión").setMessage("¿Quieres cerrar la sesión de este dispositivo?")
                .setNegativeButton("Cancelar", (d,w) -> d.dismiss())
                .setPositiveButton("Cerrar sesión", (d,w) -> { auth.cerrarSesion(); Toast.makeText(this, "Sesión cerrada", Toast.LENGTH_SHORT).show(); irLogin(); }).show();
    }
    private void irLogin() { Intent i = new Intent(this, LoginActivity.class); i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK); startActivity(i); finish(); }
    private void abrirSeccion(String seccion) { Intent i = new Intent(this, PerfilSeccionActivity.class); i.putExtra("seccion", seccion); i.putExtra("usuario_id", usuarioId); startActivity(i); }
    private void abrirCatalogos() { Intent i = new Intent(this, CatalogoActivity.class); i.putExtra("usuario_id", usuarioId); i.putExtra("rol", rol); i.putExtra("plan", plan); startActivity(i); }
    private void aviso(String m) { Toast.makeText(this, m, Toast.LENGTH_SHORT).show(); }
    @Override protected void onDestroy() { super.onDestroy(); executor.shutdown(); }
}
