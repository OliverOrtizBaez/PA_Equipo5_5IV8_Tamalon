-- ============================================================
-- Tamalon v2 - esquema MySQL 8.0.16+  (3FN, columnas atomicas)
-- ATENCION: BORRA y recrea la base. Hacer respaldo antes si hay datos que importan.
--
-- Reglas aplicadas
--  1FN: sin valores compuestos (nombre_completo -> nombre + apellidos; dia_semana -> catalogo).
--  2FN: toda PK es simple; no hay dependencias parciales.
--  3FN: ningun atributo depende de otro no-clave. Los datos "de lista" (roles, estados, tipos de
--       vendedor, categorias, dias, estados de pago) viven en tablas catalogo con FK.
--       Premium = EXISTENCIA de una fila en premium_vendedor (sin NULLs, sin fecha_corte, sin renovacion).
--
-- Cambios de negocio
--  * Cliente Premium eliminado: cliente solo tiene id (favoritos libres para todo cliente).
--  * Premium de vendedor: pago unico de $50 MXN, vitalicio. No hay vencimiento ni renovacion.
--  * Tipo de vendedor (ropavejero, limpieza, basura, etc.) con icono; categorias de producto por tipo.
--  * Ubicacion: ubicacion_actual (ultimo punto) + jornada (turno de trabajo) + ubicacion_historial
--    (puntos para estadisticas premium) + evento_vendedor (vistas/favoritos para estadisticas).
-- ============================================================
CREATE DATABASE IF NOT EXISTS `tamalon_db` CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
USE `tamalon_db`;

SET @OLD_FOREIGN_KEY_CHECKS = @@FOREIGN_KEY_CHECKS;
SET FOREIGN_KEY_CHECKS = 0;
SET time_zone = '+00:00';

DROP VIEW  IF EXISTS `vw_usuario`;
DROP TABLE IF EXISTS `evento_vendedor`, `ubicacion_historial`, `jornada`, `ubicacion_actual`,
  `ubi_vendedor`, `notificacion_enviada`, `dispositivo`, `registro_login`, `waypoint`, `ruta`,
  `producto`, `catalogo`, `favorito`, `mensaje_contacto`, `solicitud_soporte`, `solicitudes_soporte`,
  `reporte`, `pago`, `suscripcion`, `sesion`, `recuperacion_pass`, `historial_plan`, `historial_rol`,
  `premium_vendedor`, `suspension`, `control_acceso`, `vendedor`, `cliente`, `usuarios`, `usuario`,
  `categoria_producto`, `tipo_vendedor`, `dia_semana`, `estado_pago`, `estado_cuenta`, `rol`;

-- ===================== CATALOGOS (tablas de lista) =====================
CREATE TABLE `rol` (
  `id_rol`  tinyint NOT NULL,
  `nombre`  varchar(20) NOT NULL,
  PRIMARY KEY (`id_rol`),
  UNIQUE KEY `uq_rol_nombre` (`nombre`)
) ENGINE=InnoDB;
INSERT INTO `rol` VALUES (1,'Administrador'),(2,'Vendedor'),(3,'Cliente');

CREATE TABLE `estado_cuenta` (
  `id_estado_cuenta` tinyint NOT NULL,
  `nombre`           varchar(30) NOT NULL,
  PRIMARY KEY (`id_estado_cuenta`),
  UNIQUE KEY `uq_estado_cuenta_nombre` (`nombre`)
) ENGINE=InnoDB;
INSERT INTO `estado_cuenta` VALUES (1,'Activa'),(2,'Suspendida indefinidamente'),(3,'Suspendida temporalmente');

CREATE TABLE `estado_pago` (
  `id_estado_pago` tinyint NOT NULL,
  `nombre`         varchar(20) NOT NULL,
  PRIMARY KEY (`id_estado_pago`),
  UNIQUE KEY `uq_estado_pago_nombre` (`nombre`)
) ENGINE=InnoDB;
INSERT INTO `estado_pago` VALUES (1,'Aprobado'),(2,'Rechazado'),(3,'Sin confirmar');

