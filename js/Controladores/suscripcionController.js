const { pool, transaccion } = require('../config/db');
const C = require('../config/constantes');
const MSG = require('../utils/mensajes');
const { ErrorHttp, asyncHandler } = require('../utils/errores');
const S = require('../services/suscripcionService');
const F = require('../utils/fechas');

const obtenerSuscripcion = asyncHandler(async (req, res) => {
  const [[usuario]] = await pool.query(
    'SELECT tipo, plan, rol, fecha_corte, renovacion FROM vw_usuario WHERE id_usuario = ? LIMIT 1',
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
    tipo: usuario.tipo,
    rol: usuario.rol,
    plan: usuario.plan,
    fecha_corte: usuario.fecha_corte,
    renovacion: Boolean(usuario.renovacion),
    pago_simulado_habilitado: process.env.PAGOS_MODO === 'demo',
    pagos,
  });
});

const procesarPagoPremium = asyncHandler(async (req, res) => {
  if (![C.TIPOS.VENDEDOR, C.TIPOS.CLIENTE].includes(req.usuario.tipo)) {
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
      'SELECT id_usuario, tipo FROM usuarios WHERE id_usuario = ? LIMIT 1 FOR UPDATE',
      [req.usuario.id]
    );
    if (!usuario) throw new ErrorHttp(404, 'La cuenta no existe');
    if (usuario.tipo === C.TIPOS.ADMIN) {
      throw new ErrorHttp(403, 'Las cuentas de administrador no pueden contratar una suscripción');
    }

    const ahora = new Date();
    const codigoTransaccion = `DEMO-${usuario.id_usuario}-${ahora.getTime()}`;
    let planUsuario;
    let fechaCorteActual = null;
    if (usuario.tipo === C.TIPOS.VENDEDOR) {
      const [[fila]] = await conn.query(
        'SELECT plan, fecha_corte FROM vendedor WHERE id_vendedor = ? FOR UPDATE',
        [usuario.id_usuario]
      );
      planUsuario = fila?.plan;
      fechaCorteActual = fila?.fecha_corte;
    } else {
      const [[fila]] = await conn.query(
        'SELECT plan, fecha_corte FROM cliente WHERE id_cliente = ? FOR UPDATE',
        [usuario.id_usuario]
      );
      planUsuario = fila?.plan;
      fechaCorteActual = fila?.fecha_corte;
    }
    usuario.plan = planUsuario;

    const tipo = S.esPremium(usuario) ? 'Renovacion' : 'Compra';
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

    let rol = C.componerRol(usuario.tipo, planUsuario);
    let fechaCorte;
    if (S.esGratuito(usuario)) {
      fechaCorte = S.calcularFechaCorte(ahora);
      rol = await S.activarPremium(conn, usuario, { fechaCorte, renovacion: false });
    } else {
      const hoy = F.fechaEnZona(ahora);
      const fechaCorteTexto = fechaCorteActual instanceof Date
        ? fechaCorteActual.toISOString().slice(0, 10)
        : String(fechaCorteActual || '').slice(0, 10);
      const base = fechaCorteTexto > hoy ? fechaCorteTexto : hoy;
      fechaCorte = F.sumarDiasFecha(base, C.PREMIUM_DIAS);
      if (usuario.tipo === C.TIPOS.VENDEDOR) {
        await conn.query(
          'UPDATE vendedor SET fecha_corte = ? WHERE id_vendedor = ?',
          [fechaCorte, usuario.id_usuario]
        );
      } else {
        await conn.query(
          'UPDATE cliente SET fecha_corte = ? WHERE id_cliente = ?',
          [fechaCorte, usuario.id_usuario]
        );
      }
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
