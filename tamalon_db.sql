-- MySQL dump 10.13  Distrib 8.4.10, for Linux (x86_64)
--
-- Host: 127.0.0.1    Database: tamalon_db
-- ------------------------------------------------------
-- Server version	8.4.10

/*!40101 SET @OLD_CHARACTER_SET_CLIENT=@@CHARACTER_SET_CLIENT */;
/*!40101 SET @OLD_CHARACTER_SET_RESULTS=@@CHARACTER_SET_RESULTS */;
/*!40101 SET @OLD_COLLATION_CONNECTION=@@COLLATION_CONNECTION */;
/*!50503 SET NAMES utf8mb4 */;
/*!40103 SET @OLD_TIME_ZONE=@@TIME_ZONE */;
/*!40103 SET TIME_ZONE='+00:00' */;
/*!40014 SET @OLD_UNIQUE_CHECKS=@@UNIQUE_CHECKS, UNIQUE_CHECKS=0 */;
/*!40014 SET @OLD_FOREIGN_KEY_CHECKS=@@FOREIGN_KEY_CHECKS, FOREIGN_KEY_CHECKS=0 */;
/*!40101 SET @OLD_SQL_MODE=@@SQL_MODE, SQL_MODE='NO_AUTO_VALUE_ON_ZERO' */;
/*!40111 SET @OLD_SQL_NOTES=@@SQL_NOTES, SQL_NOTES=0 */;

--
-- Table structure for table `catalogo`
--

DROP TABLE IF EXISTS `catalogo`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `catalogo` (
  `id_catalogo` int NOT NULL AUTO_INCREMENT,
  `id_vendedor` int NOT NULL,
  `nombre` varchar(50) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL,
  `estado` varchar(20) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL,
  `fech_creacion` timestamp NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`id_catalogo`),
  KEY `fk_catalogo_vendedor` (`id_vendedor`),
  CONSTRAINT `fk_catalogo_vendedor` FOREIGN KEY (`id_vendedor`) REFERENCES `usuarios` (`id_usuario`) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `catalogo`
--

