// Mensajes exactos de los requerimientos RF25.x (y de RF22 / RF2.3).
module.exports = {
  CORREO_REPETIDO: 'Este correo ya está registrado',                                   // RF25.2
  ENLACE_VENCIDO: 'El enlace ya no es válido. Solicita uno nuevo',                     // RF25.11
  RECUPERACION_ENVIADA: 'Si el correo está registrado, recibirás un enlace de restablecimiento', // RF25.12
  ERROR_INESPERADO: 'Ocurrió un error inesperado. Intenta de nuevo en unos minutos',   // RF25.16
  SIN_USUARIOS: 'No se encontraron usuarios',                                          // RF25.17
  RANGO_FECHAS_INVALIDO: 'La fecha inicial no puede ser posterior a la final',         // RF25.18
  SIN_REPORTES: 'No hay reportes registrados',                                         // RF25.19
  BLOQUEO_TEMPORAL: 'Demasiados intentos. Intenta de nuevo en 15 minutos',             // RF25.21
  SIN_TRAFICO: 'No hay inicios de sesión en la fecha seleccionada',                    // RF25.25
  OPERACION_NO_PERMITIDA: 'No puedes suspender ni cambiar la suscripción de una cuenta de Administrador', // RF25.26
  CREDENCIALES_INCORRECTAS: 'Correo o contraseña incorrectos',                         // RF2.3
  USUARIO_SUSPENDIDO: 'Usuario suspendido',                                            // RF22.4 / RF22.5
  // Mensajes propios (no definidos en los RF)
  SESION_INVALIDA: 'Sesión no válida. Inicia sesión de nuevo',
  SIN_PERMISO: 'No tienes permiso para realizar esta acción',
  NO_ENCONTRADO: 'Recurso no encontrado',
  CUENTA_NO_ENCONTRADA: 'Cuenta no encontrada',
  SOLICITUD_MAL_FORMADA: 'La solicitud está mal formada',
  UBICACION_INVALIDA: 'Latitud y longitud deben ser números válidos dentro de sus rangos',
  FILTRO_UBICACION_INVALIDO: 'Los filtros de ubicación no son válidos',
  VENDEDOR_NO_ENCONTRADO: 'Vendedor no encontrado',
};
