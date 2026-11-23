package com.tamalon.app.payment;

import android.app.Activity;
import androidx.annotation.NonNull;
import com.tamalon.app.R;
import com.tamalon.app.network.ApiClient;
import com.tamalon.app.network.ApiErrorHelper;
import com.tamalon.app.network.ApiService;
import com.tamalon.app.network.ProcesarPagoRequest;
import com.tamalon.app.network.ProcesarPagoResponse;
import retrofit2.Call;
import retrofit2.Response;

public class PagoSimuladoService implements PagoService {

    private final String resultadoDeseado;

    public PagoSimuladoService() {
        this("aprobado");
    }

    public PagoSimuladoService(String resultadoDeseado) {
        this.resultadoDeseado = resultadoDeseado != null ? resultadoDeseado : "aprobado";
    }

    @Override
    public void iniciarPago(Activity activity, String planId, String precio, Callback callback) {
        if (activity == null || callback == null) return;

        ApiService apiService = ApiClient.getApiService(activity);
        ProcesarPagoRequest request = new ProcesarPagoRequest(resultadoDeseado);

        apiService.procesarPago(request).enqueue(new retrofit2.Callback<ProcesarPagoResponse>() {
            @Override
            public void onResponse(@NonNull Call<ProcesarPagoResponse> call, @NonNull Response<ProcesarPagoResponse> response) {
                ResultadoPago resultadoPago;
                int code = response.code();

                if (code == 201 && response.body() != null) {
                    ProcesarPagoResponse body = response.body();
                    String opId = body.codigoTransaccion != null ? body.codigoTransaccion : "DEMO-APROBADO";
                    String msg = body.mensaje != null ? body.mensaje : activity.getString(R.string.pago_exito_simulado);
                    resultadoPago = crearResultado(EstadoPago.APROBADO, opId, msg);
                } else if (code == 200 && response.body() != null) {
                    ProcesarPagoResponse body = response.body();
                    String opId = body.referencia != null ? String.valueOf(body.referencia) : "DEMO-RECHAZADO";
                    String msg = body.mensaje != null ? body.mensaje : activity.getString(R.string.pago_rechazado_msg);
                    if ("Rechazado".equalsIgnoreCase(body.estado) || "rechazado".equalsIgnoreCase(body.estado)) {
                        resultadoPago = crearResultado(EstadoPago.RECHAZADO, opId, msg);
                    } else {
                        resultadoPago = crearResultado(EstadoPago.APROBADO, opId, msg);
                    }
                } else if (code == 503) {
                    resultadoPago = crearResultado(EstadoPago.ERROR, null, activity.getString(R.string.pago_simulado_deshabilitado));
                } else if (code == 401) {
                    resultadoPago = crearResultado(EstadoPago.ERROR, null, activity.getString(R.string.pago_error_401));
                } else if (code == 403) {
                    String errStr = ApiErrorHelper.parseError(activity, null, response);
                    resultadoPago = crearResultado(EstadoPago.ERROR, null, errStr);
                } else {
                    String errStr = ApiErrorHelper.parseError(activity, null, response);
                    resultadoPago = crearResultado(EstadoPago.ERROR, null, errStr);
                }

                notificarEnUI(activity, callback, resultadoPago);
            }

            @Override
            public void onFailure(@NonNull Call<ProcesarPagoResponse> call, @NonNull Throwable t) {
                String errStr = ApiErrorHelper.parseError(activity, t, null);
                ResultadoPago resultadoPago = crearResultado(EstadoPago.ERROR, null, errStr);
                notificarEnUI(activity, callback, resultadoPago);
            }
        });
    }

    private ResultadoPago crearResultado(EstadoPago estado, String operacionId, String mensaje) {
        return new ResultadoPago() {
            @Override
            public EstadoPago getEstado() {
                return estado;
            }

            @Override
            public String getOperacionId() {
                return operacionId;
            }

            @Override
            public String getMensaje() {
                return mensaje;
            }
        };
    }

    private void notificarEnUI(Activity activity, Callback callback, ResultadoPago resultadoPago) {
        if (activity != null && !activity.isFinishing() && !activity.isDestroyed()) {
            activity.runOnUiThread(() -> callback.onResultado(resultadoPago));
        }
    }
}
