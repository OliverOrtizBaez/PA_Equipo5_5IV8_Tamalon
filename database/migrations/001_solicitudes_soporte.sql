-- Migración no destructiva para bases de datos ya creadas.
CREATE TABLE IF NOT EXISTS `solicitudes_soporte` (
  `id_solicitud`   int NOT NULL AUTO_INCREMENT,
  `id_usuario`     int NOT NULL,
  `rol_emisor`     varchar(30) NOT NULL,
  `categoria`      varchar(20) NOT NULL,
  `asunto`         varchar(120) NOT NULL,
  `descripcion`    varchar(1000) NOT NULL,
  `estado`         varchar(20) NOT NULL DEFAULT 'pendiente',
  `fecha_creacion` timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`id_solicitud`),
  KEY `idx_solicitud_soporte_usuario_fecha` (`id_usuario`, `fecha_creacion`),
  CONSTRAINT `fk_solicitud_soporte_usuario` FOREIGN KEY (`id_usuario`) REFERENCES `usuarios` (`id_usuario`) ON DELETE CASCADE,
  CONSTRAINT `chk_solicitud_soporte_categoria` CHECK (`categoria` IN ('queja','peticion','sugerencia','tecnico','otro')),
  CONSTRAINT `chk_solicitud_soporte_estado` CHECK (`estado` IN ('pendiente','en_revision','atendida'))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
