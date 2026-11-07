-- Índice de apoyo para filtrar disponibilidad, vigencia y caja geográfica.
ALTER TABLE `ubi_vendedor`
  ADD INDEX `idx_ubi_cercanos` (`disponibilidad`, `ultima_actualizacion`, `latitud`, `longitud`);
