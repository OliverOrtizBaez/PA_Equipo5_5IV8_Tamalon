package com.tamalon.app.ui;

import android.content.Intent;
import android.os.Bundle;
import android.text.SpannableStringBuilder;
import android.text.Spanned;
import android.text.TextPaint;
import android.text.method.LinkMovementMethod;
import android.text.style.ClickableSpan;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.view.View;
import android.widget.*;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.app.AlertDialog;
import com.tamalon.app.R;
import com.tamalon.app.model.Usuario;
import com.tamalon.app.service.AutenticacionService;

/**
 * Pantalla de registro de nueva cuenta.
 * No crea ni elimina usuarios predeterminados: solo registra lo que el usuario escribe.
 */
public class RegistroActivity extends AppCompatActivity {

    private EditText etNombre, etCorreo, etPassword, etConfirm;
    private RadioGroup rgRol;
    private RadioButton rbCliente, rbVendedor;
    private CheckBox chkTerminos;
    private Button btnCrear, btnVolver;
    private TextView tvError, tvLegalLinks;
    private AutenticacionService authService;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_register);

        authService = new AutenticacionService(this);

        etNombre   = findViewById(R.id.register_name);
        etCorreo   = findViewById(R.id.register_email);
        etPassword = findViewById(R.id.register_password);
        etConfirm  = findViewById(R.id.register_confirm_password);
        rgRol = findViewById(R.id.register_rol);
        rbCliente  = findViewById(R.id.register_rol_cliente);
        rbVendedor = findViewById(R.id.register_rol_vendedor);
        chkTerminos = findViewById(R.id.register_terms);
        btnCrear   = findViewById(R.id.register_submit);
        btnVolver  = findViewById(R.id.back_to_login);
        tvError    = findViewById(R.id.register_error);
        tvLegalLinks = findViewById(R.id.register_legal_links);
        configurarEnlacesLegales();

        btnCrear.setOnClickListener(v -> intentarRegistro());
        btnVolver.setOnClickListener(v -> finish());
    }


    private void configurarEnlacesLegales() {
        if (tvLegalLinks == null) return;
        String terminos = "Términos y condiciones";
        String separador = "   ·   ";
        String privacidad = "Aviso de privacidad";
        SpannableStringBuilder texto = new SpannableStringBuilder(terminos + separador + privacidad);
        texto.setSpan(new ClickableSpan() {
            @Override public void onClick(View widget) {
                mostrarDocumento("Términos y condiciones", "file:///android_asset/terminos.html");
            }
            @Override public void updateDrawState(TextPaint ds) {
                super.updateDrawState(ds);
                ds.setColor(getColor(R.color.tamalon_field_text));
                ds.setUnderlineText(true);
            }
        }, 0, terminos.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        int inicioPrivacidad = terminos.length() + separador.length();
        texto.setSpan(new ClickableSpan() {
            @Override public void onClick(View widget) {
                mostrarDocumento("Aviso de privacidad", "file:///android_asset/aviso_privacidad.html");
            }
            @Override public void updateDrawState(TextPaint ds) {
                super.updateDrawState(ds);
                ds.setColor(getColor(R.color.tamalon_field_text));
                ds.setUnderlineText(true);
            }
        }, inicioPrivacidad, texto.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        tvLegalLinks.setText(texto);
        tvLegalLinks.setTextColor(getColor(R.color.tamalon_field_text));
        tvLegalLinks.setHighlightColor(getColor(R.color.tamalon_sand));
        tvLegalLinks.setMovementMethod(LinkMovementMethod.getInstance());
        tvLegalLinks.setLinksClickable(true);
    }

    private void mostrarDocumento(String titulo, String urlLocal) {
        WebView webView = new WebView(this);
        WebSettings settings = webView.getSettings();
        settings.setJavaScriptEnabled(false);
        settings.setDomStorageEnabled(false);
        webView.setBackgroundColor(getColor(R.color.tamalon_surface));
        webView.setWebViewClient(new WebViewClient());
        webView.loadUrl(urlLocal);
        int ancho = (int) (getResources().getDisplayMetrics().density * 320);
        int alto = (int) (getResources().getDisplayMetrics().density * 460);
        AlertDialog dialogo = new AlertDialog.Builder(this)
                .setTitle(titulo)
                .setView(webView)
                .setPositiveButton("Cerrar", (dialog, which) -> dialog.dismiss())
                .create();
        dialogo.setOnDismissListener(dialog -> webView.destroy());
        dialogo.show();
        if (dialogo.getWindow() != null) {
            dialogo.getWindow().setLayout(ancho, alto);
        }
    }

    private void intentarRegistro() {
        String nombre   = etNombre.getText().toString();
        String correo   = etCorreo.getText().toString();
        String password = etPassword.getText().toString();
        String confirm  = etConfirm.getText().toString();
        String rol      = rbVendedor.isChecked() ? "vendedor" : rbCliente.isChecked() ? "cliente" : null;
        boolean terminos = chkTerminos.isChecked();

        mostrarCargando(true);
        ocultarError();

        authService.registrar(nombre, correo, password, confirm, rol, terminos,
                new AutenticacionService.Callback<Usuario>() {
                    @Override public void onExito(Usuario usuario) {
                        runOnUiThread(() -> {
                            mostrarCargando(false);
                            abrirMapa(usuario);
                        });
                    }
                    @Override public void onError(String mensajeResId) {
                        runOnUiThread(() -> {
                            mostrarCargando(false);
                            mostrarError(resolverString(mensajeResId));
                        });
                    }
                });
    }

    private void abrirMapa(Usuario usuario) {
        Intent intent = new Intent(this, MapaActivity.class);
        intent.putExtra("usuario_id", usuario.id);
        intent.putExtra("rol", usuario.rol);
        intent.putExtra("plan", usuario.plan);
        intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_NEW_TASK);
        startActivity(intent);
        finishAffinity(); // Cierra Login y Registro para que el usuario no regrese con Back
    }

    private String resolverString(String resIdNombre) {
        int id = getResources().getIdentifier(resIdNombre, "string", getPackageName());
        return id != 0 ? getString(id) : resIdNombre;
    }

    private void mostrarCargando(boolean cargando) {
        btnCrear.setEnabled(!cargando);
        btnCrear.setText(cargando
                ? getString(R.string.register_loading)
                : getString(R.string.register_button));
    }

    private void mostrarError(String mensaje) {
        tvError.setText(mensaje);
        tvError.setVisibility(View.VISIBLE);
    }

    private void ocultarError() {
        tvError.setVisibility(View.GONE);
    }
}
