-- Mensajes enviados desde el formulario público de contacto de la landing.
CREATE TABLE IF NOT EXISTS `mensaje_contacto` (
  `id_mensaje`      int NOT NULL AUTO_INCREMENT,
  `nombre`          varchar(100) NOT NULL,
  `correo`          varchar(100) NOT NULL,
  `mensaje`         varchar(1000) NOT NULL,
  `fecha_creacion`  timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`id_mensaje`),
  KEY `idx_mensaje_contacto_fecha` (`fecha_creacion`, `id_mensaje`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
