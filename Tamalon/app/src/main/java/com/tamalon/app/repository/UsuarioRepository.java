package com.tamalon.app.repository;

import android.content.Context;
import com.tamalon.app.data.local.AppDatabase;
import com.tamalon.app.data.local.ClienteDao;
import com.tamalon.app.data.local.UsuarioDao;
import com.tamalon.app.data.local.VendedorDao;
import com.tamalon.app.model.ClientePerfil;
import com.tamalon.app.model.Usuario;
import com.tamalon.app.model.VendedorPerfil;

public class UsuarioRepository {

    private final AppDatabase db;
    private final UsuarioDao usuarioDao;
    private final VendedorDao vendedorDao;
    private final ClienteDao clienteDao;

    public UsuarioRepository(Context context) {
        this.db = AppDatabase.getInstance(context);
        this.usuarioDao = db.usuarioDao();
        this.vendedorDao = db.vendedorDao();
        this.clienteDao = db.clienteDao();
    }

    public UsuarioRepository(AppDatabase db) {
        this.db = db;
        this.usuarioDao = db.usuarioDao();
        this.vendedorDao = db.vendedorDao();
        this.clienteDao = db.clienteDao();
    }

    public void guardarUsuarioTrasLogin(Usuario localUser) {
        if (localUser == null) return;
        db.runInTransaction(() -> {
            Usuario existing = usuarioDao.obtenerPorCorreo(localUser.correo);
            if (existing != null) {
                localUser.id = existing.id;
                usuarioDao.actualizar(localUser);
            } else {
                long newId = usuarioDao.insertar(localUser);
                if (newId > 0) {
                    localUser.id = (int) newId;
                }
            }

            if (localUser.esVendedor()) {
                VendedorPerfil existingVp = vendedorDao.obtenerPorIdUsuario(localUser.id);
                VendedorPerfil vp = new VendedorPerfil(localUser.id, localUser.plan);
                if (existingVp != null) {
                    vp.ubicacionLatitud = existingVp.ubicacionLatitud;
                    vp.ubicacionLongitud = existingVp.ubicacionLongitud;
                    vp.disponibleEnRuta = existingVp.disponibleEnRuta;
                    vp.ultimaUbicacion = existingVp.ultimaUbicacion;
                }
                vendedorDao.guardar(vp);
            } else {
                ClientePerfil cp = new ClientePerfil(localUser.id, localUser.plan);
                clienteDao.guardar(cp);
            }
        });
    }

    public Usuario obtenerPorId(int id) {
        Usuario u = usuarioDao.obtenerPorId(id);
        if (u != null) {
            completarDatosPerfil(u);
        }
        return u;
    }

    public Usuario obtenerPorCorreo(String correo) {
        Usuario u = usuarioDao.obtenerPorCorreo(correo);
        if (u != null) {
            completarDatosPerfil(u);
        }
        return u;
    }

    private void completarDatosPerfil(Usuario u) {
        if (u.esVendedor()) {
            VendedorPerfil vp = vendedorDao.obtenerPorIdUsuario(u.id);
            if (vp != null) {
                u.plan = vp.plan != null ? vp.plan : "gratuito";
                u.ubicacionLatitud = vp.ubicacionLatitud;
                u.ubicacionLongitud = vp.ubicacionLongitud;
                u.disponibleEnRuta = vp.disponibleEnRuta;
                u.ultimaUbicacion = vp.ultimaUbicacion;
            } else {
                u.plan = "gratuito";
            }
        } else {
            ClientePerfil cp = clienteDao.obtenerPorIdUsuario(u.id);
            if (cp != null) {
                u.plan = cp.plan != null ? cp.plan : "gratuito";
            } else {
                u.plan = "gratuito";
            }
        }
    }

    public void actualizarPlan(int id, String plan) {
        db.runInTransaction(() -> {
            vendedorDao.actualizarPlan(id, plan);
            clienteDao.actualizarPlan(id, plan);
        });
    }
}
