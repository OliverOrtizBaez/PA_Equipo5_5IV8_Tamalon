const crypto = require('crypto');
const { pool, transaccion } = require('../config/db');
const C = require('../config/constantes');
const MSG = require('../utils/mensajes');
const { ErrorHttp, ErrorValidacion, asyncHandler } = require('../utils/errores');
const S = require('../services/suscripcionService');

const obtenerSuscripcion = asyncHandler(async (req, res) => {
  let esPremium = false;
  let premiumDesde = null;
  if (req.usuario.tipo === C.TIPOS.VENDEDOR) {
    const [[estado]] = await pool.query(
      'SELECT es_premium, premium_desde FROM vw_usuario WHERE id_usuario = ?',
      [req.usuario.id]
    );
    if (!estado) throw new ErrorHttp(404, 'La cuenta no existe');
    esPremium = Boolean(estado.es_premium);
    premiumDesde = estado.premium_desde;
  }
  res.json({
    es_premium: esPremium,
    premium_desde: premiumDesde,
    precio: C.PRECIO_PREMIUM,
    moneda: 'MXN',
    pago_demo_habilitado: process.env.PAGOS_MODO === 'demo',
  });
});

const procesarPagoPremium = asyncHandler(async (req, res) => {
  if (req.usuario.tipo !== C.TIPOS.VENDEDOR) {
    throw new ErrorHttp(403, 'Solo los vendedores pueden adquirir Premium');
  }
  if (process.env.PAGOS_MODO !== 'demo') {
    throw new ErrorHttp(503, MSG.PAGO_SIMULADO_NO_HABILITADO);
  }

  const body = req.body;
  if (body !== undefined && (!body || typeof body !== 'object' || Array.isArray(body))) {
    throw new ErrorHttp(400, MSG.RESULTADO_PAGO_INVALIDO);
  }
  const resultado = body?.resultado ?? 'aprobado';
  if (!['aprobado', 'rechazado'].includes(resultado)) {
    throw new ErrorHttp(400, MSG.RESULTADO_PAGO_INVALIDO);
  }

  const pago = await transaccion(async (conn) => {
    const [[vendedor]] = await conn.query(
      'SELECT id_vendedor FROM vendedor WHERE id_vendedor = ? FOR UPDATE',
      [req.usuario.id]
    );
    if (!vendedor) throw new ErrorHttp(403, 'Solo los vendedores pueden adquirir Premium');
    const [[premium]] = await conn.query(
      'SELECT id_vendedor FROM premium_vendedor WHERE id_vendedor = ? FOR UPDATE',
      [req.usuario.id]
    );
    if (premium) throw new ErrorHttp(409, 'El vendedor ya cuenta con Premium');

    const codigo = `DEMO-${req.usuario.id}-${crypto.randomBytes(12).toString('hex')}`;
    const idEstado = resultado === 'aprobado' ? 1 : 2;
    const [insertado] = await conn.query(
      `INSERT INTO pago (id_vendedor, id_estado_pago, monto, moneda, codigo_transaccion)
       VALUES (?, ?, ?, 'MXN', ?)`,
      [req.usuario.id, idEstado, C.PRECIO_PREMIUM, codigo]
    );
    if (resultado === 'aprobado') await S.activarPremium(conn, req.usuario.id, insertado.insertId);
    return { id_pago: insertado.insertId, codigo_transaccion: codigo };
  });

  if (resultado === 'rechazado') {
    return res.status(402).json({ es_premium: false, estado_pago: 'Rechazado' });
  }
  res.status(201).json({ es_premium: true, id_pago: pago.id_pago });
});

module.exports = { obtenerSuscripcion, procesarPagoPremium };
