const PDFDocument = require('pdfkit');
const { pool, transaccion } = require('../config/db');
const C = require('../config/constantes');
const MSG = require('../utils/mensajes');
const { ErrorHttp, ErrorValidacion, asyncHandler } = require('../utils/errores');
const { validar, entero } = require('../utils/validaciones');
const F = require('../utils/fechas');
const { crearCuenta } = require('../services/cuentasService');
const S = require('../services/suscripcionService');
const sesiones = require('../services/sesionesService');

const parseId = (valor) => {
  const id = entero(valor);
  if (id === null) throw new ErrorHttp(400, 'Identificador no válido');
  return id;
};

const parsePagina = (valor) => {
  const pagina = entero(valor ?? '1');
  if (pagina === null || pagina < 1) throw new ErrorValidacion(['Dato inválido en Página']);
  return pagina;
};

const crearAdministrador = asyncHandler(async (req, res) => {
  const cuenta = await crearCuenta(req.body, C.TIPOS.ADMIN);
  res.status(201).json({ mensaje: 'Cuenta de administrador creada', usuario: cuenta });
});

const consultarSuscripciones = asyncHandler(async (_req, res) => {
  const [[fila]] = await pool.query('SELECT COUNT(*) AS total FROM premium_vendedor');
  const vendedoresPremium = Number(fila.total || 0);
  res.json({ vendedoresPremium, clientesPremium: 0, totalSuscripciones: vendedoresPremium });
});

const consultarTrafico = asyncHandler(async (req, res) => {
  const { fecha } = req.query;
  const errores = validar([{ campo: 'fecha', valor: fecha }]);
  if (errores.length) throw new ErrorValidacion(errores);
  const tz = req.usuario.zonaHoraria;
  const base = new Date(`${fecha}T00:00:00Z`);
  const [filas] = await pool.query(
    'SELECT fecha FROM registro_login WHERE fecha >= ? AND fecha < ?',
    [F.sumarDias(base, -1), F.sumarDias(base, 2)]
  );
  const traficoHorario = Array(24).fill(0);
  for (const { fecha: fechaLogin } of filas) {
    const d = new Date(fechaLogin);
    if (F.fechaEnZona(d, tz) === fecha) traficoHorario[F.horaEnZona(d, tz)] += 1;
  }
  const total = traficoHorario.reduce((sum, n) => sum + n, 0);
  res.json(total === 0
    ? { traficoHorario, sinDatos: true, mensaje: MSG.SIN_TRAFICO }
    : { traficoHorario, sinDatos: false });
});

function leerRango(query) {
  const { inicio, fin } = query;
  const errores = [
    ...validar([{ campo: 'fecha', valor: inicio, etiqueta: 'Fecha inicial' }]),
    ...validar([{ campo: 'fecha', valor: fin, etiqueta: 'Fecha final' }]),
  ];
  if (errores.length) throw new ErrorValidacion(errores);
  if (inicio > fin) throw new ErrorHttp(400, MSG.RANGO_FECHAS_INVALIDO);
  return { inicio, fin, desde: F.inicioDiaZHN(inicio), hasta: F.finDiaZHN(fin) };
}

async function pagosAprobados(desde, hasta) {
  const [pagos] = await pool.query(
    `SELECT p.fecha_pago, p.id_vendedor AS id_usuario, u.correo, p.monto, p.codigo_transaccion
       FROM pago p JOIN estado_pago ep ON ep.id_estado_pago = p.id_estado_pago
       JOIN usuario u ON u.id_usuario = p.id_vendedor
      WHERE ep.nombre = 'Aprobado' AND p.moneda = 'MXN' AND p.fecha_pago BETWEEN ? AND ?
      ORDER BY p.fecha_pago, p.id_pago`,
    [desde, hasta]
  );
  return pagos;
}

const consultarIngresos = asyncHandler(async (req, res) => {
  const { desde, hasta } = leerRango(req.query);
  const [[{ total }]] = await pool.query(
    `SELECT CAST(COALESCE(SUM(p.monto), 0) AS DECIMAL(14,2)) AS total
       FROM pago p JOIN estado_pago ep ON ep.id_estado_pago = p.id_estado_pago
      WHERE ep.nombre = 'Aprobado' AND p.moneda = 'MXN' AND p.fecha_pago BETWEEN ? AND ?`,
    [desde, hasta]
  );
  const pagos = await pagosAprobados(desde, hasta);
  res.json({ totalIngresos: String(total), pagos });
});

