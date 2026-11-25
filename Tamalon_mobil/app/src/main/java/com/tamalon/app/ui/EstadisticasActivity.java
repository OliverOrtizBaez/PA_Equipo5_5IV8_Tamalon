package com.tamalon.app.ui;

import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.TextView;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import com.tamalon.app.R;
import com.tamalon.app.network.ApiClient;
import com.tamalon.app.network.ApiService;
import com.tamalon.app.network.EstadisticasResponse;
import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

public class EstadisticasActivity extends AppCompatActivity {

    private String plan = "gratuito";
    private View lockedView;
    private LinearLayout contentLayout;
    private TextView summaryText;
    private BarChartCustomView chartView;
    private ApiService apiService;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_estadisticas);

        if (getIntent().getStringExtra("plan") != null) {
            plan = getIntent().getStringExtra("plan");
        }

        lockedView = findViewById(R.id.estadisticas_locked_view);
        contentLayout = findViewById(R.id.estadisticas_content_layout);
        summaryText = findViewById(R.id.estadisticas_summary_text);
        chartView = findViewById(R.id.estadisticas_chart_view);
        apiService = ApiClient.getApiService(this);

        if (!"premium".equalsIgnoreCase(plan)) {
            mostrarPantallaBloqueada();
        } else {
            cargarEstadisticas();
        }
    }

    private void mostrarPantallaBloqueada() {
        contentLayout.setVisibility(View.GONE);
        lockedView.setVisibility(View.VISIBLE);
        androidx.appcompat.app.AlertDialog.Builder builder = new androidx.appcompat.app.AlertDialog.Builder(this);
        builder.setTitle("Función Premium")
                .setMessage("Hazte Premium por $50 para ver las estadísticas de tu negocio.")
                .setPositiveButton("Hazte Premium por $50", (dialog, which) -> {
                    startActivity(new Intent(this, PremiumActivity.class));
                    finish();
                })
                .setNegativeButton("Volver", (dialog, which) -> finish())
                .setCancelable(false)
                .show();
    }

    private void cargarEstadisticas() {
        apiService.obtenerEstadisticasVendedor().enqueue(new Callback<EstadisticasResponse>() {
            @Override
            public void onResponse(@NonNull Call<EstadisticasResponse> call, @NonNull Response<EstadisticasResponse> response) {
                if (response.isSuccessful() && response.body() != null) {
                    EstadisticasResponse data = response.body();
                    summaryText.setText("Total Aperturas: " + data.totalAperturas + " | Favoritos: " + data.totalFavoritos);
                    if (data.visitasPorDia != null) {
                        chartView.setData(data.visitasPorDia);
                    }
                } else {
                    summaryText.setText("No se pudieron cargar las estadísticas.");
                }
            }

            @Override
            public void onFailure(@NonNull Call<EstadisticasResponse> call, @NonNull Throwable t) {
                summaryText.setText("Error de conexión al cargar estadísticas.");
            }
        });
    }
}
