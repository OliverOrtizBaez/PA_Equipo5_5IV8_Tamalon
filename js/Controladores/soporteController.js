const { pool } = require('../config/db');
const C = require('../config/constantes');
const { ErrorHttp, ErrorValidacion, asyncHandler } = require('../utils/errores');
const { entero } = require('../utils/validaciones');

const CATEGORIAS_TICKET = ['queja', 'peticion', 'sugerencia', 'tecnico', 'otro'];
const rolesSociales = [C.ROLES.VENDEDOR_GRATUITO, C.ROLES.VENDEDOR_PREMIUM, C.ROLES.CLIENTE_GRATUITO, C.ROLES.CLIENTE_PREMIUM];

const crearReporte = asyncHandler(async (req, res) => {
  const { id_reportado, motivo } = req.body || {};
  const idReportado = entero(id_reportado);
  const errores = [];

  if (idReportado === null) errores.push('Dato inválido en Usuario reportado: identificador no válido');
  if (typeof motivo !== 'string' || motivo.trim().length < 1 || motivo.trim().length > 500) {
    errores.push('Dato inválido en Motivo: debe contener de 1 a 500 caracteres');
  }
  if (errores.length) throw new ErrorValidacion(errores);
  if (idReportado === req.usuario.id) throw new ErrorHttp(400, 'No puedes reportar tu propia cuenta');

  const [usuarios] = await pool.query(
    'SELECT id_usuario, rol FROM usuarios WHERE id_usuario = ? LIMIT 1',
    [idReportado]
  );
  const reportado = usuarios[0];
  if (!reportado) throw new ErrorHttp(404, 'La cuenta que intentas reportar no existe');
  if (!rolesSociales.includes(req.usuario.rol) || !rolesSociales.includes(reportado.rol)) {
    throw new ErrorHttp(400, 'Solo puedes reportar entre cuentas de cliente y vendedor');
  }
  const esVendedorEmisor = req.usuario.rol.startsWith('Vendedor');
  const esVendedorReportado = reportado.rol.startsWith('Vendedor');
  if (esVendedorEmisor === esVendedorReportado) {
    throw new ErrorHttp(400, 'Solo puedes reportar a usuarios del rol opuesto');
  }

  const [resultado] = await pool.query(
    'INSERT INTO reporte (id_emisor, id_reportado, motivo) VALUES (?, ?, ?)',
    [req.usuario.id, idReportado, motivo.trim()]
  );
  res.status(201).json({ mensaje: 'Reporte enviado', id_reporte: resultado.insertId });
});

const obtenerMisReportes = asyncHandler(async (req, res) => {
  const [reportes] = await pool.query(
    `SELECT r.id_reporte, r.id_reportado, u.nombre_completo AS nombre_reportado, r.motivo, r.fech_hrs
       FROM reporte r
       JOIN usuarios u ON u.id_usuario = r.id_reportado
      WHERE r.id_emisor = ?
      ORDER BY r.fech_hrs DESC, r.id_reporte DESC`,
    [req.usuario.id]
  );
  res.json({ reportes });
});

const crearSolicitudSoporte = asyncHandler(async (req, res) => {
  const { categoria, asunto, descripcion } = req.body || {};
  const errores = [];
  if (typeof categoria !== 'string' || !CATEGORIAS_TICKET.includes(categoria)) {
    errores.push('Dato inválido en Categoría: queja, peticion, sugerencia, tecnico u otro');
  }
  if (typeof asunto !== 'string' || asunto.trim().length < 1 || asunto.trim().length > 120) {
    errores.push('Dato inválido en Asunto: debe contener de 1 a 120 caracteres');
  }
  if (typeof descripcion !== 'string' || descripcion.trim().length < 1 || descripcion.trim().length > 1000) {
    errores.push('Dato inválido en Descripción: debe contener de 1 a 1000 caracteres');
  }
  if (errores.length) throw new ErrorValidacion(errores);

  const [resultado] = await pool.query(
    `INSERT INTO solicitudes_soporte (id_usuario, rol_emisor, categoria, asunto, descripcion)
     VALUES (?, ?, ?, ?, ?)`,
    [req.usuario.id, req.usuario.rol, categoria, asunto.trim(), descripcion.trim()]
  );
  res.status(201).json({ mensaje: 'Solicitud de soporte enviada', id_solicitud: resultado.insertId });
});

const obtenerMisTickets = asyncHandler(async (req, res) => {
  const [tickets] = await pool.query(
    `SELECT id_solicitud, categoria, asunto, descripcion, estado, fecha_creacion
       FROM solicitudes_soporte
      WHERE id_usuario = ?
      ORDER BY fecha_creacion DESC, id_solicitud DESC`,
    [req.usuario.id]
  );
  res.json({ tickets });
});

module.exports = { crearReporte, obtenerMisReportes, crearSolicitudSoporte, obtenerMisTickets };
