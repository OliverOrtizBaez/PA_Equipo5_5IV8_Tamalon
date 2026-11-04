const MSG = require('../utils/mensajes');
const { ErrorHttp } = require('../utils/errores');

function noEncontrado(req, res) {
  res.status(404).json({ mensaje: MSG.NO_ENCONTRADO });
}

// eslint-disable-next-line no-unused-vars
function manejadorErrores(err, req, res, next) {
  if (err instanceof ErrorHttp) {
    return res.status(err.status).json({ mensaje: err.message, ...err.extra });
  }
  // JSON invalido en el cuerpo de la peticion
  if (err && (err.type === 'entity.parse.failed' || err.type === 'entity.too.large')) {
    return res.status(err.status || 400).json({ mensaje: MSG.SOLICITUD_MAL_FORMADA });
  }
  console.error(`[error] ${req.method} ${req.originalUrl}`, err); // RF25.16: el detalle solo va al log
  res.status(500).json({ mensaje: MSG.ERROR_INESPERADO });
}

module.exports = { noEncontrado, manejadorErrores };
