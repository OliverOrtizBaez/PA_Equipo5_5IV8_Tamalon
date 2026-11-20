package com.tamalon.app.ui;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.net.Uri;
import android.widget.ImageView;
import com.tamalon.app.R;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/** Carga fotos guardadas localmente y admite URLs HTTPS cuando se integre el almacenamiento remoto. */
public final class ImagenPerfil {
    private static final ExecutorService IO = Executors.newCachedThreadPool();
    private ImagenPerfil() {}

    public static void mostrar(ImageView vista, String referencia, Context context) {
        vista.setImageResource(R.drawable.ic_user);
        if (referencia == null || referencia.trim().isEmpty()) return;
        final String valor = referencia.trim();
        vista.setTag(valor);
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
                vista.post(() -> { if (valor.equals(vista.getTag()) && resultado != null) vista.setImageBitmap(resultado); });
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
            if (bitmap != null) vista.setImageBitmap(bitmap);
        } catch (Exception ignored) { }
    }
}