CREATE TABLE `dia_semana` (
  `id_dia`  tinyint NOT NULL,          -- 1 = Lunes ... 7 = Domingo
  `nombre`  varchar(10) NOT NULL,
  PRIMARY KEY (`id_dia`),
  UNIQUE KEY `uq_dia_nombre` (`nombre`)
) ENGINE=InnoDB;
INSERT INTO `dia_semana` VALUES (1,'Lunes'),(2,'Martes'),(3,'Miercoles'),(4,'Jueves'),(5,'Viernes'),(6,'Sabado'),(7,'Domingo');

-- Tipo de vendedor: define el icono del mapa. 'icono' = nombre de recurso drawable en Android
-- (ic_tipo_<clave>) y de archivo en la web (iconos/tipo_<clave>.svg).
CREATE TABLE `tipo_vendedor` (
  `id_tipo_vendedor` smallint NOT NULL AUTO_INCREMENT,
  `clave`            varchar(30) NOT NULL,
  `nombre`           varchar(50) NOT NULL,
  `icono`            varchar(50) NOT NULL,
  `activo`           tinyint(1) NOT NULL DEFAULT 1,
  PRIMARY KEY (`id_tipo_vendedor`),
  UNIQUE KEY `uq_tipo_vendedor_clave` (`clave`)
) ENGINE=InnoDB;
INSERT INTO `tipo_vendedor` (`clave`,`nombre`,`icono`) VALUES
 ('ropavejero',  'Ropavejero / fierro viejo',    'ic_tipo_ropavejero'),
 ('limpieza',    'Productos de limpieza',        'ic_tipo_limpieza'),
 ('basura',      'Recolector de basura',         'ic_tipo_basura'),
 ('comida',      'Comida y antojitos',           'ic_tipo_comida'),
 ('fruta_verdura','Frutas y verduras',           'ic_tipo_fruta_verdura'),
 ('pan_dulces',  'Pan y dulces',                 'ic_tipo_pan_dulces'),
 ('agua_gas',    'Agua y gas',                   'ic_tipo_agua_gas'),
 ('afilador',    'Afilador y reparaciones',      'ic_tipo_afilador'),
 ('plantas',     'Plantas y flores',             'ic_tipo_plantas'),
 ('otro',        'Otro',                         'ic_tipo_otro');

-- Categorias de producto: dependen del tipo de vendedor (3FN: no se repiten textos en producto).
CREATE TABLE `categoria_producto` (
  `id_categoria`     int NOT NULL AUTO_INCREMENT,
  `id_tipo_vendedor` smallint NOT NULL,
  `nombre`           varchar(50) NOT NULL,
  PRIMARY KEY (`id_categoria`),
  UNIQUE KEY `uq_categoria_tipo_nombre` (`id_tipo_vendedor`,`nombre`),
  CONSTRAINT `fk_categoria_tipo` FOREIGN KEY (`id_tipo_vendedor`) REFERENCES `tipo_vendedor` (`id_tipo_vendedor`)
) ENGINE=InnoDB;
INSERT INTO `categoria_producto` (`id_tipo_vendedor`,`nombre`)
SELECT t.id_tipo_vendedor, c.nombre FROM tipo_vendedor t JOIN (
  SELECT 'ropavejero' k,'Ropa y calzado' nombre UNION ALL SELECT 'ropavejero','Muebles y electrodomesticos'
  UNION ALL SELECT 'ropavejero','Fierro y metales' UNION ALL SELECT 'ropavejero','Otros articulos'
  UNION ALL SELECT 'limpieza','Detergentes' UNION ALL SELECT 'limpieza','Desinfectantes y cloro'
  UNION ALL SELECT 'limpieza','Utensilios (escobas, trapeadores)' UNION ALL SELECT 'limpieza','Otros'
  UNION ALL SELECT 'basura','Recoleccion domiciliaria' UNION ALL SELECT 'basura','Reciclables' UNION ALL SELECT 'basura','Otros'
  UNION ALL SELECT 'comida','Comida preparada' UNION ALL SELECT 'comida','Bebidas' UNION ALL SELECT 'comida','Botanas y snacks' UNION ALL SELECT 'comida','Postres'
  UNION ALL SELECT 'fruta_verdura','Frutas' UNION ALL SELECT 'fruta_verdura','Verduras' UNION ALL SELECT 'fruta_verdura','Otros'
  UNION ALL SELECT 'pan_dulces','Pan' UNION ALL SELECT 'pan_dulces','Dulces' UNION ALL SELECT 'pan_dulces','Otros'
  UNION ALL SELECT 'agua_gas','Agua' UNION ALL SELECT 'agua_gas','Gas' UNION ALL SELECT 'agua_gas','Otros'
  UNION ALL SELECT 'afilador','Afilado' UNION ALL SELECT 'afilador','Reparaciones' UNION ALL SELECT 'afilador','Otros'
  UNION ALL SELECT 'plantas','Plantas' UNION ALL SELECT 'plantas','Flores' UNION ALL SELECT 'plantas','Macetas y tierra'
  UNION ALL SELECT 'otro','General'
) c ON c.k = t.clave;

