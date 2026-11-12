const { pool, transaccion } = require('../config/db');
const C = require('../config/constantes');
const MSG = require('../utils/mensajes');
const { ErrorHttp, asyncHandler } = require('../utils/errores');
const S = require('../services/suscripcionService');
const F = require('../utils/fechas');

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
    pago_simulado_habilitado: process.env.PAGOS_MODO === 'demo',
    pagos,
  });
});

const procesarPagoPremium = asyncHandler(async (req, res) => {
  if (![C.ROLES.VENDEDOR_GRATUITO, C.ROLES.VENDEDOR_PREMIUM, C.ROLES.CLIENTE_GRATUITO, C.ROLES.CLIENTE_PREMIUM]
    .includes(req.usuario.rol)) {
    throw new ErrorHttp(403, 'Las cuentas de administrador no pueden contratar una suscripción');
  }

  if (process.env.PAGOS_MODO !== 'demo') {
    throw new ErrorHttp(503, MSG.PAGO_SIMULADO_NO_HABILITADO);
  }

  const body = req.body;
  if (body !== undefined && (!body || typeof body !== 'object' || Array.isArray(body))) {
    throw new ErrorHttp(400, MSG.RESULTADO_PAGO_INVALIDO);
  }
  const resultado = body && Object.prototype.hasOwnProperty.call(body, 'resultado')
    ? body.resultado
    : 'aprobado';
  if (resultado !== 'aprobado' && resultado !== 'rechazado') {
    throw new ErrorHttp(400, MSG.RESULTADO_PAGO_INVALIDO);
  }

  const pago = await transaccion(async (conn) => {
    const [[usuario]] = await conn.query(
      'SELECT id_usuario, rol, fecha_corte FROM usuarios WHERE id_usuario = ? LIMIT 1 FOR UPDATE',
      [req.usuario.id]
    );
    if (!usuario) throw new ErrorHttp(404, 'La cuenta no existe');
    if (usuario.rol === C.ROLES.ADMIN) {
      throw new ErrorHttp(403, 'Las cuentas de administrador no pueden contratar una suscripción');
    }

    const ahora = new Date();
    const codigoTransaccion = `DEMO-${usuario.id_usuario}-${ahora.getTime()}`;
    const tipo = S.esPremium(usuario.rol) ? 'Renovacion' : 'Compra';
    const estado = resultado === 'aprobado' ? 'Aprobado' : 'Rechazado';
    const [insertado] = await conn.query(
      `INSERT INTO suscripcion
         (id_usuario, tipo, estado_pago, suscrs_monto, moneda, codigo_transaccion, fech_pago)
       VALUES (?, ?, ?, ?, 'MXN', ?, ?)`,
      [usuario.id_usuario, tipo, estado, C.PAGO_SIMULADO_MONTO, codigoTransaccion, ahora]
    );

    if (resultado === 'rechazado') {
      return { idSuscripcion: insertado.insertId, estado };
    }

    let rol = usuario.rol;
    let fechaCorte;
    if (S.esGratuito(usuario.rol)) {
      fechaCorte = S.calcularFechaCorte(ahora);
      rol = await S.activarPremium(conn, usuario, { fechaCorte, renovacion: false });
    } else {
      const hoy = F.fechaEnZona(ahora);
      const fechaCorteActual = usuario.fecha_corte instanceof Date
        ? usuario.fecha_corte.toISOString().slice(0, 10)
        : String(usuario.fecha_corte || '').slice(0, 10);
      const base = fechaCorteActual > hoy ? fechaCorteActual : hoy;
      fechaCorte = F.sumarDiasFecha(base, C.PREMIUM_DIAS);
      await conn.query('UPDATE usuarios SET fecha_corte = ? WHERE id_usuario = ?',
        [fechaCorte, usuario.id_usuario]);
    }

    return {
      idSuscripcion: insertado.insertId,
      codigoTransaccion,
      estado,
      rol,
      fechaCorte,
    };
  });

  if (resultado === 'rechazado') {
    return res.json({
      mensaje: MSG.PAGO_SIMULADO_RECHAZADO,
      simulado: true,
      estado: pago.estado,
      referencia: pago.idSuscripcion,
    });
  }

  res.status(201).json({
    mensaje: MSG.PAGO_SIMULADO_APROBADO,
    simulado: true,
    estado: pago.estado,
    referencia: pago.idSuscripcion,
    codigo_transaccion: pago.codigoTransaccion,
    rol: pago.rol,
    plan: 'Premium',
    fecha_corte: pago.fechaCorte,
  });
});

module.exports = { obtenerSuscripcion, procesarPagoPremium };
