package com.tamalon.app.data.local;

import androidx.lifecycle.LiveData;
import androidx.room.*;
import com.tamalon.app.model.Usuario;
import java.util.List;

@Dao
public interface UsuarioDao {

    @Insert(onConflict = OnConflictStrategy.ABORT)
    long insertar(Usuario usuario);

    @Update
    void actualizar(Usuario usuario);

    @Delete
    void eliminar(Usuario usuario);

    @Query("SELECT * FROM usuarios WHERE id = :id LIMIT 1")
    Usuario obtenerPorId(int id);

    @Query("SELECT * FROM usuarios WHERE correo = :correo LIMIT 1")
    Usuario obtenerPorCorreo(String correo);

    @Query("SELECT COUNT(*) FROM usuarios WHERE correo = :correo")
    int contarPorCorreo(String correo);

    @Query("SELECT * FROM usuarios")
    LiveData<List<Usuario>> obtenerTodos();

    @Query("SELECT * FROM usuarios WHERE rol = :rol ORDER BY nombre_completo COLLATE NOCASE")
    List<Usuario> obtenerPorRol(String rol);

    @Query("UPDATE usuarios SET nombre_completo = :nombre, telefono = :telefono, foto_uri = :fotoUri WHERE id = :id")
    void actualizarPerfil(int id, String nombre, String telefono, String fotoUri);

    @Query("UPDATE usuarios SET foto_uri = :fotoUri WHERE id = :id")
    void actualizarFoto(int id, String fotoUri);

    @Query("SELECT u.* FROM usuarios u JOIN vendedor v ON v.id_usuario = u.id WHERE v.disponible_en_ruta = 1 AND v.ubicacion_latitud IS NOT NULL AND v.ubicacion_longitud IS NOT NULL AND v.ultima_ubicacion >= :desdeTimestamp")
    List<Usuario> obtenerVendedoresEnRuta(long desdeTimestamp);

}