-- ===================== USUARIOS =====================
CREATE TABLE `usuario` (
  `id_usuario`       int NOT NULL AUTO_INCREMENT,
  `id_rol`           tinyint NOT NULL,
  `id_estado_cuenta` tinyint NOT NULL DEFAULT 1,
  `nombre`           varchar(60) NOT NULL,
  `apellidos`        varchar(60) NOT NULL,
  `correo`           varchar(100) NOT NULL,
  `contrasena`       varchar(255) NOT NULL,
  `telefono`         varchar(10) DEFAULT NULL,
  `foto_perfil`      text,
  `zona_horaria`     varchar(50) NOT NULL DEFAULT 'America/Mexico_City',
  `fecha_creacion`   timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`id_usuario`),
  UNIQUE KEY `uq_usuario_correo` (`correo`),
  KEY `idx_usuario_orden` (`nombre`,`apellidos`,`id_usuario`),       -- listado alfabetico del admin
  KEY `idx_usuario_rol` (`id_rol`),
  KEY `idx_usuario_estado` (`id_estado_cuenta`),
  CONSTRAINT `fk_usuario_rol`    FOREIGN KEY (`id_rol`) REFERENCES `rol` (`id_rol`),
  CONSTRAINT `fk_usuario_estado` FOREIGN KEY (`id_estado_cuenta`) REFERENCES `estado_cuenta` (`id_estado_cuenta`)
) ENGINE=InnoDB;

-- Fin de suspension: solo existe si la cuenta esta suspendida temporalmente (evita NULLs en usuario)
CREATE TABLE `suspension` (
  `id_usuario` int NOT NULL,
  `fin`        timestamp NOT NULL,
  PRIMARY KEY (`id_usuario`),
  KEY `idx_suspension_fin` (`fin`),
  CONSTRAINT `fk_suspension_usuario` FOREIGN KEY (`id_usuario`) REFERENCES `usuario` (`id_usuario`) ON DELETE CASCADE
) ENGINE=InnoDB;

CREATE TABLE `control_acceso` (
  `id_usuario`        int NOT NULL,
  `intentos_fallidos` int NOT NULL DEFAULT 0,
  `inicio_intentos`   timestamp NULL DEFAULT NULL,
  `ultimo_intento`    timestamp NULL DEFAULT NULL,
  PRIMARY KEY (`id_usuario`),
  CONSTRAINT `fk_control_acceso_usuario` FOREIGN KEY (`id_usuario`) REFERENCES `usuario` (`id_usuario`) ON DELETE CASCADE
) ENGINE=InnoDB;

CREATE TABLE `cliente` (
  `id_cliente` int NOT NULL,
  PRIMARY KEY (`id_cliente`),
  CONSTRAINT `fk_cliente_usuario` FOREIGN KEY (`id_cliente`) REFERENCES `usuario` (`id_usuario`) ON DELETE CASCADE
) ENGINE=InnoDB;

CREATE TABLE `vendedor` (
  `id_vendedor`      int NOT NULL,
  `id_tipo_vendedor` smallint NOT NULL,
  PRIMARY KEY (`id_vendedor`),
  KEY `idx_vendedor_tipo` (`id_tipo_vendedor`),
  CONSTRAINT `fk_vendedor_usuario` FOREIGN KEY (`id_vendedor`) REFERENCES `usuario` (`id_usuario`) ON DELETE CASCADE,
  CONSTRAINT `fk_vendedor_tipo` FOREIGN KEY (`id_tipo_vendedor`) REFERENCES `tipo_vendedor` (`id_tipo_vendedor`)
) ENGINE=InnoDB;

