// Valores de negocio tomados de los requerimientos. Se centralizan para ajustarlos en un solo lugar.
const TIPOS = {
  VENDEDOR: 'Vendedor',
  CLIENTE: 'Cliente',
  ADMIN: 'Administrador',
};

const ESTADOS_CUENTA = {
  ACTIVA: 'Activa',
  SUSPENDIDA_INDEF: 'Suspendida indefinidamente',
  SUSPENDIDA_TEMP: 'Suspendida temporalmente',
};

module.exports = {
  TIPOS,
  ESTADOS_CUENTA,
  ZONA_NEGOCIO: 'America/Mexico_City',       // ZH-N
  OFFSET_ZHN: '-06:00',                        // sin horario de verano
  SESION_DIAS: 7,                              // RF2.1
  MAX_INTENTOS_LOGIN: 5,                       // RF2.3b
  VENTANA_INTENTOS_MIN: 15,                    // RF2.3b
  BLOQUEO_MIN: 15,                             // RF2.3b
  RECUPERACION_MAX_SOLICITUDES: 3,             // RF3.1b
  RECUPERACION_VENTANA_MIN: 60,                // RF3.1b
  RECUPERACION_VIGENCIA_MIN: 30,               // RF3.3
  PRECIO_PREMIUM: 50.00,
  LIMITE_FAVORITOS: 5,
  MIN_GPS_METROS: 25,
  MIN_GPS_SEG: 60,
  LIMITE_CATALOGOS_PREMIUM: 3,                 // RF10.2
  LIMITE_PRODUCTOS_GRATIS: 10,                 // RF11.1
  LIMITE_PRODUCTOS_PREMIUM: 50,                // RF11.2
  POR_PAGINA: 20,                              // RF21.1 / RF24
  RETENCION_LOGIN_MESES: 12,                   // RF20a
  UBICACION_VIGENCIA_SEG: 60,
};
