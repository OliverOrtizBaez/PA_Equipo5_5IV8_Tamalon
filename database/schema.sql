-- ============================================================
-- Tamalon - esquema MySQL 8.0.16+ (requerido para CHECK y funciones de ventana)
-- ATENCION: este script BORRA y recrea todas las tablas.
-- ============================================================
CREATE DATABASE IF NOT EXISTS `tamalon_db` CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
USE `tamalon_db`;

SET @OLD_FOREIGN_KEY_CHECKS = @@FOREIGN_KEY_CHECKS;
SET FOREIGN_KEY_CHECKS = 0;
SET time_zone = '+00:00';

DROP TABLE IF EXISTS `notificacion_enviada`;
DROP TABLE IF EXISTS `dispositivo`;
DROP TABLE IF EXISTS `registro_login`;
DROP TABLE IF EXISTS `waypoint`;
DROP TABLE IF EXISTS `ruta`;
DROP TABLE IF EXISTS `producto`;
DROP TABLE IF EXISTS `catalogo`;
DROP TABLE IF EXISTS `favorito`;
DROP TABLE IF EXISTS `solicitudes_soporte`;
DROP TABLE IF EXISTS `reporte`;
DROP TABLE IF EXISTS `ubi_vendedor`;
DROP TABLE IF EXISTS `suscripcion`;
DROP TABLE IF EXISTS `sesion`;
DROP TABLE IF EXISTS `recuperacion_pass`;
DROP TABLE IF EXISTS `historial_rol`;
DROP TABLE IF EXISTS `usuarios`;
DROP TABLE IF EXISTS `rol`;            -- eliminada: el rol vive en usuarios.rol

