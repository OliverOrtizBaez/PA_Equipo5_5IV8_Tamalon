package com.tamalon.app.ui;

import android.content.Context;
import android.widget.ImageView;
import com.bumptech.glide.Glide;
import com.tamalon.app.R;

/**
 * Carga fotos de perfil y producto usando Glide con caché y placeholders.
 */
public final class ImagenPerfil {
    private ImagenPerfil() {}

    public static void mostrar(ImageView vista, String referencia, Context context) {
        mostrarConPlaceholder(vista, referencia, context, R.drawable.ic_user, true);
    }

    public static void mostrarProducto(ImageView vista, String referencia, Context context) {
        mostrarConPlaceholder(vista, referencia, context, R.drawable.ic_photo_placeholder, false);
    }

    private static void mostrarConPlaceholder(ImageView vista, String referencia, Context context,
                                              int placeholderRes, boolean esAvatar) {
        if (vista == null || context == null) return;
        if (referencia == null || referencia.trim().isEmpty()) {
            vista.setImageResource(placeholderRes);
            if (esAvatar) {
                int pad = Math.round(8f * vista.getResources().getDisplayMetrics().density);
                vista.setPadding(pad, pad, pad, pad);
                vista.setScaleType(ImageView.ScaleType.FIT_CENTER);
            } else {
                vista.setPadding(0, 0, 0, 0);
                vista.setScaleType(ImageView.ScaleType.CENTER_CROP);
            }
            return;
        }

        if (esAvatar) {
            vista.setPadding(0, 0, 0, 0);
            vista.setScaleType(ImageView.ScaleType.CENTER_CROP);
            vista.setClipToOutline(true);
        }

        Glide.with(context)
                .load(referencia.trim())
                .placeholder(placeholderRes)
                .error(placeholderRes)
                .centerCrop()
                .into(vista);
    }
}
