package com.tamalon.app.validation;

import java.math.BigDecimal;
import java.math.RoundingMode;

public final class ValidadorProducto {

    public static final int LIMITE_GRATUITO = 10;
    public static final int LIMITE_PREMIUM  = 50;

    private ValidadorProducto() {}

    public static ResultadoValidacion validarNombre(String nombre) {
        if (!ValidadorComun.esCadenaNoVacia(nombre))
            return ResultadoValidacion.error("validation_required_field");
        if (!ValidadorComun.esCadenaEnRango(nombre, 2, 60))
            return ResultadoValidacion.error("validation_product_name_length");
        return ResultadoValidacion.ok();
    }

    public static ResultadoValidacion validarDescripcion(String descripcion) {
        if (!ValidadorComun.esCadenaNoVacia(descripcion))
            return ResultadoValidacion.error("validation_required_field");
        if (!ValidadorComun.esCadenaEnRango(descripcion, 1, 300))
            return ResultadoValidacion.error("validation_product_desc_length");
        return ResultadoValidacion.ok();
    }

    public static ResultadoValidacion validarPrecio(String precioTexto) {
        if (!ValidadorComun.esCadenaNoVacia(precioTexto))
            return ResultadoValidacion.error("validation_required_field");
        try {
            BigDecimal precio = new BigDecimal(precioTexto.trim());
            BigDecimal min = new BigDecimal("0.01");
            BigDecimal max = new BigDecimal("99999.99");
            if (precio.compareTo(min) < 0 || precio.compareTo(max) > 0)
                return ResultadoValidacion.error("validation_price_range");
            // Verificar máximo 2 decimales
            BigDecimal escala = precio.stripTrailingZeros();
            if (escala.scale() > 2)
                return ResultadoValidacion.error("validation_price_decimals");
        } catch (NumberFormatException e) {
            return ResultadoValidacion.error("validation_price_range");
        }
        return ResultadoValidacion.ok();
    }

    public static ResultadoValidacion validarCategoria(String categoria) {
        if (!ValidadorComun.esCadenaNoVacia(categoria))
            return ResultadoValidacion.error("validation_required_field");
        return ResultadoValidacion.ok();
    }

    public static ResultadoValidacion validarImagen(String nombreArchivo, long tamanoBytes) {
        if (nombreArchivo == null || nombreArchivo.isEmpty())
            return ResultadoValidacion.ok(); // La imagen es opcional
        if (!ValidadorComun.esImagenFormatoValido(nombreArchivo))
            return ResultadoValidacion.error("validation_image_format");
        if (!ValidadorComun.esImagenTamanoValido(tamanoBytes))
            return ResultadoValidacion.error("validation_image_size");
        return ResultadoValidacion.ok();
    }

    public static ResultadoValidacion validarLimite(int productosActuales, boolean esPremium) {
        int limite = esPremium ? LIMITE_PREMIUM : LIMITE_GRATUITO;
        if (productosActuales >= limite) {
            String resId = esPremium ? "product_limit_premium" : "product_limit_free";
            return ResultadoValidacion.error(resId);
        }
        return ResultadoValidacion.ok();
    }
}