LOCK TABLES `catalogo` WRITE;
/*!40000 ALTER TABLE `catalogo` DISABLE KEYS */;
/*!40000 ALTER TABLE `catalogo` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Table structure for table `dispositivo`
--

DROP TABLE IF EXISTS `dispositivo`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `dispositivo` (
  `id_dispositivo` int NOT NULL AUTO_INCREMENT,
  `id_usuario` int NOT NULL,
  `token_push` varchar(255) COLLATE utf8mb4_unicode_ci NOT NULL,
  `plataforma` varchar(20) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `fech_registro` timestamp NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`id_dispositivo`),
  UNIQUE KEY `uq_token_push` (`token_push`),
  KEY `fk_dispositivo_usuario` (`id_usuario`),
  CONSTRAINT `fk_dispositivo_usuario` FOREIGN KEY (`id_usuario`) REFERENCES `usuarios` (`id_usuario`) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `dispositivo`
--

LOCK TABLES `dispositivo` WRITE;
/*!40000 ALTER TABLE `dispositivo` DISABLE KEYS */;
/*!40000 ALTER TABLE `dispositivo` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Table structure for table `favorito`
--

DROP TABLE IF EXISTS `favorito`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `favorito` (
  `id_favorito` int NOT NULL AUTO_INCREMENT,
  `id_cliente` int NOT NULL,
  `id_vendedor` int NOT NULL,
  PRIMARY KEY (`id_favorito`),
  KEY `fk_favorito_cliente` (`id_cliente`),
  KEY `fk_favorito_vendedor` (`id_vendedor`),
  CONSTRAINT `fk_favorito_cliente` FOREIGN KEY (`id_cliente`) REFERENCES `usuarios` (`id_usuario`) ON DELETE CASCADE,
  CONSTRAINT `fk_favorito_vendedor` FOREIGN KEY (`id_vendedor`) REFERENCES `usuarios` (`id_usuario`) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `favorito`
--

LOCK TABLES `favorito` WRITE;
/*!40000 ALTER TABLE `favorito` DISABLE KEYS */;
/*!40000 ALTER TABLE `favorito` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Table structure for table `historial_rol`
--

DROP TABLE IF EXISTS `historial_rol`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `historial_rol` (
  `id_historial` int NOT NULL AUTO_INCREMENT,
  `id_admin` int NOT NULL,
  `id_usuario` int NOT NULL,
  `rol_anterior` varchar(30) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL,
  `rol_nuevo` varchar(30) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL,
  `fech_hrs` timestamp NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`id_historial`),
  KEY `fk_historial_admin` (`id_admin`),
  KEY `fk_historial_usuario` (`id_usuario`),
  CONSTRAINT `fk_historial_admin` FOREIGN KEY (`id_admin`) REFERENCES `usuarios` (`id_usuario`) ON DELETE CASCADE,
  CONSTRAINT `fk_historial_usuario` FOREIGN KEY (`id_usuario`) REFERENCES `usuarios` (`id_usuario`) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `historial_rol`
--

LOCK TABLES `historial_rol` WRITE;
/*!40000 ALTER TABLE `historial_rol` DISABLE KEYS */;
/*!40000 ALTER TABLE `historial_rol` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Table structure for table `notificacion_enviada`
--

DROP TABLE IF EXISTS `notificacion_enviada`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `notificacion_enviada` (
  `id_notificacion` bigint NOT NULL AUTO_INCREMENT,
  `id_cliente` int NOT NULL,
  `id_vendedor` int NOT NULL,
  `tipo` varchar(20) COLLATE utf8mb4_unicode_ci NOT NULL,
  `fech_hrs` timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`id_notificacion`),
  KEY `idx_notif_par` (`id_cliente`,`id_vendedor`,`tipo`,`fech_hrs`),
  KEY `fk_notif_vendedor` (`id_vendedor`),
  CONSTRAINT `fk_notif_cliente` FOREIGN KEY (`id_cliente`) REFERENCES `usuarios` (`id_usuario`) ON DELETE CASCADE,
  CONSTRAINT `fk_notif_vendedor` FOREIGN KEY (`id_vendedor`) REFERENCES `usuarios` (`id_usuario`) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `notificacion_enviada`
--

LOCK TABLES `notificacion_enviada` WRITE;
/*!40000 ALTER TABLE `notificacion_enviada` DISABLE KEYS */;
/*!40000 ALTER TABLE `notificacion_enviada` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Table structure for table `producto`
--

DROP TABLE IF EXISTS `producto`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `producto` (
  `id_producto` int NOT NULL AUTO_INCREMENT,
  `id_catalogo` int NOT NULL,
  `nombre` varchar(60) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL,
  `descripcion` varchar(300) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL,
  `precio` decimal(10,2) NOT NULL,
  `imagen` text CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci,
  `estado` varchar(20) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL,
  `categoria` varchar(50) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL,
  `fech_creacion` timestamp NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`id_producto`),
  KEY `fk_producto_catalogo` (`id_catalogo`),
  CONSTRAINT `fk_producto_catalogo` FOREIGN KEY (`id_catalogo`) REFERENCES `catalogo` (`id_catalogo`) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `producto`
--

LOCK TABLES `producto` WRITE;
/*!40000 ALTER TABLE `producto` DISABLE KEYS */;
/*!40000 ALTER TABLE `producto` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Table structure for table `recuperacion_pass`
--

DROP TABLE IF EXISTS `recuperacion_pass`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `recuperacion_pass` (
  `id_recuperacion` int NOT NULL AUTO_INCREMENT,
  `id_usuario` int NOT NULL,
  `token` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL,
  `fech_emision` timestamp NULL DEFAULT CURRENT_TIMESTAMP,
  `fecha_expiracion` timestamp NOT NULL,
  `valido` tinyint(1) DEFAULT '1',
  PRIMARY KEY (`id_recuperacion`),
  UNIQUE KEY `token_unico` (`token`),
  KEY `fk_recup_usuario` (`id_usuario`),
  CONSTRAINT `fk_recup_usuario` FOREIGN KEY (`id_usuario`) REFERENCES `usuarios` (`id_usuario`) ON DELETE CASCADE
) ENGINE=InnoDB AUTO_INCREMENT=16 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `recuperacion_pass`
--

LOCK TABLES `recuperacion_pass` WRITE;
/*!40000 ALTER TABLE `recuperacion_pass` DISABLE KEYS */;
INSERT INTO `recuperacion_pass` VALUES (14,1,'72fb9c1737098b547c11962b6dd42d19321e4da90f9696b7eb268159d275e304','2026-11-05 00:42:42','2026-11-05 01:12:42',1),(15,1,'b26a8a2fdd5832917592630fe7c73414f8d05385d352b711a90449d0f0269512','2026-11-05 00:44:23','2026-11-05 01:14:23',1);
/*!40000 ALTER TABLE `recuperacion_pass` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Table structure for table `registro_login`
--

DROP TABLE IF EXISTS `registro_login`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `registro_login` (
  `id_registro` bigint NOT NULL AUTO_INCREMENT,
  `id_usuario` int NOT NULL,
  `rol` varchar(30) COLLATE utf8mb4_unicode_ci NOT NULL,
  `fech_hrs` timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`id_registro`),
  KEY `idx_registro_login_fecha` (`fech_hrs`),
  KEY `fk_registro_login_usuario` (`id_usuario`),
  CONSTRAINT `fk_registro_login_usuario` FOREIGN KEY (`id_usuario`) REFERENCES `usuarios` (`id_usuario`) ON DELETE CASCADE
) ENGINE=InnoDB AUTO_INCREMENT=10 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `registro_login`
--

LOCK TABLES `registro_login` WRITE;
/*!40000 ALTER TABLE `registro_login` DISABLE KEYS */;
INSERT INTO `registro_login` VALUES (1,1,'Administrador','2026-10-28 01:37:26'),(2,1,'Administrador','2026-10-28 01:43:39'),(3,1,'Administrador','2026-10-28 01:46:08'),(4,1,'Administrador','2026-10-28 02:28:29'),(5,1,'Administrador','2026-10-28 21:18:51'),(6,1,'Administrador','2026-11-04 02:29:33'),(7,1,'Administrador','2026-11-04 02:38:03'),(8,1,'Administrador','2026-11-04 21:43:54'),(9,1,'Administrador','2026-11-06 01:10:34');
/*!40000 ALTER TABLE `registro_login` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Table structure for table `reporte`
--

DROP TABLE IF EXISTS `reporte`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `reporte` (
  `id_reporte` int NOT NULL AUTO_INCREMENT,
  `id_emisor` int NOT NULL,
  `id_reportado` int NOT NULL,
  `motivo` varchar(500) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL,
  `fech_hrs` timestamp NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`id_reporte`),
  KEY `fk_reporte_emisor` (`id_emisor`),
  KEY `fk_reporte_reportado` (`id_reportado`),
  CONSTRAINT `fk_reporte_emisor` FOREIGN KEY (`id_emisor`) REFERENCES `usuarios` (`id_usuario`) ON DELETE CASCADE,
  CONSTRAINT `fk_reporte_reportado` FOREIGN KEY (`id_reportado`) REFERENCES `usuarios` (`id_usuario`) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `reporte`
--

LOCK TABLES `reporte` WRITE;
/*!40000 ALTER TABLE `reporte` DISABLE KEYS */;
/*!40000 ALTER TABLE `reporte` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Table structure for table `rol`
--

DROP TABLE IF EXISTS `rol`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `rol` (
  `id_rol` int NOT NULL,
  `nombre` varchar(45) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL,
  `nombre_rol` varchar(45) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL,
  `descripcion` varchar(45) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL,
  PRIMARY KEY (`id_rol`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `rol`
--

LOCK TABLES `rol` WRITE;
/*!40000 ALTER TABLE `rol` DISABLE KEYS */;
/*!40000 ALTER TABLE `rol` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Table structure for table `ruta`
--

DROP TABLE IF EXISTS `ruta`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `ruta` (
  `id_ruta` int NOT NULL AUTO_INCREMENT,
  `id_vendedor` int NOT NULL,
  `estado` varchar(20) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL,
  `dia_semana` varchar(20) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL,
  PRIMARY KEY (`id_ruta`),
  KEY `fk_ruta_vendedor` (`id_vendedor`),
  CONSTRAINT `fk_ruta_vendedor` FOREIGN KEY (`id_vendedor`) REFERENCES `usuarios` (`id_usuario`) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `ruta`
--

LOCK TABLES `ruta` WRITE;
/*!40000 ALTER TABLE `ruta` DISABLE KEYS */;
/*!40000 ALTER TABLE `ruta` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Table structure for table `sesion`
--

DROP TABLE IF EXISTS `sesion`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `sesion` (
  `id_sesion` int NOT NULL AUTO_INCREMENT,
  `id_usuario` int NOT NULL,
  `token` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL,
  `fech_hrs_login` timestamp NULL DEFAULT CURRENT_TIMESTAMP,
  `fecha_expiracion` timestamp NOT NULL,
  PRIMARY KEY (`id_sesion`),
  UNIQUE KEY `token_unico` (`token`),
  KEY `fk_sesion_usuario` (`id_usuario`),
  CONSTRAINT `fk_sesion_usuario` FOREIGN KEY (`id_usuario`) REFERENCES `usuarios` (`id_usuario`) ON DELETE CASCADE
) ENGINE=InnoDB AUTO_INCREMENT=5 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `sesion`
--

LOCK TABLES `sesion` WRITE;
/*!40000 ALTER TABLE `sesion` DISABLE KEYS */;
INSERT INTO `sesion` VALUES (1,1,'463ebb7b7933dd44d2620268091f6b7f695b955e7367459f8ce1b626724894e7','2026-11-04 02:29:33','2026-11-11 02:29:33'),(2,1,'b88860fc10d00db3c2d482060c479bbe5e12eeacc71de5a0cc856ae6ed2cf1d2','2026-11-04 02:38:03','2026-11-11 02:38:03'),(3,1,'76dbe8535c3f0e1ef15023f7eba774f39b28356b1d3aa67652a48aec6ab3b9d0','2026-11-04 21:43:54','2026-11-11 21:43:54');
/*!40000 ALTER TABLE `sesion` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Table structure for table `solicitud_soporte`
--

DROP TABLE IF EXISTS `solicitud_soporte`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `solicitud_soporte` (
  `id_solicitud` int NOT NULL AUTO_INCREMENT,
  `id_emisor` int NOT NULL,
  `categoria` varchar(50) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL,
  `asunto` varchar(120) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL,
  `descripcion` text CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL,
  `estado` varchar(30) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL DEFAULT 'pendiente',
  `fech_creacion` timestamp NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`id_solicitud`),
  KEY `fk_soporte_usuario` (`id_emisor`),
  CONSTRAINT `fk_soporte_usuario` FOREIGN KEY (`id_emisor`) REFERENCES `usuarios` (`id_usuario`) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `solicitud_soporte`
--

LOCK TABLES `solicitud_soporte` WRITE;
/*!40000 ALTER TABLE `solicitud_soporte` DISABLE KEYS */;
/*!40000 ALTER TABLE `solicitud_soporte` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Table structure for table `suscripcion`
--

DROP TABLE IF EXISTS `suscripcion`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `suscripcion` (
  `id_suscripcion` int NOT NULL AUTO_INCREMENT,
  `id_usuario` int NOT NULL,
  `estado_pago` varchar(20) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL,
  `suscrs_monto` decimal(10,2) NOT NULL,
  `moneda` varchar(10) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL DEFAULT 'MXN',
  `fech_pago` timestamp NULL DEFAULT CURRENT_TIMESTAMP,
  `fech_corte` timestamp NOT NULL,
  `renovacion` tinyint(1) DEFAULT '1',
  PRIMARY KEY (`id_suscripcion`),
  KEY `fk_suscripcion_usuario` (`id_usuario`),
  CONSTRAINT `fk_suscripcion_usuario` FOREIGN KEY (`id_usuario`) REFERENCES `usuarios` (`id_usuario`) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `suscripcion`
--

LOCK TABLES `suscripcion` WRITE;
/*!40000 ALTER TABLE `suscripcion` DISABLE KEYS */;
/*!40000 ALTER TABLE `suscripcion` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Table structure for table `ubi_vendedor`
--

DROP TABLE IF EXISTS `ubi_vendedor`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `ubi_vendedor` (
  `id_ubicacion` int NOT NULL AUTO_INCREMENT,
  `id_vendedor` int NOT NULL,
  `latitud` double NOT NULL,
  `longitud` double NOT NULL,
  `disponibilidad` varchar(20) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL,
  `ultima_actualizacion` timestamp NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id_ubicacion`),
  UNIQUE KEY `id_vendedor` (`id_vendedor`),
  CONSTRAINT `fk_ubicacion_vendedor` FOREIGN KEY (`id_vendedor`) REFERENCES `usuarios` (`id_usuario`) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `ubi_vendedor`
--

LOCK TABLES `ubi_vendedor` WRITE;
/*!40000 ALTER TABLE `ubi_vendedor` DISABLE KEYS */;
/*!40000 ALTER TABLE `ubi_vendedor` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Table structure for table `usuarios`
--

DROP TABLE IF EXISTS `usuarios`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `usuarios` (
  `id_usuario` int NOT NULL AUTO_INCREMENT,
  `nombre_completo` varchar(100) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL,
  `contrasena` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL,
  `correo` varchar(100) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL,
  `telefono` varchar(10) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `foto_perfil` text CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci,
  `rol` varchar(30) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL,
  `estado_cuenta` varchar(30) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL,
  `zona_horaria` varchar(50) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL,
  `fecha_creacion` timestamp NULL DEFAULT CURRENT_TIMESTAMP,
  `fin_suspension` timestamp NULL DEFAULT NULL,
  `intentos_fallidos` int DEFAULT '0',
  `ultimo_intento` timestamp NULL DEFAULT NULL,
  `inicio_intentos` timestamp NULL DEFAULT NULL,
  PRIMARY KEY (`id_usuario`),
  UNIQUE KEY `correo` (`correo`)
) ENGINE=InnoDB AUTO_INCREMENT=3 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `usuarios`
--

LOCK TABLES `usuarios` WRITE;
/*!40000 ALTER TABLE `usuarios` DISABLE KEYS */;
INSERT INTO `usuarios` VALUES (1,'Administrador Principal','$2b$10$xbXBx7zKtljxyjZ921AO8eyhUg5gqf7SRm2Z47eMO59e84KNdle86','oli.ortizbaez@gmail.com',NULL,NULL,'Administrador','Activa','America/Mexico_City','2026-11-04 02:19:18',NULL,0,NULL,NULL),(2,'Ivak','$2b$10$ACVXyqsWvAOQ/hX/QBS4S.EDiAbjrEatRluaf8.TU0lbnsK2lZMOu','prepacorreroiv1@gmail.com',NULL,NULL,'Administrador','Activa','America/Mexico_City','2026-11-04 21:44:44',NULL,0,NULL,NULL);
/*!40000 ALTER TABLE `usuarios` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Table structure for table `waypoint`
--

DROP TABLE IF EXISTS `waypoint`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `waypoint` (
  `id_waypoint` int NOT NULL AUTO_INCREMENT,
  `id_ruta` int NOT NULL,
  `orden` int NOT NULL,
  `latitud` double NOT NULL,
  `longitud` double NOT NULL,
  PRIMARY KEY (`id_waypoint`),
  KEY `fk_waypoint_ruta` (`id_ruta`),
  CONSTRAINT `fk_waypoint_ruta` FOREIGN KEY (`id_ruta`) REFERENCES `ruta` (`id_ruta`) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `waypoint`
--

LOCK TABLES `waypoint` WRITE;
/*!40000 ALTER TABLE `waypoint` DISABLE KEYS */;
/*!40000 ALTER TABLE `waypoint` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Dumping routines for database 'tamalon_db'
--
/*!40103 SET TIME_ZONE=@OLD_TIME_ZONE */;

/*!40101 SET SQL_MODE=@OLD_SQL_MODE */;
/*!40014 SET FOREIGN_KEY_CHECKS=@OLD_FOREIGN_KEY_CHECKS */;
/*!40014 SET UNIQUE_CHECKS=@OLD_UNIQUE_CHECKS */;
/*!40101 SET CHARACTER_SET_CLIENT=@OLD_CHARACTER_SET_CLIENT */;
/*!40101 SET CHARACTER_SET_RESULTS=@OLD_CHARACTER_SET_RESULTS */;
/*!40101 SET COLLATION_CONNECTION=@OLD_COLLATION_CONNECTION */;
/*!40111 SET SQL_NOTES=@OLD_SQL_NOTES */;

-- Dump completed on 2026-11-05 20:14:53