-- 1. Usuarios -------------------------------------------------
-- fecha_corte es DATE (dia calendario en ZH-N, America/Mexico_City): ultimo dia inclusive de la vigencia Premium.
-- intentos_fallidos / inicio_intentos / ultimo_intento implementan RF2.3b.
CREATE TABLE `usuarios` (
  `id_usuario`        int NOT NULL AUTO_INCREMENT,
  `nombre_completo`   varchar(100) NOT NULL,
  `contrasena`        varchar(255) NOT NULL,
  `correo`            varchar(100) NOT NULL,
  `telefono`          varchar(10) DEFAULT NULL,
  `foto_perfil`       text,
  `rol`               varchar(30) NOT NULL,
  `estado_cuenta`     varchar(30) NOT NULL DEFAULT 'Activa',
  `zona_horaria`      varchar(50) NOT NULL DEFAULT 'America/Mexico_City',
  `fecha_creacion`    timestamp NULL DEFAULT CURRENT_TIMESTAMP,
  `fin_suspension`    timestamp NULL DEFAULT NULL,
  `intentos_fallidos` int NOT NULL DEFAULT 0,
  `inicio_intentos`   timestamp NULL DEFAULT NULL,
  `ultimo_intento`    timestamp NULL DEFAULT NULL,
  `fecha_corte`       date DEFAULT NULL,
  `renovacion`        tinyint(1) NOT NULL DEFAULT 0,
  PRIMARY KEY (`id_usuario`),
  UNIQUE KEY `correo` (`correo`),
  KEY `idx_usuarios_rol` (`rol`),
  KEY `idx_usuarios_suspension` (`estado_cuenta`, `fin_suspension`),
  CONSTRAINT `chk_usuarios_rol` CHECK (`rol` IN ('Vendedor Gratuito','Vendedor Premium','Cliente Gratuito','Cliente Premium','Administrador')),
  CONSTRAINT `chk_usuarios_estado` CHECK (`estado_cuenta` IN ('Activa','Suspendida indefinidamente','Suspendida temporalmente'))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- 2. Suscripcion = libro de pagos (compras y renovaciones) ------
-- Los cambios manuales del administrador NO generan fila aqui (van a historial_rol).
CREATE TABLE `suscripcion` (
  `id_suscripcion`     int NOT NULL AUTO_INCREMENT,
  `id_usuario`         int NOT NULL,
  `tipo`               varchar(20) NOT NULL DEFAULT 'Compra',
  `estado_pago`        varchar(20) NOT NULL,
  `suscrs_monto`       decimal(10,2) NOT NULL,
  `moneda`             varchar(10) NOT NULL DEFAULT 'MXN',
  `codigo_transaccion` varchar(100) DEFAULT NULL,
  `fech_pago`          timestamp NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`id_suscripcion`),
  UNIQUE KEY `uq_codigo_transaccion` (`codigo_transaccion`),
  KEY `idx_suscripcion_estado_fecha` (`estado_pago`, `fech_pago`),
  KEY `fk_suscripcion_usuario` (`id_usuario`),
  CONSTRAINT `fk_suscripcion_usuario` FOREIGN KEY (`id_usuario`) REFERENCES `usuarios` (`id_usuario`) ON DELETE CASCADE,
  CONSTRAINT `chk_suscripcion_tipo` CHECK (`tipo` IN ('Compra','Renovacion')),
  CONSTRAINT `chk_suscripcion_estado` CHECK (`estado_pago` IN ('Aprobado','Rechazado','Sin confirmar'))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- 3. Sesion (token = SHA-256 del token entregado al cliente) ---
CREATE TABLE `sesion` (
  `id_sesion`        int NOT NULL AUTO_INCREMENT,
  `id_usuario`       int NOT NULL,
  `token`            varchar(255) NOT NULL,
  `fech_hrs_login`   timestamp NULL DEFAULT CURRENT_TIMESTAMP,
  `fecha_expiracion` timestamp NOT NULL,
  PRIMARY KEY (`id_sesion`),
  UNIQUE KEY `token_unico` (`token`),
  KEY `fk_sesion_usuario` (`id_usuario`),
  KEY `idx_sesion_expiracion` (`fecha_expiracion`),
  CONSTRAINT `fk_sesion_usuario` FOREIGN KEY (`id_usuario`) REFERENCES `usuarios` (`id_usuario`) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- 4. Recuperacion de contrasena (token = SHA-256) ---------------
CREATE TABLE `recuperacion_pass` (
  `id_recuperacion`  int NOT NULL AUTO_INCREMENT,
  `id_usuario`       int NOT NULL,
  `token`            varchar(255) NOT NULL,
  `fech_emision`     timestamp NULL DEFAULT CURRENT_TIMESTAMP,
  `fecha_expiracion` timestamp NOT NULL,
  `valido`           tinyint(1) NOT NULL DEFAULT 1,
  PRIMARY KEY (`id_recuperacion`),
  UNIQUE KEY `token_unico` (`token`),
  KEY `idx_recup_usuario_fecha` (`id_usuario`, `fech_emision`),
  CONSTRAINT `fk_recup_usuario` FOREIGN KEY (`id_usuario`) REFERENCES `usuarios` (`id_usuario`) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- 5. Historial de cambios manuales de rol (RF17d) ---------------
CREATE TABLE `historial_rol` (
  `id_historial` int NOT NULL AUTO_INCREMENT,
  `id_admin`     int NOT NULL,
  `id_usuario`   int NOT NULL,
  `rol_anterior` varchar(30) NOT NULL,
  `rol_nuevo`    varchar(30) NOT NULL,
  `fech_hrs`     timestamp NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`id_historial`),
  KEY `fk_historial_admin` (`id_admin`),
  KEY `fk_historial_usuario` (`id_usuario`),
  CONSTRAINT `fk_historial_admin` FOREIGN KEY (`id_admin`) REFERENCES `usuarios` (`id_usuario`) ON DELETE CASCADE,
  CONSTRAINT `fk_historial_usuario` FOREIGN KEY (`id_usuario`) REFERENCES `usuarios` (`id_usuario`) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- 6. Registro de inicios de sesion exitosos (RF20a, retencion 12 meses)
CREATE TABLE `registro_login` (
  `id_registro` bigint NOT NULL AUTO_INCREMENT,
  `id_usuario`  int NOT NULL,
  `rol`         varchar(30) NOT NULL,
  `fech_hrs`    timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`id_registro`),
  KEY `idx_registro_login_fecha` (`fech_hrs`),
  CONSTRAINT `fk_registro_login_usuario` FOREIGN KEY (`id_usuario`) REFERENCES `usuarios` (`id_usuario`) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- 7. Catalogo ---------------------------------------------------
CREATE TABLE `catalogo` (
  `id_catalogo`   int NOT NULL AUTO_INCREMENT,
  `id_vendedor`   int NOT NULL,
  `nombre`        varchar(50) NOT NULL,
  `estado`        varchar(20) NOT NULL DEFAULT 'Visible',
  `fech_creacion` timestamp NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`id_catalogo`),
  UNIQUE KEY `uq_catalogo_nombre` (`id_vendedor`, `nombre`),
  CONSTRAINT `fk_catalogo_vendedor` FOREIGN KEY (`id_vendedor`) REFERENCES `usuarios` (`id_usuario`) ON DELETE CASCADE,
  CONSTRAINT `chk_catalogo_estado` CHECK (`estado` IN ('Visible','Oculto'))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- 8. Producto ---------------------------------------------------
CREATE TABLE `producto` (
  `id_producto`   int NOT NULL AUTO_INCREMENT,
  `id_catalogo`   int NOT NULL,
  `nombre`        varchar(60) NOT NULL,
  `descripcion`   varchar(300) NOT NULL,
  `precio`        decimal(10,2) NOT NULL,
  `imagen`        text,
  `estado`        varchar(20) NOT NULL DEFAULT 'Visible',
  `categoria`     varchar(50) NOT NULL,
  `fech_creacion` timestamp NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`id_producto`),
  KEY `idx_producto_catalogo_fecha` (`id_catalogo`, `fech_creacion`),
  CONSTRAINT `fk_producto_catalogo` FOREIGN KEY (`id_catalogo`) REFERENCES `catalogo` (`id_catalogo`) ON DELETE CASCADE,
  CONSTRAINT `chk_producto_estado` CHECK (`estado` IN ('Visible','Oculto')),
  CONSTRAINT `chk_producto_categoria` CHECK (`categoria` IN ('Comida preparada','Bebidas','Postres y dulces','Frutas y verduras','Botanas y snacks','Otros'))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- 9. Ruta (una por dia por vendedor, RF14.1d) -------------------
CREATE TABLE `ruta` (
  `id_ruta`     int NOT NULL AUTO_INCREMENT,
  `id_vendedor` int NOT NULL,
  `estado`      varchar(20) NOT NULL DEFAULT 'Activa',
  `dia_semana`  varchar(20) NOT NULL,
  PRIMARY KEY (`id_ruta`),
  UNIQUE KEY `uq_ruta_dia` (`id_vendedor`, `dia_semana`),
  CONSTRAINT `fk_ruta_vendedor` FOREIGN KEY (`id_vendedor`) REFERENCES `usuarios` (`id_usuario`) ON DELETE CASCADE,
  CONSTRAINT `chk_ruta_estado` CHECK (`estado` IN ('Activa','Inactiva')),
  CONSTRAINT `chk_ruta_dia` CHECK (`dia_semana` IN ('Lunes','Martes','Miercoles','Jueves','Viernes','Sabado','Domingo'))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- 10. Waypoint --------------------------------------------------
CREATE TABLE `waypoint` (
  `id_waypoint` int NOT NULL AUTO_INCREMENT,
  `id_ruta`     int NOT NULL,
  `orden`       int NOT NULL,
  `latitud`     double NOT NULL,
  `longitud`    double NOT NULL,
  PRIMARY KEY (`id_waypoint`),
  UNIQUE KEY `uq_waypoint_orden` (`id_ruta`, `orden`),
  CONSTRAINT `fk_waypoint_ruta` FOREIGN KEY (`id_ruta`) REFERENCES `ruta` (`id_ruta`) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- 11. Ubicacion del vendedor (solo la ultima, RF6.2b) -----------
CREATE TABLE `ubi_vendedor` (
  `id_ubicacion`         int NOT NULL AUTO_INCREMENT,
  `id_vendedor`          int NOT NULL,
  `latitud`              double NOT NULL,
  `longitud`             double NOT NULL,
  `disponibilidad`       varchar(20) NOT NULL DEFAULT 'Fuera de ruta',
  `ultima_actualizacion` timestamp NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id_ubicacion`),
  UNIQUE KEY `id_vendedor` (`id_vendedor`),
  KEY `idx_ubi_disponibilidad` (`disponibilidad`, `ultima_actualizacion`),
  CONSTRAINT `fk_ubicacion_vendedor` FOREIGN KEY (`id_vendedor`) REFERENCES `usuarios` (`id_usuario`) ON DELETE CASCADE,
  CONSTRAINT `chk_ubi_disponibilidad` CHECK (`disponibilidad` IN ('En ruta','Fuera de ruta'))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- 12. Favorito (maximo 5 por cliente se valida en la API, RF9.1c)
CREATE TABLE `favorito` (
  `id_favorito` int NOT NULL AUTO_INCREMENT,
  `id_cliente`  int NOT NULL,
  `id_vendedor` int NOT NULL,
  PRIMARY KEY (`id_favorito`),
  UNIQUE KEY `uq_favorito` (`id_cliente`, `id_vendedor`),
  KEY `fk_favorito_vendedor` (`id_vendedor`),
  CONSTRAINT `fk_favorito_cliente` FOREIGN KEY (`id_cliente`) REFERENCES `usuarios` (`id_usuario`) ON DELETE CASCADE,
  CONSTRAINT `fk_favorito_vendedor` FOREIGN KEY (`id_vendedor`) REFERENCES `usuarios` (`id_usuario`) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- 13. Reporte ---------------------------------------------------
CREATE TABLE `reporte` (
  `id_reporte`   int NOT NULL AUTO_INCREMENT,
  `id_emisor`    int NOT NULL,
  `id_reportado` int NOT NULL,
  `motivo`       varchar(500) NOT NULL,
  `fech_hrs`     timestamp NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`id_reporte`),
  KEY `idx_reporte_fecha` (`fech_hrs`, `id_reporte`),
  KEY `idx_reporte_par` (`id_emisor`, `id_reportado`, `fech_hrs`),
  KEY `fk_reporte_reportado` (`id_reportado`),
  CONSTRAINT `fk_reporte_emisor` FOREIGN KEY (`id_emisor`) REFERENCES `usuarios` (`id_usuario`) ON DELETE CASCADE,
  CONSTRAINT `fk_reporte_reportado` FOREIGN KEY (`id_reportado`) REFERENCES `usuarios` (`id_usuario`) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- Solicitudes de ayuda al equipo Tamalon (distintas de reportes entre usuarios)
CREATE TABLE `solicitudes_soporte` (
  `id_solicitud`  int NOT NULL AUTO_INCREMENT,
  `id_usuario`    int NOT NULL,
  `rol_emisor`    varchar(30) NOT NULL,
  `categoria`     varchar(20) NOT NULL,
  `asunto`        varchar(120) NOT NULL,
  `descripcion`   varchar(1000) NOT NULL,
  `estado`        varchar(20) NOT NULL DEFAULT 'pendiente',
  `fecha_creacion` timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`id_solicitud`),
  KEY `idx_solicitud_soporte_usuario_fecha` (`id_usuario`, `fecha_creacion`),
  CONSTRAINT `fk_solicitud_soporte_usuario` FOREIGN KEY (`id_usuario`) REFERENCES `usuarios` (`id_usuario`) ON DELETE CASCADE,
  CONSTRAINT `chk_solicitud_soporte_categoria` CHECK (`categoria` IN ('queja','peticion','sugerencia','tecnico','otro')),
  CONSTRAINT `chk_solicitud_soporte_estado` CHECK (`estado` IN ('pendiente','en_revision','atendida'))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- Mensajes enviados desde el formulario público de contacto de la landing.
CREATE TABLE `mensaje_contacto` (
  `id_mensaje`      int NOT NULL AUTO_INCREMENT,
  `nombre`          varchar(100) NOT NULL,
  `correo`          varchar(100) NOT NULL,
  `mensaje`         varchar(1000) NOT NULL,
  `fecha_creacion`  timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`id_mensaje`),
  KEY `idx_mensaje_contacto_fecha` (`fecha_creacion`, `id_mensaje`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- 14. Dispositivos para push (RF9.2 / RF9.3) --------------------
CREATE TABLE `dispositivo` (
  `id_dispositivo` int NOT NULL AUTO_INCREMENT,
  `id_usuario`     int NOT NULL,
  `token_push`     varchar(255) NOT NULL,
  `plataforma`     varchar(20) DEFAULT NULL,
  `fech_registro`  timestamp NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`id_dispositivo`),
  UNIQUE KEY `uq_token_push` (`token_push`),
  KEY `fk_dispositivo_usuario` (`id_usuario`),
  CONSTRAINT `fk_dispositivo_usuario` FOREIGN KEY (`id_usuario`) REFERENCES `usuarios` (`id_usuario`) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- 15. Control de frecuencia de notificaciones (max 1 cada 30 min)
CREATE TABLE `notificacion_enviada` (
  `id_notificacion` bigint NOT NULL AUTO_INCREMENT,
  `id_cliente`      int NOT NULL,
  `id_vendedor`     int NOT NULL,
  `tipo`            varchar(20) NOT NULL,
  `fech_hrs`        timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`id_notificacion`),
  KEY `idx_notif_par` (`id_cliente`, `id_vendedor`, `tipo`, `fech_hrs`),
  CONSTRAINT `fk_notif_cliente` FOREIGN KEY (`id_cliente`) REFERENCES `usuarios` (`id_usuario`) ON DELETE CASCADE,
  CONSTRAINT `fk_notif_vendedor` FOREIGN KEY (`id_vendedor`) REFERENCES `usuarios` (`id_usuario`) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

SET FOREIGN_KEY_CHECKS = @OLD_FOREIGN_KEY_CHECKS;
