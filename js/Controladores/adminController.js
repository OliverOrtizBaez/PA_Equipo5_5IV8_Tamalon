const PDFDocument = require('pdfkit');
const { pool, transaccion } = require('../config/db');
const C = require('../config/constantes');
const MSG = require('../utils/mensajes');
const { ErrorHttp, ErrorValidacion, asyncHandler } = require('../utils/errores');
const { validar, entero } = require('../utils/validaciones');
const F = require('../utils/fechas');
const { crearCuenta } = require('../services/cuentasService');
const S = require('../services/suscripcionService');

const parseId = (valor) => {
  const id = entero(valor);
  if (id === null) throw new ErrorHttp(400, 'Identificador no válido');
  return id;
};

const parsePagina = (valor) => entero(valor ?? '1') || 1;

const crearAdministrador = asyncHandler(async (req, res) => {
  const cuenta = await crearCuenta(req.body, C.ROLES.ADMIN);
  res.status(201).json({ mensaje: 'Cuenta de administrador creada', usuario: cuenta });
});

const consultarSuscripciones = asyncHandler(async (req, res) => {
  const [[f]] = await pool.query(
    `SELECT SUM(rol = ?) AS vendedores, SUM(rol = ?) AS clientes FROM usuarios`,
    [C.ROLES.VENDEDOR_PREMIUM, C.ROLES.CLIENTE_PREMIUM] 
  );
  const vendedoresPremium = Number(f.vendedores || 0);
  const clientesPremium = Number(f.clientes || 0);
  res.json({ vendedoresPremium, clientesPremium, totalSuscripciones: vendedoresPremium + clientesPremium });
});

const consultarTrafico = asyncHandler(async (req, res) => {
  const { fecha } = req.query;
  const errores = validar([{ campo: 'fecha', valor: fecha }]);
  if (errores.length) throw new ErrorValidacion(errores);

  const tz = req.usuario.zonaHoraria; 
  const base = new Date(`${fecha}T00:00:00Z`);
  const [filas] = await pool.query(
    'SELECT fech_hrs FROM registro_login WHERE fech_hrs >= ? AND fech_hrs < ?',
    [F.sumarDias(base, -1), F.sumarDias(base, 2)]
  );

  const traficoHorario = Array(24).fill(0);
  let total = 0;
  for (const { fech_hrs } of filas) {
    const d = new Date(fech_hrs);
    if (F.fechaEnZona(d, tz) === fecha) {
      traficoHorario[F.horaEnZona(d, tz)] += 1;
      total += 1;
    }
  }
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
    `SELECT s.fech_pago, s.id_usuario, u.correo, s.suscrs_monto
       FROM suscripcion s JOIN usuarios u ON u.id_usuario = s.id_usuario
      WHERE s.estado_pago = 'Aprobado' AND s.moneda = 'MXN' AND s.fech_pago BETWEEN ? AND ?
      ORDER BY s.fech_pago ASC, s.id_suscripcion ASC`,
    [desde, hasta]
  );
  return pagos;
}

