package com.tamalon.app.payment;

public class PagoServiceFactory {
    public static PagoService getPagoService() {
        return new PagoSimuladoService("aprobado");
    }

    public static PagoService getPagoService(String resultado) {
        return new PagoSimuladoService(resultado);
    }
}
