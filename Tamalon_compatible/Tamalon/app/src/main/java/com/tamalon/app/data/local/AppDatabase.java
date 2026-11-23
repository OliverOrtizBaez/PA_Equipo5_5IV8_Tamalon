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
        VendedorPerfil.class,
        ClientePerfil.class,
        Catalogo.class,
        Producto.class,
        Reporte.class,
        SolicitudSoporte.class,
        Suscripcion.class,
        Favorito.class
    },
    version = 3,
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

    private static final Migration MIGRATION_2_3 = new Migration(2, 3) {
        @Override
        public void migrate(SupportSQLiteDatabase database) {
            database.execSQL("CREATE TABLE IF NOT EXISTS `vendedor` (" +
                    "`id_usuario` INTEGER NOT NULL, " +
                    "`plan` TEXT, " +
                    "`ubicacion_latitud` REAL, " +
                    "`ubicacion_longitud` REAL, " +
                    "`disponible_en_ruta` INTEGER NOT NULL DEFAULT 0, " +
                    "`ultima_ubicacion` INTEGER NOT NULL DEFAULT 0, " +
                    "PRIMARY KEY(`id_usuario`), " +
                    "FOREIGN KEY(`id_usuario`) REFERENCES `usuarios`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE)");
            database.execSQL("CREATE INDEX IF NOT EXISTS `index_vendedor_id_usuario` ON `vendedor` (`id_usuario`)");

            database.execSQL("CREATE TABLE IF NOT EXISTS `cliente` (" +
                    "`id_usuario` INTEGER NOT NULL, " +
                    "`plan` TEXT, " +
                    "PRIMARY KEY(`id_usuario`), " +
                    "FOREIGN KEY(`id_usuario`) REFERENCES `usuarios`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE)");
            database.execSQL("CREATE INDEX IF NOT EXISTS `index_cliente_id_usuario` ON `cliente` (`id_usuario`)");

            database.execSQL("INSERT INTO `vendedor` (`id_usuario`, `plan`, `ubicacion_latitud`, `ubicacion_longitud`, `disponible_en_ruta`, `ultima_ubicacion`) " +
                    "SELECT `id`, COALESCE(`plan`, 'gratuito'), `ubicacion_latitud`, `ubicacion_longitud`, `disponible_en_ruta`, `ultima_ubicacion` " +
                    "FROM `usuarios` WHERE `rol` = 'vendedor'");

            database.execSQL("INSERT INTO `cliente` (`id_usuario`, `plan`) " +
                    "SELECT `id`, COALESCE(`plan`, 'gratuito') " +
                    "FROM `usuarios` WHERE `rol` <> 'vendedor'");

            database.execSQL("CREATE TABLE IF NOT EXISTS `usuarios_nueva` (" +
                    "`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                    "`correo` TEXT NOT NULL, " +
                    "`nombre_completo` TEXT NOT NULL, " +
                    "`password_hash` TEXT NOT NULL, " +
                    "`password_salt` TEXT NOT NULL, " +
                    "`rol` TEXT NOT NULL, " +
                    "`telefono` TEXT, " +
                    "`foto_uri` TEXT, " +
                    "`fecha_registro` INTEGER NOT NULL, " +
                    "`supabase_uid` TEXT)");

            database.execSQL("INSERT INTO `usuarios_nueva` (`id`, `correo`, `nombre_completo`, `password_hash`, `password_salt`, `rol`, `telefono`, `foto_uri`, `fecha_registro`, `supabase_uid`) " +
                    "SELECT `id`, `correo`, `nombre_completo`, `password_hash`, `password_salt`, `rol`, `telefono`, `foto_uri`, `fecha_registro`, `supabase_uid` " +
                    "FROM `usuarios`");

            database.execSQL("DROP TABLE `usuarios`");

            database.execSQL("ALTER TABLE `usuarios_nueva` RENAME TO `usuarios`");
        }
    };

    public abstract UsuarioDao      usuarioDao();
    public abstract VendedorDao     vendedorDao();
    public abstract ClienteDao      clienteDao();
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
                    ).addMigrations(MIGRATION_1_2, MIGRATION_2_3).build();
                }
            }
        }
        return INSTANCE;
    }
}