-- ===================== PREMIUM VITALICIO (vendedor) =====================
-- Fila presente = vendedor Premium para siempre. Se crea al aprobar el pago de $50, o por el admin.
-- id_pago NULL = otorgado manualmente por un administrador (ver historial_premium).
CREATE TABLE `pago` (
  `id_pago`            int NOT NULL AUTO_INCREMENT,
  `id_vendedor`        int NOT NULL,
  `id_estado_pago`     tinyint NOT NULL,
  `monto`              decimal(10,2) NOT NULL,
  `moneda`             char(3) NOT NULL DEFAULT 'MXN',
  `codigo_transaccion` varchar(100) DEFAULT NULL,
  `fecha_pago`         timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`id_pago`),
  UNIQUE KEY `uq_pago_codigo` (`codigo_transaccion`),
  KEY `idx_pago_estado_fecha` (`id_estado_pago`,`fecha_pago`),
  KEY `idx_pago_vendedor` (`id_vendedor`),
  CONSTRAINT `fk_pago_vendedor` FOREIGN KEY (`id_vendedor`) REFERENCES `vendedor` (`id_vendedor`) ON DELETE CASCADE,
  CONSTRAINT `fk_pago_estado`   FOREIGN KEY (`id_estado_pago`) REFERENCES `estado_pago` (`id_estado_pago`),
  CONSTRAINT `chk_pago_monto`   CHECK (`monto` >= 0)
) ENGINE=InnoDB;

CREATE TABLE `premium_vendedor` (
  `id_vendedor`      int NOT NULL,
  `id_pago`          int DEFAULT NULL,
  `fecha_activacion` timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`id_vendedor`),
  UNIQUE KEY `uq_premium_pago` (`id_pago`),
  CONSTRAINT `fk_premium_vendedor` FOREIGN KEY (`id_vendedor`) REFERENCES `vendedor` (`id_vendedor`) ON DELETE CASCADE,
  CONSTRAINT `fk_premium_pago` FOREIGN KEY (`id_pago`) REFERENCES `pago` (`id_pago`)
) ENGINE=InnoDB;

-- Cambios manuales del admin (otorgar / revocar premium)
CREATE TABLE `historial_premium` (
  `id_historial` int NOT NULL AUTO_INCREMENT,
  `id_admin`     int NOT NULL,
  `id_vendedor`  int NOT NULL,
  `accion`       varchar(10) NOT NULL,
  `fecha`        timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`id_historial`),
  KEY `idx_hist_admin` (`id_admin`),
  KEY `idx_hist_vendedor` (`id_vendedor`),
  CONSTRAINT `fk_hist_admin` FOREIGN KEY (`id_admin`) REFERENCES `usuario` (`id_usuario`) ON DELETE CASCADE,
  CONSTRAINT `fk_hist_vendedor` FOREIGN KEY (`id_vendedor`) REFERENCES `vendedor` (`id_vendedor`) ON DELETE CASCADE,
  CONSTRAINT `chk_hist_accion` CHECK (`accion` IN ('Otorgar','Revocar'))
) ENGINE=InnoDB;

-- ===================== SESIONES / SEGURIDAD =====================
CREATE TABLE `sesion` (
  `id_sesion`        int NOT NULL AUTO_INCREMENT,
  `id_usuario`       int NOT NULL,
  `token`            varchar(255) NOT NULL,
  `fecha_login`      timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `fecha_expiracion` timestamp NOT NULL,
  PRIMARY KEY (`id_sesion`),
  UNIQUE KEY `uq_sesion_token` (`token`),
  KEY `idx_sesion_usuario` (`id_usuario`),
  KEY `idx_sesion_expiracion` (`fecha_expiracion`),
  CONSTRAINT `fk_sesion_usuario` FOREIGN KEY (`id_usuario`) REFERENCES `usuario` (`id_usuario`) ON DELETE CASCADE
) ENGINE=InnoDB;

CREATE TABLE `recuperacion_pass` (
  `id_recuperacion`  int NOT NULL AUTO_INCREMENT,
  `id_usuario`       int NOT NULL,
  `token`            varchar(255) NOT NULL,
  `fecha_emision`    timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `fecha_expiracion` timestamp NOT NULL,
  `valido`           tinyint(1) NOT NULL DEFAULT 1,
  PRIMARY KEY (`id_recuperacion`),
  UNIQUE KEY `uq_recup_token` (`token`),
  KEY `idx_recup_usuario_fecha` (`id_usuario`,`fecha_emision`),
  CONSTRAINT `fk_recup_usuario` FOREIGN KEY (`id_usuario`) REFERENCES `usuario` (`id_usuario`) ON DELETE CASCADE
) ENGINE=InnoDB;

