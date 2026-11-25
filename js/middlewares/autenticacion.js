const MSG = require('../utils/mensajes');
const { asyncHandler } = require('../utils/errores');
const sesiones = require('../services/sesionesService');

/**
 * Valida el token Bearer (JWT firmado + sesion viva en la tabla sesion, RF2.1) y rechaza cuentas
 * suspendidas (RF22.4). La logica vive en sesionesService.validarToken para compartirla con el
 * canal WebSocket. Deja en req.usuario los datos de la cuenta con el rol ACTUAL de la BD.
 */
const verificarSesion = asyncHandler(async (req, res, next) => {
  const [esquema, token] = (req.headers.authorization || '').split(' ');
  if (esquema !== 'Bearer' || !token) {
    return res.status(401).json({ mensaje: MSG.SESION_INVALIDA });
  }

  const s = await sesiones.validarToken(token); // lanza ErrorHttp 401 / 403
  req.idSesion = s.idSesion;
  req.jti = s.jti;
  req.usuario = s.usuario;
  next();
});

const verificarSesionOpcional = asyncHandler(async (req, res, next) => {
  const authorization = req.headers.authorization;
  if (!authorization) return next();
  const [esquema, token, extra] = authorization.split(' ');
  if (esquema !== 'Bearer' || !token || extra) {
    return res.status(401).json({ mensaje: MSG.SESION_INVALIDA });
  }
  const sesion = await sesiones.validarToken(token);
  req.idSesion = sesion.idSesion;
  req.jti = sesion.jti;
  req.usuario = sesion.usuario;
  next();
});

/** Uso: verificarRol('Administrador')  /  verificarRol('Vendedor Gratuito', 'Vendedor Premium') */
const verificarRol = (...rolesPermitidos) => (req, res, next) => {
  if (!req.usuario || !rolesPermitidos.includes(req.usuario.rol)) {
    return res.status(403).json({ mensaje: MSG.SIN_PERMISO });
  }
  next();
};

const verificarTipo = (...tiposPermitidos) => (req, res, next) => {
  if (!req.usuario || !tiposPermitidos.includes(req.usuario.tipo)) {
    return res.status(403).json({ mensaje: MSG.SIN_PERMISO });
  }
  next();
};

module.exports = {
  verificarSesion, verificarSesionOpcional, verificarRol, verificarTipo,
  reactivarSiVencio: sesiones.reactivarSiVencio,
};
