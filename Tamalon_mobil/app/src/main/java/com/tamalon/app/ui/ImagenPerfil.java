package com.tamalon.app.ui;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.net.Uri;
import android.util.Base64;
import android.widget.ImageView;
import com.tamalon.app.R;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Carga fotos de perfil y producto desde Data URI (Base64 en BD), URLs HTTP/HTTPS
 * o rutas locales (file/content), y codifica imágenes seleccionadas para guardarlas en BD.
 */
public final class ImagenPerfil {
    private static final ExecutorService IO = Executors.newCachedThreadPool();
    private static final int MAX_AVATAR_DIM = 240;
    private static final int MAX_BASE64_BYTES = 45_000;

    private ImagenPerfil() {}

    public static void mostrar(ImageView vista, String referencia, Context context) {
        mostrarConPlaceholder(vista, referencia, context, R.drawable.ic_user, true);
    }

    public static void mostrarProducto(ImageView vista, String referencia, Context context) {
        mostrarConPlaceholder(vista, referencia, context, R.drawable.ic_photo_placeholder, false);
    }

    private static void mostrarConPlaceholder(ImageView vista, String referencia, Context context,
                                              int placeholderRes, boolean esAvatar) {
        if (vista == null) return;
        vista.setImageResource(placeholderRes);
        if (esAvatar) {
            int pad = Math.round(8f * vista.getResources().getDisplayMetrics().density);
            vista.setPadding(pad, pad, pad, pad);
            vista.setScaleType(ImageView.ScaleType.FIT_CENTER);
        }
        if (referencia == null || referencia.trim().isEmpty()) {
            vista.setTag(null);
            return;
        }
        final String valor = referencia.trim();
        vista.setTag(valor);

        if (valor.startsWith("data:image")) {
            try {
                int commaIdx = valor.indexOf(',');
                if (commaIdx > 0) {
                    String base64Part = valor.substring(commaIdx + 1);
                    byte[] bytes = Base64.decode(base64Part, Base64.DEFAULT);
                    Bitmap bitmap = BitmapFactory.decodeByteArray(bytes, 0, bytes.length);
                    if (bitmap != null) {
                        aplicarBitmap(vista, bitmap, esAvatar);
                    }
                }
            } catch (Exception ignored) { }
            return;
        }

        if (valor.startsWith("https://") || valor.startsWith("http://")) {
            IO.execute(() -> {
                Bitmap bitmap = null;
                HttpURLConnection conexion = null;
                try {
                    URL url = new URL(valor);
                    conexion = (HttpURLConnection) url.openConnection();
                    conexion.setConnectTimeout(8000);
                    conexion.setReadTimeout(8000);
                    conexion.setInstanceFollowRedirects(true);
                    conexion.connect();
                    if (conexion.getResponseCode() >= 200 && conexion.getResponseCode() < 300) {
                        try (InputStream input = conexion.getInputStream()) { bitmap = BitmapFactory.decodeStream(input); }
                    }
                } catch (Exception ignored) { }
                finally { if (conexion != null) conexion.disconnect(); }
                final Bitmap resultado = bitmap;
                vista.post(() -> {
                    if (valor.equals(vista.getTag()) && resultado != null) {
                        aplicarBitmap(vista, resultado, esAvatar);
                    }
                });
            });
            return;
        }
        try {
            Uri uri = Uri.parse(valor);
            Bitmap bitmap;
            if ("file".equals(uri.getScheme())) bitmap = BitmapFactory.decodeFile(uri.getPath());
            else if ("content".equals(uri.getScheme())) {
                try (InputStream input = context.getContentResolver().openInputStream(uri)) { bitmap = input == null ? null : BitmapFactory.decodeStream(input); }
            } else bitmap = BitmapFactory.decodeFile(valor);
            if (bitmap != null) aplicarBitmap(vista, bitmap, esAvatar);
        } catch (Exception ignored) { }
    }

    private static void aplicarBitmap(ImageView vista, Bitmap bitmap, boolean esAvatar) {
        if (esAvatar) {
            vista.setPadding(0, 0, 0, 0);
            vista.setScaleType(ImageView.ScaleType.CENTER_CROP);
            vista.setClipToOutline(true);
        }
        vista.setImageBitmap(bitmap);
    }

    /**
     * Redimensiona y comprime una imagen elegida del dispositivo a un Data URI JPEG compacto
     * para almacenarla directamente en la columna `foto_perfil` (TEXT) de la base de datos.
     */
    public static String codificarComoDataUri(Context context, Uri origen) throws Exception {
        BitmapFactory.Options bounds = new BitmapFactory.Options();
        bounds.inJustDecodeBounds = true;
        try (InputStream in = context.getContentResolver().openInputStream(origen)) {
            if (in == null) throw new java.io.IOException("No se pudo abrir la imagen");
            BitmapFactory.decodeStream(in, null, bounds);
        }

        int sampleSize = 1;
        int w = bounds.outWidth;
        int h = bounds.outHeight;
        while (w / sampleSize > MAX_AVATAR_DIM * 2 || h / sampleSize > MAX_AVATAR_DIM * 2) {
            sampleSize *= 2;
        }

        BitmapFactory.Options opts = new BitmapFactory.Options();
        opts.inSampleSize = sampleSize;
        Bitmap decoded;
        try (InputStream in2 = context.getContentResolver().openInputStream(origen)) {
            if (in2 == null) throw new java.io.IOException("No se pudo leer la imagen");
            decoded = BitmapFactory.decodeStream(in2, null, opts);
        }
        if (decoded == null) throw new java.io.IOException("Formato de imagen no válido");

        Bitmap escalado = escalarProporcional(decoded, MAX_AVATAR_DIM);
        if (escalado != decoded) decoded.recycle();

        byte[] jpegBytes = null;
        int[] calidades = {75, 60, 45, 30};
        for (int q : calidades) {
            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            escalado.compress(Bitmap.CompressFormat.JPEG, q, baos);
            jpegBytes = baos.toByteArray();
            if (jpegBytes.length <= MAX_BASE64_BYTES) break;
        }
        escalado.recycle();
        if (jpegBytes == null || jpegBytes.length == 0) {
            throw new java.io.IOException("No se pudo comprimir la imagen");
        }
        return "data:image/jpeg;base64," + Base64.encodeToString(jpegBytes, Base64.NO_WRAP);
    }

    private static Bitmap escalarProporcional(Bitmap src, int maxDim) {
        int w = src.getWidth();
        int h = src.getHeight();
        if (w <= maxDim && h <= maxDim) return src;
        float ratio = Math.min((float) maxDim / w, (float) maxDim / h);
        int nw = Math.max(1, Math.round(w * ratio));
        int nh = Math.max(1, Math.round(h * ratio));
        return Bitmap.createScaledBitmap(src, nw, nh, true);
    }
}