CREATE TABLE `registro_login` (
  `id_registro` bigint NOT NULL AUTO_INCREMENT,
  `id_usuario`  int NOT NULL,
  `fecha`       timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`id_registro`),
  KEY `idx_login_fecha` (`fecha`),
  KEY `idx_login_usuario` (`id_usuario`),
  CONSTRAINT `fk_login_usuario` FOREIGN KEY (`id_usuario`) REFERENCES `usuario` (`id_usuario`) ON DELETE CASCADE
) ENGINE=InnoDB;

CREATE TABLE `dispositivo` (
  `id_dispositivo` int NOT NULL AUTO_INCREMENT,
  `id_usuario`     int NOT NULL,
  `token_push`     varchar(255) NOT NULL,
  `plataforma`     varchar(20) DEFAULT NULL,
  `fecha_registro` timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`id_dispositivo`),
  UNIQUE KEY `uq_token_push` (`token_push`),
  KEY `idx_dispositivo_usuario` (`id_usuario`),
  CONSTRAINT `fk_dispositivo_usuario` FOREIGN KEY (`id_usuario`) REFERENCES `usuario` (`id_usuario`) ON DELETE CASCADE
) ENGINE=InnoDB;

-- ===================== CATALOGO / PRODUCTO =====================
CREATE TABLE `catalogo` (
  `id_catalogo`    int NOT NULL AUTO_INCREMENT,
  `id_vendedor`    int NOT NULL,
  `nombre`         varchar(50) NOT NULL,
  `visible`        tinyint(1) NOT NULL DEFAULT 1,
  `fecha_creacion` timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`id_catalogo`),
  UNIQUE KEY `uq_catalogo_nombre` (`id_vendedor`,`nombre`),
  CONSTRAINT `fk_catalogo_vendedor` FOREIGN KEY (`id_vendedor`) REFERENCES `vendedor` (`id_vendedor`) ON DELETE CASCADE
) ENGINE=InnoDB;

CREATE TABLE `producto` (
  `id_producto`    int NOT NULL AUTO_INCREMENT,
  `id_catalogo`    int NOT NULL,
  `id_categoria`   int NOT NULL,
  `nombre`         varchar(60) NOT NULL,
  `descripcion`    varchar(300) NOT NULL,
  `precio`         decimal(10,2) NOT NULL,
  `imagen`         text,
  `visible`        tinyint(1) NOT NULL DEFAULT 1,
  `fecha_creacion` timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`id_producto`),
  KEY `idx_producto_catalogo_fecha` (`id_catalogo`,`fecha_creacion`),
  KEY `idx_producto_categoria` (`id_categoria`),
  CONSTRAINT `fk_producto_catalogo`  FOREIGN KEY (`id_catalogo`)  REFERENCES `catalogo` (`id_catalogo`) ON DELETE CASCADE,
  CONSTRAINT `fk_producto_categoria` FOREIGN KEY (`id_categoria`) REFERENCES `categoria_producto` (`id_categoria`),
  CONSTRAINT `chk_producto_precio` CHECK (`precio` > 0)
) ENGINE=InnoDB;

-- ===================== RUTAS =====================
CREATE TABLE `ruta` (
  `id_ruta`     int NOT NULL AUTO_INCREMENT,
  `id_vendedor` int NOT NULL,
  `id_dia`      tinyint NOT NULL,
  `activa`      tinyint(1) NOT NULL DEFAULT 1,
  PRIMARY KEY (`id_ruta`),
  UNIQUE KEY `uq_ruta_dia` (`id_vendedor`,`id_dia`),
  CONSTRAINT `fk_ruta_vendedor` FOREIGN KEY (`id_vendedor`) REFERENCES `vendedor` (`id_vendedor`) ON DELETE CASCADE,
  CONSTRAINT `fk_ruta_dia` FOREIGN KEY (`id_dia`) REFERENCES `dia_semana` (`id_dia`)
) ENGINE=InnoDB;

