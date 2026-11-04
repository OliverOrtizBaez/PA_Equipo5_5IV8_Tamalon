const { pool } = require('../config/db');
const C = require('../config/constantes');
const { ErrorHttp, asyncHandler } = require('../utils/errores');
const S = require('../services/suscripcionService');

const obtenerSuscripcion = asyncHandler(async (req, res) => {
  const [[usuario]] = await pool.query(
    'SELECT rol, fecha_corte, renovacion FROM usuarios WHERE id_usuario = ? LIMIT 1',
    [req.usuario.id]
  );
  if (!usuario) throw new ErrorHttp(404, 'La cuenta no existe');

  const [pagos] = await pool.query(
    `SELECT id_suscripcion, tipo, estado_pago, suscrs_monto, moneda, codigo_transaccion, fech_pago
       FROM suscripcion
      WHERE id_usuario = ?
      ORDER BY fech_pago DESC, id_suscripcion DESC`,
    [req.usuario.id]
  );
  res.json({
    rol: usuario.rol,
    plan: S.esPremium(usuario.rol) ? 'Premium' : 'Gratuito',
    fecha_corte: usuario.fecha_corte,
    renovacion: Boolean(usuario.renovacion),
    pagos,
  });
});

const procesarPagoPremium = asyncHandler(async (req, res) => {
  if (![C.ROLES.VENDEDOR_GRATUITO, C.ROLES.VENDEDOR_PREMIUM, C.ROLES.CLIENTE_GRATUITO, C.ROLES.CLIENTE_PREMIUM]
    .includes(req.usuario.rol)) {
    throw new ErrorHttp(403, 'Las cuentas de administrador no pueden contratar una suscripción');
  }

  throw new ErrorHttp(
    503,
    'Los pagos todavía no están habilitados. Configura la integración del proveedor y su verificación en el servidor antes de activar Premium.'
  );
});

module.exports = { obtenerSuscripcion, procesarPagoPremium };
