package com.tamalon.app.ui;

import android.app.Activity;
import android.content.Intent;
import android.view.View;
import android.widget.Button;
import android.widget.Toast;

import com.tamalon.app.R;

/** Conecta la barra inferior de las pantallas principales según el rol (cliente / vendedor). */
public final class BottomNavigation {
    private BottomNavigation() {}

    public static void configurar(Activity activity, View root, int usuarioId, String rol, String plan) {
        View map = root.findViewById(R.id.nav_map);
        View list = root.findViewById(R.id.nav_list);
        View favorites = root.findViewById(R.id.nav_favorites);
        View profile = root.findViewById(R.id.nav_profile);

        if ("vendedor".equals(rol)) {
            // Adaptar etiquetas e iconos para la barra inferior del vendedor: Mapa, Catálogos, Rutas, Perfil
            if (map instanceof Button) {
                ((Button) map).setText("Mapa");
            }
            if (list instanceof Button) {
                ((Button) list).setText("Catálogos");
                ((Button) list).setCompoundDrawablesWithIntrinsicBounds(0, R.drawable.ic_list, 0, 0);
            }
            if (favorites instanceof Button) {
                ((Button) favorites).setText("Rutas");
                ((Button) favorites).setCompoundDrawablesWithIntrinsicBounds(0, R.drawable.ic_navigation, 0, 0);
            }

            if (map != null) map.setOnClickListener(v -> abrir(activity, MapaActivity.class, usuarioId, rol, plan));
            if (list != null) list.setOnClickListener(v -> abrir(activity, CatalogoActivity.class, usuarioId, rol, plan));
            if (favorites != null) favorites.setOnClickListener(v -> {
                if (!"premium".equals(plan)) {
                    Toast.makeText(activity, "Rutas: Consulta y administra las funciones de rutas disponibles con Tamalón Premium", Toast.LENGTH_LONG).show();
                    Intent i = new Intent(activity, PagoActivity.class);
                    i.putExtra("usuario_id", usuarioId);
                    i.putExtra("rol", rol);
                    activity.startActivity(i);
                } else {
                    Toast.makeText(activity, "Rutas Premium activadas", Toast.LENGTH_SHORT).show();
                }
            });
            if (profile != null) profile.setOnClickListener(v -> abrir(activity, PerfilActivity.class, usuarioId, rol, plan));
        } else {
            // Cliente: Mapa, Lista, Favoritos, Perfil
            if (map != null) map.setOnClickListener(v -> abrir(activity, MapaActivity.class, usuarioId, rol, plan));
            if (list != null) list.setOnClickListener(v -> abrir(activity, ListaVendedoresActivity.class, usuarioId, rol, plan));
            if (favorites != null) favorites.setOnClickListener(v -> abrir(activity, FavoritosActivity.class, usuarioId, rol, plan));
            if (profile != null) profile.setOnClickListener(v -> abrir(activity, PerfilActivity.class, usuarioId, rol, plan));
        }
    }

    private static void abrir(Activity actual, Class<?> destino, int id, String rol, String plan) {
        if (actual.getClass().equals(destino)) return;
        Intent i = new Intent(actual, destino);
        i.putExtra("usuario_id", id);
        i.putExtra("rol", rol);
        i.putExtra("plan", plan);
        actual.startActivity(i);
    }
}