CREATE TABLE `waypoint` (
  `id_waypoint` int NOT NULL AUTO_INCREMENT,
  `id_ruta`     int NOT NULL,
  `orden`       int NOT NULL,
  `latitud`     decimal(9,6) NOT NULL,
  `longitud`    decimal(9,6) NOT NULL,
  PRIMARY KEY (`id_waypoint`),
  UNIQUE KEY `uq_waypoint_orden` (`id_ruta`,`orden`),
  CONSTRAINT `fk_waypoint_ruta` FOREIGN KEY (`id_ruta`) REFERENCES `ruta` (`id_ruta`) ON DELETE CASCADE,
  CONSTRAINT `chk_waypoint_lat` CHECK (`latitud` BETWEEN -90 AND 90),
  CONSTRAINT `chk_waypoint_lng` CHECK (`longitud` BETWEEN -180 AND 180)
) ENGINE=InnoDB;

-- ===================== UBICACION (GPS nativo de Android) =====================
-- Ultimo punto conocido (1 fila por vendedor). Es lo que consultan los clientes.
CREATE TABLE `ubicacion_actual` (
  `id_vendedor`  int NOT NULL,
  `latitud`      decimal(9,6) NOT NULL,
  `longitud`     decimal(9,6) NOT NULL,
  `precision_m`  float DEFAULT NULL,
  `actualizado`  timestamp(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
  PRIMARY KEY (`id_vendedor`),
  KEY `idx_ubi_cercanos` (`actualizado`,`latitud`,`longitud`),
  CONSTRAINT `fk_ubi_vendedor` FOREIGN KEY (`id_vendedor`) REFERENCES `vendedor` (`id_vendedor`) ON DELETE CASCADE,
  CONSTRAINT `chk_ubi_lat` CHECK (`latitud` BETWEEN -90 AND 90),
  CONSTRAINT `chk_ubi_lng` CHECK (`longitud` BETWEEN -180 AND 180)
) ENGINE=InnoDB;

-- Jornada = turno de trabajo. "En ruta" = existe una jornada con fin IS NULL (ya no se guarda
-- un texto 'En ruta/Fuera de ruta': es un dato derivable). Una sola jornada abierta por vendedor.
CREATE TABLE `jornada` (
  `id_jornada`  bigint NOT NULL AUTO_INCREMENT,
  `id_vendedor` int NOT NULL,
  `inicio`      timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `fin`         timestamp NULL DEFAULT NULL,
  `abierta_uq`  int GENERATED ALWAYS AS (IF(`fin` IS NULL, `id_vendedor`, NULL)) VIRTUAL,
  PRIMARY KEY (`id_jornada`),
  UNIQUE KEY `uq_jornada_abierta` (`abierta_uq`),     -- impide 2 jornadas abiertas del mismo vendedor
  KEY `idx_jornada_vendedor_inicio` (`id_vendedor`,`inicio`),
  CONSTRAINT `fk_jornada_vendedor` FOREIGN KEY (`id_vendedor`) REFERENCES `vendedor` (`id_vendedor`) ON DELETE CASCADE
) ENGINE=InnoDB;

-- Puntos GPS de cada jornada (solo se guardan si el vendedor se movio >= 25 m o pasaron 60 s).
-- Retencion sugerida: 90 dias (job nocturno). Alimenta km recorridos, zonas y horas activas.
CREATE TABLE `ubicacion_historial` (
  `id_punto`    bigint NOT NULL AUTO_INCREMENT,
  `id_jornada`  bigint NOT NULL,
  `latitud`     decimal(9,6) NOT NULL,
  `longitud`    decimal(9,6) NOT NULL,
  `registrado`  timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`id_punto`),
  KEY `idx_hist_jornada_tiempo` (`id_jornada`,`registrado`),
  KEY `idx_hist_registrado` (`registrado`),
  CONSTRAINT `fk_histubi_jornada` FOREIGN KEY (`id_jornada`) REFERENCES `jornada` (`id_jornada`) ON DELETE CASCADE
) ENGINE=InnoDB;

