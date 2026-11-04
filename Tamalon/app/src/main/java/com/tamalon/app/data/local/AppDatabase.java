package com.tamalon.app.data.local;

import android.content.Context;
import androidx.room.Database;
import androidx.room.Room;
import androidx.room.RoomDatabase;
import androidx.room.migration.Migration;
import androidx.sqlite.db.SupportSQLiteDatabase;
import com.tamalon.app.model.*;

/**
 * Base de datos Room local de Tamalon.
 *
 * Migración: incrementa `version` y agrega un objeto Migration cuando cambies el esquema.
 * No uses fallbackToDestructiveMigration() en producción: se perderán los datos del usuario.
 *
 * Integración futura con Supabase:
 *   Esta base de datos permanece como caché local. El SyncService se encargará de
 *   propagar los registros pendientes (sincronizado = 0) al backend cuando haya conexión.
 *
 * Usuarios de demostración: esta clase NO crea ningún usuario predeterminado.
 * El primer uso de la aplicación presenta la pantalla de registro.
 */
@Database(
    entities = {
        Usuario.class,
        Catalogo.class,
        Producto.class,
        Reporte.class,
        SolicitudSoporte.class,
        Suscripcion.class,
        Favorito.class
    },
    version = 2,
    exportSchema = false
)
public abstract class AppDatabase extends RoomDatabase {

    private static final String DB_NAME = "tamalon_local.db";
    private static volatile AppDatabase INSTANCE;

    private static final Migration MIGRATION_1_2 = new Migration(1, 2) {
        @Override
        public void migrate(SupportSQLiteDatabase database) {
            database.execSQL("ALTER TABLE usuarios ADD COLUMN ubicacion_latitud REAL");
            database.execSQL("ALTER TABLE usuarios ADD COLUMN ubicacion_longitud REAL");
            database.execSQL("ALTER TABLE usuarios ADD COLUMN disponible_en_ruta INTEGER NOT NULL DEFAULT 0");
            database.execSQL("ALTER TABLE usuarios ADD COLUMN ultima_ubicacion INTEGER NOT NULL DEFAULT 0");
        }
    };

    public abstract UsuarioDao      usuarioDao();
    public abstract CatalogoDao     catalogoDao();
    public abstract ProductoDao     productoDao();
    public abstract ReporteDao      reporteDao();
    public abstract SolicitudSoporteDao solicitudSoporteDao();
    public abstract SuscripcionDao  suscripcionDao();
    public abstract FavoritoDao     favoritoDao();

    public static AppDatabase getInstance(Context context) {
        if (INSTANCE == null) {
            synchronized (AppDatabase.class) {
                if (INSTANCE == null) {
                    INSTANCE = Room.databaseBuilder(
                            context.getApplicationContext(),
                            AppDatabase.class,
                            DB_NAME
                    ).addMigrations(MIGRATION_1_2).build();
                }
            }
        }
        return INSTANCE;
    }
}
