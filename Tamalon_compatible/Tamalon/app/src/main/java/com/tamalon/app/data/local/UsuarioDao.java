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

    // Actualiza solo el plan del usuario; la activación real requiere el backend
    @Query("UPDATE usuarios SET plan = :plan WHERE id = :id")
    void actualizarPlan(int id, String plan);

    @Query("UPDATE usuarios SET nombre_completo = :nombre, telefono = :telefono, foto_uri = :fotoUri WHERE id = :id")
    void actualizarPerfil(int id, String nombre, String telefono, String fotoUri);

    @Query("UPDATE usuarios SET foto_uri = :fotoUri WHERE id = :id")
    void actualizarFoto(int id, String fotoUri);

    @Query("SELECT * FROM usuarios WHERE rol = 'vendedor' AND disponible_en_ruta = 1 AND ubicacion_latitud IS NOT NULL AND ubicacion_longitud IS NOT NULL AND ultima_ubicacion >= :desdeTimestamp")
    List<Usuario> obtenerVendedoresEnRuta(long desdeTimestamp);

    @Query("UPDATE usuarios SET ubicacion_latitud = :latitud, ubicacion_longitud = :longitud, ultima_ubicacion = :timestamp WHERE id = :id AND rol = 'vendedor'")
    void actualizarUbicacionVendedor(int id, double latitud, double longitud, long timestamp);

    @Query("UPDATE usuarios SET disponible_en_ruta = :disponible, ultima_ubicacion = :timestamp WHERE id = :id AND rol = 'vendedor'")
    void actualizarDisponibilidadVendedor(int id, boolean disponible, long timestamp);

    @Query("UPDATE usuarios SET disponible_en_ruta = 0 WHERE rol = 'vendedor' AND disponible_en_ruta = 1 AND ultima_ubicacion < :limiteTimestamp")
    void desactivarVendedoresSinSenal(long limiteTimestamp);

}