-- ===================== INTERACCIONES (alimentan estadisticas) =====================
CREATE TABLE `tipo_evento` (
  `id_tipo_evento` tinyint NOT NULL,
  `clave`          varchar(30) NOT NULL,
  PRIMARY KEY (`id_tipo_evento`),
  UNIQUE KEY `uq_tipo_evento_clave` (`clave`)
) ENGINE=InnoDB;
INSERT INTO `tipo_evento` VALUES (1,'vista_perfil'),(2,'vista_catalogo'),(3,'vista_producto'),(4,'favorito_agregado'),(5,'favorito_quitado');

-- id_cliente NULL = visita anonima. Para zonas se usa lat/lng del CLIENTE redondeado a 3 decimales (~110 m).
CREATE TABLE `evento_vendedor` (
  `id_evento`      bigint NOT NULL AUTO_INCREMENT,
  `id_vendedor`    int NOT NULL,
  `id_tipo_evento` tinyint NOT NULL,
  `id_cliente`     int DEFAULT NULL,
  `id_producto`    int DEFAULT NULL,
  `fecha`          timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`id_evento`),
  KEY `idx_evento_vendedor_fecha` (`id_vendedor`,`id_tipo_evento`,`fecha`),
  KEY `idx_evento_producto` (`id_producto`),
  CONSTRAINT `fk_evento_vendedor` FOREIGN KEY (`id_vendedor`) REFERENCES `vendedor` (`id_vendedor`) ON DELETE CASCADE,
  CONSTRAINT `fk_evento_tipo` FOREIGN KEY (`id_tipo_evento`) REFERENCES `tipo_evento` (`id_tipo_evento`),
  CONSTRAINT `fk_evento_cliente` FOREIGN KEY (`id_cliente`) REFERENCES `cliente` (`id_cliente`) ON DELETE SET NULL,
  CONSTRAINT `fk_evento_producto` FOREIGN KEY (`id_producto`) REFERENCES `producto` (`id_producto`) ON DELETE SET NULL
) ENGINE=InnoDB;

CREATE TABLE `favorito` (
  `id_favorito` int NOT NULL AUTO_INCREMENT,
  `id_cliente`  int NOT NULL,
  `id_vendedor` int NOT NULL,
  `fecha`       timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`id_favorito`),
  UNIQUE KEY `uq_favorito` (`id_cliente`,`id_vendedor`),
  KEY `idx_favorito_vendedor` (`id_vendedor`),
  CONSTRAINT `fk_favorito_cliente`  FOREIGN KEY (`id_cliente`)  REFERENCES `cliente` (`id_cliente`) ON DELETE CASCADE,
  CONSTRAINT `fk_favorito_vendedor` FOREIGN KEY (`id_vendedor`) REFERENCES `vendedor` (`id_vendedor`) ON DELETE CASCADE
) ENGINE=InnoDB;

CREATE TABLE `notificacion_enviada` (
  `id_notificacion` bigint NOT NULL AUTO_INCREMENT,
  `id_cliente`      int NOT NULL,
  `id_vendedor`     int NOT NULL,
  `tipo`            varchar(20) NOT NULL,
  `fecha`           timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`id_notificacion`),
  KEY `idx_notif_par` (`id_cliente`,`id_vendedor`,`tipo`,`fecha`),
  KEY `idx_notif_vendedor` (`id_vendedor`),
  CONSTRAINT `fk_notif_cliente`  FOREIGN KEY (`id_cliente`)  REFERENCES `cliente` (`id_cliente`) ON DELETE CASCADE,
  CONSTRAINT `fk_notif_vendedor` FOREIGN KEY (`id_vendedor`) REFERENCES `vendedor` (`id_vendedor`) ON DELETE CASCADE
) ENGINE=InnoDB;

-- ===================== MODERACION Y SOPORTE =====================
CREATE TABLE `reporte` (
  `id_reporte`   int NOT NULL AUTO_INCREMENT,
  `id_emisor`    int NOT NULL,
  `id_reportado` int NOT NULL,
  `motivo`       varchar(500) NOT NULL,
  `fecha`        timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`id_reporte`),
  KEY `idx_reporte_fecha` (`fecha`,`id_reporte`),
  KEY `idx_reporte_par` (`id_emisor`,`id_reportado`,`fecha`),
  KEY `idx_reporte_reportado` (`id_reportado`),
  CONSTRAINT `fk_reporte_emisor` FOREIGN KEY (`id_emisor`) REFERENCES `usuario` (`id_usuario`) ON DELETE CASCADE,
  CONSTRAINT `fk_reporte_reportado` FOREIGN KEY (`id_reportado`) REFERENCES `usuario` (`id_usuario`) ON DELETE CASCADE
) ENGINE=InnoDB;