const celdaCsv = (valor) => {
  let texto = String(valor);
  if (/^[=+\-@\t\r]/.test(texto)) texto = `'${texto}`;
  return /[",\r\n]/.test(texto) ? `"${texto.replace(/"/g, '""')}"` : texto;
};

function generarCsv(pagos) {
  const filas = [['fecha', 'ID de vendedor', 'correo', 'monto', 'codigo_transaccion'].join(',')];
  for (const pago of pagos) {
    filas.push([
      celdaCsv(F.formatoFechaHora(new Date(pago.fecha_pago), C.ZONA_NEGOCIO)),
      celdaCsv(pago.id_usuario),
      celdaCsv(pago.correo),
      celdaCsv(Number(pago.monto).toFixed(2)),
      celdaCsv(pago.codigo_transaccion || ''),
    ].join(','));
  }
  return `${filas.join('\r\n')}\r\n`;
}

function generarPdf({ inicio, fin, pagos }) {
  return new Promise((resolve, reject) => {
    const doc = new PDFDocument({ size: 'A4', margin: 40 });
    const partes = [];
    doc.on('data', (data) => partes.push(data));
    doc.on('end', () => resolve(Buffer.concat(partes)));
    doc.on('error', reject);
    doc.font('Helvetica-Bold').fontSize(16).text('Reporte de ingresos - Tamalon');
    doc.font('Helvetica').fontSize(10).text(`Rango: ${inicio} a ${fin} | Pagos aprobados: ${pagos.length}`).moveDown();
    for (const pago of pagos) {
      if (doc.y > 750) doc.addPage();
      doc.fontSize(9).text(
        `${F.formatoFechaHora(new Date(pago.fecha_pago), C.ZONA_NEGOCIO)} | ${pago.id_usuario} | ${pago.correo} | $${Number(pago.monto).toFixed(2)} MXN | ${pago.codigo_transaccion || '-'}`,
        { width: 515 }
      );
    }
    doc.end();
  });
}

const exportarIngresos = asyncHandler(async (req, res) => {
  const { formato } = req.query;
  if (!['csv', 'pdf'].includes(formato)) {
    throw new ErrorValidacion(['Dato inválido en Formato: csv o pdf']);
  }
  const { inicio, fin, desde, hasta } = leerRango(req.query);
  const pagos = await pagosAprobados(desde, hasta);
  const nombre = `ingresos_${inicio}_a_${fin}`;
  if (formato === 'csv') {
    res.set({
      'Content-Type': 'text/csv; charset=utf-8',
      'Content-Disposition': `attachment; filename="${nombre}.csv"`,
    });
    return res.send(generarCsv(pagos));
  }
  const pdf = await generarPdf({ inicio, fin, pagos });
  res.set({
    'Content-Type': 'application/pdf',
    'Content-Disposition': `attachment; filename="${nombre}.pdf"`,
  });
  res.send(pdf);
});

function escaparLike(texto) {
  return `%${texto.replace(/[!%_]/g, '!$&')}%`;
}

const buscarUsuarios = asyncHandler(async (req, res) => {
  const pagina = parsePagina(req.query.pagina);
  const { buscar, rol, es_premium: rawPremium, id_tipo_vendedor: rawTipo } = req.query;
  const where = [];
  const params = [];
  if (buscar !== undefined) {
    if (typeof buscar !== 'string' || buscar.length > 100) {
      throw new ErrorValidacion(['Dato inválido en Búsqueda: máximo 100 caracteres']);
    }
    where.push("(nombre LIKE ? ESCAPE '!' OR apellidos LIKE ? ESCAPE '!' OR correo LIKE ? ESCAPE '!')");
    const patron = escaparLike(buscar.trim());
    params.push(patron, patron, patron);
  }
  if (rol !== undefined) {
    if (![C.TIPOS.ADMIN, C.TIPOS.VENDEDOR, C.TIPOS.CLIENTE].includes(rol)) {
      throw new ErrorValidacion(['Dato inválido en Rol']);
    }
    where.push('rol = ?');
    params.push(rol);
  }
  if (rawPremium !== undefined) {
    if (rawPremium !== 'true' && rawPremium !== 'false') {
      throw new ErrorValidacion(['Dato inválido en es_premium']);
    }
    where.push('es_premium = ?');
    params.push(rawPremium === 'true' ? 1 : 0);
  }
  if (rawTipo !== undefined) {
    const tipo = entero(rawTipo);
    if (tipo === null || tipo < 1) throw new ErrorValidacion(['Dato inválido en id_tipo_vendedor']);
    where.push('id_tipo_vendedor = ?');
    params.push(tipo);
  }
  const condicion = where.length ? `WHERE ${where.join(' AND ')}` : '';
  const [[{ total }]] = await pool.query(
    `SELECT COUNT(*) AS total FROM vw_usuario ${condicion}`,
    params
  );
  const [usuarios] = await pool.query(
    `SELECT id_usuario, nombre_completo, correo, rol, tipo_vendedor, es_premium, estado_cuenta, id_tipo_vendedor
       FROM vw_usuario ${condicion}
      ORDER BY nombre, apellidos, id_usuario LIMIT ? OFFSET ?`,
    [...params, C.POR_PAGINA, (pagina - 1) * C.POR_PAGINA]
  );
  res.json({
    usuarios: usuarios.map((usuario) => ({ ...usuario, es_premium: Boolean(usuario.es_premium) })),
    pagina,
    total: Number(total),
    totalPaginas: Math.ceil(total / C.POR_PAGINA),
  });
});

const cambiarPremium = asyncHandler(async (req, res) => {
  const idVendedor = parseId(req.params.id);
  const { otorgar } = req.body || {};
  if (typeof otorgar !== 'boolean') throw new ErrorValidacion(['Dato inválido en Otorgar: debe ser booleano']);
  const premium = await transaccion(async (conn) => {
    const [[cuenta]] = await conn.query(
      `SELECT r.nombre AS rol FROM usuario u JOIN rol r ON r.id_rol = u.id_rol
        WHERE u.id_usuario = ? FOR UPDATE`,
      [idVendedor]
    );
    if (!cuenta) throw new ErrorHttp(404, MSG.CUENTA_NO_ENCONTRADA);
    if (cuenta.rol !== C.TIPOS.VENDEDOR) throw new ErrorHttp(400, 'Premium solo está disponible para vendedores');
    const [[vendedor]] = await conn.query('SELECT id_vendedor FROM vendedor WHERE id_vendedor = ? FOR UPDATE', [idVendedor]);
    if (!vendedor) throw new ErrorHttp(404, MSG.CUENTA_NO_ENCONTRADA);
    const [[actual]] = await conn.query('SELECT id_vendedor FROM premium_vendedor WHERE id_vendedor = ? FOR UPDATE', [idVendedor]);
    if (Boolean(actual) === otorgar) throw new ErrorHttp(409, otorgar ? 'El vendedor ya es Premium' : 'El vendedor no es Premium');
    if (otorgar) await S.activarPremium(conn, idVendedor);
    else await S.revocarPremium(conn, idVendedor);
    await conn.query(
      'INSERT INTO historial_premium (id_admin, id_vendedor, accion) VALUES (?, ?, ?)',
      [req.usuario.id, idVendedor, otorgar ? 'Otorgar' : 'Revocar']
    );
    return otorgar;
  });
  res.json({ id_usuario: idVendedor, es_premium: premium });
});

async function buscarCuenta(conn, id, lock = true) {
  const [[cuenta]] = await conn.query(
    `SELECT u.id_usuario, r.nombre AS rol, ec.nombre AS estado_cuenta
       FROM usuario u JOIN rol r ON r.id_rol = u.id_rol
       JOIN estado_cuenta ec ON ec.id_estado_cuenta = u.id_estado_cuenta
      WHERE u.id_usuario = ?${lock ? ' FOR UPDATE' : ''}`,
    [id]
  );
  if (!cuenta) throw new ErrorHttp(404, MSG.CUENTA_NO_ENCONTRADA);
  if (cuenta.rol === C.TIPOS.ADMIN) throw new ErrorHttp(403, MSG.OPERACION_NO_PERMITIDA);
  return cuenta;
}

async function suspenderVendedor(conn, cuenta) {
  if (cuenta.rol === C.TIPOS.VENDEDOR) {
    await conn.query('UPDATE jornada SET fin = ? WHERE id_vendedor = ? AND fin IS NULL', [new Date(), cuenta.id_usuario]);
  }
}

const suspenderIndefinidamente = asyncHandler(async (req, res) => {
  const id = parseId(req.params.id);
  await transaccion(async (conn) => {
    const cuenta = await buscarCuenta(conn, id);
    if (cuenta.estado_cuenta === C.ESTADOS_CUENTA.SUSPENDIDA_INDEF) {
      throw new ErrorHttp(409, 'La cuenta ya está suspendida indefinidamente');
    }
    await conn.query(
      'UPDATE usuario SET id_estado_cuenta = (SELECT id_estado_cuenta FROM estado_cuenta WHERE nombre = ?) WHERE id_usuario = ?',
      [C.ESTADOS_CUENTA.SUSPENDIDA_INDEF, id]
    );
    await conn.query('DELETE FROM suspension WHERE id_usuario = ?', [id]);
    await suspenderVendedor(conn, cuenta);
  });
  await sesiones.revocarUsuario(id, 'suspendido');
  res.json({ mensaje: 'Usuario suspendido indefinidamente' });
});

const suspenderTemporalmente = asyncHandler(async (req, res) => {
  const id = parseId(req.params.id);
  const dias = req.body?.dias;
  if (!Number.isSafeInteger(dias) || dias < 1 || dias > 365) {
    throw new ErrorValidacion(['Dato inválido en Días: debe ser un entero de 1 a 365']);
  }
  const fin = new Date(Date.now() + dias * F.DIA);
  await transaccion(async (conn) => {
    const cuenta = await buscarCuenta(conn, id);
    if (cuenta.estado_cuenta !== C.ESTADOS_CUENTA.ACTIVA) {
      throw new ErrorHttp(409, 'Solo se puede suspender temporalmente una cuenta Activa');
    }
    await conn.query(
      'UPDATE usuario SET id_estado_cuenta = (SELECT id_estado_cuenta FROM estado_cuenta WHERE nombre = ?) WHERE id_usuario = ?',
      [C.ESTADOS_CUENTA.SUSPENDIDA_TEMP, id]
    );
    await conn.query(
      `INSERT INTO suspension (id_usuario, fin) VALUES (?, ?)
       ON DUPLICATE KEY UPDATE fin = VALUES(fin)`,
      [id, fin]
    );
    await suspenderVendedor(conn, cuenta);
  });
  await sesiones.revocarUsuario(id, 'suspendido');
  res.json({ mensaje: `Usuario suspendido por ${dias} día(s)`, fin_suspension: fin.toISOString() });
});

const reactivarCuenta = asyncHandler(async (req, res) => {
  const id = parseId(req.params.id);
  await transaccion(async (conn) => {
    await buscarCuenta(conn, id);
    await conn.query(
      'UPDATE usuario SET id_estado_cuenta = (SELECT id_estado_cuenta FROM estado_cuenta WHERE nombre = ?) WHERE id_usuario = ?',
      [C.ESTADOS_CUENTA.ACTIVA, id]
    );
    await conn.query('DELETE FROM suspension WHERE id_usuario = ?', [id]);
  });
  res.json({ mensaje: 'Cuenta reactivada' });
});

const consultarReportes = asyncHandler(async (req, res) => {
  const pagina = parsePagina(req.query.pagina);
  const [[{ total }]] = await pool.query('SELECT COUNT(*) AS total FROM reporte');
  const [reportes] = await pool.query(
    `SELECT r.id_reporte, e.rol AS tipo_emisor, r.id_emisor, r.id_reportado, r.fecha, r.motivo
       FROM reporte r JOIN vw_usuario e ON e.id_usuario = r.id_emisor
      ORDER BY r.fecha DESC, r.id_reporte DESC LIMIT ? OFFSET ?`,
    [C.POR_PAGINA, (pagina - 1) * C.POR_PAGINA]
  );
  res.json({ reportes, pagina, total: Number(total), totalPaginas: Math.ceil(total / C.POR_PAGINA) });
});

const consultarMensajesContacto = asyncHandler(async (req, res) => {
  const pagina = parsePagina(req.query.pagina);
  const [[{ total }]] = await pool.query('SELECT COUNT(*) AS total FROM mensaje_contacto');
  const [mensajes] = await pool.query(
    `SELECT id_mensaje, nombre, correo, mensaje, fecha_creacion FROM mensaje_contacto
      ORDER BY fecha_creacion DESC, id_mensaje DESC LIMIT ? OFFSET ?`,
    [C.POR_PAGINA, (pagina - 1) * C.POR_PAGINA]
  );
  res.json({ mensajes, pagina, total: Number(total), totalPaginas: Math.ceil(total / C.POR_PAGINA) });
});

module.exports = {
  crearAdministrador, consultarSuscripciones, consultarTrafico, consultarIngresos, exportarIngresos,
  buscarUsuarios, cambiarPremium, suspenderIndefinidamente, suspenderTemporalmente, reactivarCuenta,
  consultarReportes, consultarMensajesContacto,
};