const consultarIngresos = asyncHandler(async (req, res) => {
  const { desde, hasta } = leerRango(req.query);
  const [[{ total }]] = await pool.query(
    `SELECT CAST(COALESCE(SUM(suscrs_monto), 0) AS DECIMAL(14,2)) AS total
       FROM suscripcion WHERE estado_pago = 'Aprobado' AND moneda = 'MXN' AND fech_pago BETWEEN ? AND ?`,
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
  const lineas = [['fecha', 'ID de cuenta', 'correo de la cuenta', 'monto'].join(',')];
  for (const p of pagos) {
    lineas.push([
      celdaCsv(F.formatoFechaHora(new Date(p.fech_pago), C.ZONA_NEGOCIO)),
      celdaCsv(p.id_usuario),
      celdaCsv(p.correo),
      celdaCsv(Number(p.suscrs_monto).toFixed(2)),
    ].join(','));
  }
  return `${lineas.join('\r\n')}\r\n`;
}

function generarPdf({ inicio, fin, pagos }) {
  return new Promise((resolve, reject) => {
    const doc = new PDFDocument({ size: 'A4', margin: 40 });
    const partes = [];
    doc.on('data', (d) => partes.push(d));
    doc.on('end', () => resolve(Buffer.concat(partes)));
    doc.on('error', reject);

    const X = { fecha: 40, id: 170, correo: 230, monto: 470 };
    const encabezado = () => {
      const y = doc.y;
      doc.font('Helvetica-Bold').fontSize(9);
      doc.text('Fecha', X.fecha, y, { width: 125, lineBreak: false });
      doc.text('ID de cuenta', X.id, y, { width: 55, lineBreak: false });
      doc.text('Correo de la cuenta', X.correo, y, { width: 235, lineBreak: false });
      doc.text('Monto (MXN)', X.monto, y, { width: 85, align: 'right', lineBreak: false });
      doc.moveTo(40, y + 13).lineTo(555, y + 13).stroke();
      doc.y = y + 18;
      doc.font('Helvetica').fontSize(9);
    };

    doc.font('Helvetica-Bold').fontSize(16).text('Reporte de ingresos - Tamalon');
    doc.font('Helvetica').fontSize(10)
      .text(`Rango (ZH-N): ${inicio} a ${fin}   |   Pagos aprobados: ${pagos.length}`).moveDown(1);
    encabezado();

    for (const p of pagos) {
      if (doc.y > 770) { doc.addPage(); encabezado(); }
      const y = doc.y;
      doc.text(F.formatoFechaHora(new Date(p.fech_pago), C.ZONA_NEGOCIO), X.fecha, y, { width: 125, lineBreak: false });
      doc.text(String(p.id_usuario), X.id, y, { width: 55, lineBreak: false });
      doc.text(p.correo, X.correo, y, { width: 235, lineBreak: false, ellipsis: true });
      doc.text(Number(p.suscrs_monto).toFixed(2), X.monto, y, { width: 85, align: 'right', lineBreak: false });
      doc.y = y + 14;
    }
    doc.end();
  });
}

const exportarIngresos = asyncHandler(async (req, res) => {
  const { formato } = req.query;
  if (formato !== 'csv' && formato !== 'pdf') {
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

const NOMBRE_ESTADO = (e) => e; 

const buscarUsuarios = asyncHandler(async (req, res) => {
  const { buscar } = req.query;
  const errores = validar([{ campo: 'busqueda', valor: buscar }]);
  if (errores.length) throw new ErrorValidacion(errores);

  const pagina = parsePagina(req.query.pagina);
  const patron = `%${buscar.trim().replace(/[\\%_]/g, '\\$&')}%`; 

  const [[{ total }]] = await pool.query(
    'SELECT COUNT(*) AS total FROM usuarios WHERE nombre_completo LIKE ? OR correo LIKE ?',
    [patron, patron]
  );
  const [usuarios] = await pool.query(
    `SELECT id_usuario, nombre_completo, correo, rol, estado_cuenta
       FROM usuarios WHERE nombre_completo LIKE ? OR correo LIKE ?
      ORDER BY nombre_completo ASC, id_usuario ASC LIMIT ? OFFSET ?`,
    [patron, patron, C.POR_PAGINA, (pagina - 1) * C.POR_PAGINA]
  );
  usuarios.forEach((u) => { u.estado_cuenta = NOMBRE_ESTADO(u.estado_cuenta); });

  res.json({
    usuarios, pagina, total,
    totalPaginas: Math.ceil(total / C.POR_PAGINA),
    ...(total === 0 && { mensaje: MSG.SIN_USUARIOS }), 
  });
});

async function cuentaBloqueada(conn, id) {
  const [[u]] = await conn.query(
    'SELECT id_usuario, rol, estado_cuenta FROM usuarios WHERE id_usuario = ? FOR UPDATE',
    [id]
  );
  if (!u) throw new ErrorHttp(404, MSG.CUENTA_NO_ENCONTRADA);
  if (u.rol === C.ROLES.ADMIN) throw new ErrorHttp(403, MSG.OPERACION_NO_PERMITIDA); 
  return u;
}

const cambiarRol = asyncHandler(async (req, res) => {
  const id = parseId(req.params.id);
  const { accion } = req.body || {};
  if (accion !== 'Premium' && accion !== 'Gratuito') {
    throw new ErrorValidacion(['Dato inválido en Acción: Premium o Gratuito']);
  }

  const rolNuevo = await transaccion(async (conn) => {
    const u = await cuentaBloqueada(conn, id);
    let nuevo;
    if (accion === 'Premium') {
      if (!S.esGratuito(u.rol)) throw new ErrorHttp(409, 'La cuenta ya es Premium');
      nuevo = await S.activarPremium(conn, u, { fechaCorte: S.calcularFechaCorte(), renovacion: false });
    } else {
      if (!S.esPremium(u.rol)) throw new ErrorHttp(409, 'La cuenta ya es Gratuita');
      nuevo = await S.degradarAGratuito(conn, u); 
    }
    await conn.query(
      'INSERT INTO historial_rol (id_admin, id_usuario, rol_anterior, rol_nuevo, fech_hrs) VALUES (?, ?, ?, ?, ?)',
      [req.usuario.id, id, u.rol, nuevo, new Date()]
    );
    return nuevo;
  });

  res.json({ mensaje: 'Rol actualizado correctamente', rol: rolNuevo });
});
async function marcarFueraDeRutaSiVendedor(conn, rol, id) {
  if (S.esVendedor(rol)) { // RF22.6
    await conn.query("UPDATE ubi_vendedor SET disponibilidad = 'Fuera de ruta' WHERE id_vendedor = ?", [id]);
  }
}

const suspenderIndefinidamente = asyncHandler(async (req, res) => {
  const id = parseId(req.params.id);
  await transaccion(async (conn) => {
    const u = await cuentaBloqueada(conn, id);
    if (u.estado_cuenta === C.ESTADOS_CUENTA.SUSPENDIDA_INDEF) {
      throw new ErrorHttp(409, 'La cuenta ya está suspendida indefinidamente');
    }
    await conn.query('UPDATE usuarios SET estado_cuenta = ?, fin_suspension = NULL WHERE id_usuario = ?',
      [C.ESTADOS_CUENTA.SUSPENDIDA_INDEF, id]);
    await marcarFueraDeRutaSiVendedor(conn, u.rol, id);
  });
  res.json({ mensaje: 'Usuario suspendido indefinidamente' });
});

const suspenderTemporalmente = asyncHandler(async (req, res) => {
  const id = parseId(req.params.id);
  const dias = req.body?.dias;
  const errores = validar([{ campo: 'dias', valor: dias }]);
  if (errores.length) throw new ErrorValidacion(errores);

  const fin = new Date(Date.now() + dias * F.DIA); 
  await transaccion(async (conn) => {
    const u = await cuentaBloqueada(conn, id);
    if (u.estado_cuenta !== C.ESTADOS_CUENTA.ACTIVA) {
      throw new ErrorHttp(409, 'Solo se puede suspender temporalmente una cuenta Activa');
    }
    await conn.query('UPDATE usuarios SET estado_cuenta = ?, fin_suspension = ? WHERE id_usuario = ?',
      [C.ESTADOS_CUENTA.SUSPENDIDA_TEMP, fin, id]);
    await marcarFueraDeRutaSiVendedor(conn, u.rol, id);
  });
  res.json({ mensaje: `Usuario suspendido por ${dias} día(s)`, fin_suspension: fin.toISOString() });
});

const reactivarCuenta = asyncHandler(async (req, res) => {
  const id = parseId(req.params.id);
  await transaccion(async (conn) => {
    const u = await cuentaBloqueada(conn, id);
    if (u.estado_cuenta === C.ESTADOS_CUENTA.ACTIVA) throw new ErrorHttp(409, 'La cuenta ya está Activa');
    // El vendedor permanece "Fuera de ruta" hasta que el mismo presione RF6.1
    await conn.query('UPDATE usuarios SET estado_cuenta = ?, fin_suspension = NULL WHERE id_usuario = ?',
      [C.ESTADOS_CUENTA.ACTIVA, id]);
  });
  res.json({ mensaje: 'Cuenta reactivada' });
});

const consultarReportes = asyncHandler(async (req, res) => {
  const pagina = parsePagina(req.query.pagina);
  const [[{ total }]] = await pool.query('SELECT COUNT(*) AS total FROM reporte');
  const [reportes] = await pool.query(
    `SELECT r.id_reporte,
            CASE WHEN e.rol LIKE 'Vendedor%' THEN 'Vendedor' ELSE 'Cliente' END AS tipo_emisor,
            r.id_emisor, r.id_reportado, r.fech_hrs, r.motivo
       FROM reporte r JOIN usuarios e ON e.id_usuario = r.id_emisor
      ORDER BY r.fech_hrs DESC, r.id_reporte DESC LIMIT ? OFFSET ?`,
    [C.POR_PAGINA, (pagina - 1) * C.POR_PAGINA]
  );
  res.json({
    reportes, pagina, total,
    totalPaginas: Math.ceil(total / C.POR_PAGINA),
    ...(total === 0 && { mensaje: MSG.SIN_REPORTES }), 
  });
});

const consultarMensajesContacto = asyncHandler(async (req, res) => {
  const pagina = parsePagina(req.query.pagina);
  const [[{ total }]] = await pool.query('SELECT COUNT(*) AS total FROM mensaje_contacto');
  const [mensajes] = await pool.query(
    `SELECT id_mensaje, nombre, correo, mensaje, fecha_creacion
       FROM mensaje_contacto
      ORDER BY fecha_creacion DESC, id_mensaje DESC
      LIMIT ? OFFSET ?`,
    [C.POR_PAGINA, (pagina - 1) * C.POR_PAGINA]
  );
  res.json({
    mensajes, pagina, total,
    totalPaginas: Math.ceil(total / C.POR_PAGINA),
  });
});

module.exports = {
  crearAdministrador, consultarSuscripciones, consultarTrafico, consultarIngresos, exportarIngresos,
  buscarUsuarios, cambiarRol, suspenderIndefinidamente, suspenderTemporalmente, reactivarCuenta,
  consultarReportes, consultarMensajesContacto,
};