CREATE TABLE `categoria_soporte` (
  `id_categoria_soporte` tinyint NOT NULL,
  `clave`                varchar(20) NOT NULL,
  PRIMARY KEY (`id_categoria_soporte`),
  UNIQUE KEY `uq_cat_soporte_clave` (`clave`)
) ENGINE=InnoDB;
INSERT INTO `categoria_soporte` VALUES (1,'queja'),(2,'peticion'),(3,'sugerencia'),(4,'tecnico'),(5,'otro');

CREATE TABLE `estado_soporte` (
  `id_estado_soporte` tinyint NOT NULL,
  `clave`             varchar(20) NOT NULL,
  PRIMARY KEY (`id_estado_soporte`),
  UNIQUE KEY `uq_estado_soporte_clave` (`clave`)
) ENGINE=InnoDB;
INSERT INTO `estado_soporte` VALUES (1,'pendiente'),(2,'en_revision'),(3,'atendida');

CREATE TABLE `solicitud_soporte` (
  `id_solicitud`         int NOT NULL AUTO_INCREMENT,
  `id_usuario`           int NOT NULL,
  `id_categoria_soporte` tinyint NOT NULL,
  `id_estado_soporte`    tinyint NOT NULL DEFAULT 1,
  `asunto`               varchar(120) NOT NULL,
  `descripcion`          varchar(1000) NOT NULL,
  `fecha_creacion`       timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`id_solicitud`),
  KEY `idx_solicitud_usuario_fecha` (`id_usuario`,`fecha_creacion`),
  CONSTRAINT `fk_solicitud_usuario`   FOREIGN KEY (`id_usuario`) REFERENCES `usuario` (`id_usuario`) ON DELETE CASCADE,
  CONSTRAINT `fk_solicitud_categoria` FOREIGN KEY (`id_categoria_soporte`) REFERENCES `categoria_soporte` (`id_categoria_soporte`),
  CONSTRAINT `fk_solicitud_estado`    FOREIGN KEY (`id_estado_soporte`) REFERENCES `estado_soporte` (`id_estado_soporte`)
) ENGINE=InnoDB;

CREATE TABLE `mensaje_contacto` (
  `id_mensaje`     int NOT NULL AUTO_INCREMENT,
  `nombre`         varchar(100) NOT NULL,
  `correo`         varchar(100) NOT NULL,
  `mensaje`        varchar(1000) NOT NULL,
  `fecha_creacion` timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`id_mensaje`),
  KEY `idx_mensaje_fecha` (`fecha_creacion`,`id_mensaje`)
) ENGINE=InnoDB;

SET FOREIGN_KEY_CHECKS = @OLD_FOREIGN_KEY_CHECKS;

-- ===================== VISTA DE LECTURA (comodidad, NO se escribe) =====================
-- es_premium se DERIVA de premium_vendedor; asi nunca se desincroniza.
CREATE OR REPLACE VIEW `vw_usuario` AS
SELECT u.id_usuario,
       CONCAT(u.nombre,' ',u.apellidos) AS nombre_completo,
       u.nombre, u.apellidos, u.correo, u.contrasena, u.telefono, u.foto_perfil, u.zona_horaria, u.fecha_creacion,
       r.nombre  AS rol,
       ec.nombre AS estado_cuenta,
       s.fin     AS fin_suspension,
       v.id_tipo_vendedor,
       tv.clave  AS tipo_vendedor,
       tv.icono  AS icono_vendedor,
       (pv.id_vendedor IS NOT NULL) AS es_premium,
       pv.fecha_activacion AS premium_desde
FROM usuario u
JOIN rol r ON r.id_rol = u.id_rol
JOIN estado_cuenta ec ON ec.id_estado_cuenta = u.id_estado_cuenta
LEFT JOIN suspension s ON s.id_usuario = u.id_usuario
LEFT JOIN vendedor v ON v.id_vendedor = u.id_usuario
LEFT JOIN tipo_vendedor tv ON tv.id_tipo_vendedor = v.id_tipo_vendedor
LEFT JOIN premium_vendedor pv ON pv.id_vendedor = u.id_usuario;