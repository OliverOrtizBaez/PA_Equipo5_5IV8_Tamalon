package com.tamalon.app.ui;

import android.os.Bundle;
import androidx.appcompat.app.AppCompatActivity;
import com.tamalon.app.R;

/**
 * Vista pública del catálogo de un Vendedor.
 * Cliente Gratuito: nombre, imagen y categoría.
 * Cliente Premium: también precio y descripción.
 */
public class CatalogoPublicoActivity extends AppCompatActivity {

    private int vendedorId;
    private String planCliente;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_public_catalog);

        vendedorId  = getIntent().getIntExtra("vendedor_id", -1);
        planCliente = getIntent().getStringExtra("plan_cliente");

        // Integración futura: cargar productos del vendedor desde CatalogoRepository
        // y mostrarlos según el plan del cliente autenticado.
    }
}
