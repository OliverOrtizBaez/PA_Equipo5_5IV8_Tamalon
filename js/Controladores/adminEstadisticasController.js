const { pool } = require('../config/db');
const { asyncHandler } = require('../utils/errores');

const consultarEstadisticas = asyncHandler(async (_req, res) => {
  const [[{ vendedores }]] = await pool.query('SELECT COUNT(*) AS vendedores FROM vendedor');
  const [[{ premium }]] = await pool.query('SELECT COUNT(*) AS premium FROM premium_vendedor');
  const conversion = Number(vendedores) === 0 ? 0 : Number(((Number(premium) / Number(vendedores)) * 100).toFixed(2));

  const [[{ total: ingresos }]] = await pool.query(
    `SELECT COALESCE(SUM(p.monto), 0) AS total FROM pago p
       JOIN estado_pago ep ON ep.id_estado_pago = p.id_estado_pago WHERE ep.nombre = 'Aprobado'`
  );
  const [porMes] = await pool.query(
    `SELECT DATE_FORMAT(p.fecha_pago, '%Y-%m') AS mes, SUM(p.monto) AS monto
       FROM pago p JOIN estado_pago ep ON ep.id_estado_pago = p.id_estado_pago
      WHERE ep.nombre = 'Aprobado'
      GROUP BY DATE_FORMAT(p.fecha_pago, '%Y-%m') ORDER BY mes`
  );
  const [porRol] = await pool.query(
    'SELECT rol, COUNT(*) AS total FROM vw_usuario GROUP BY rol ORDER BY rol'
  );
  const [porTipoVendedor] = await pool.query(
    `SELECT tv.id_tipo_vendedor, tv.clave, tv.nombre, COUNT(v.id_vendedor) AS total
       FROM tipo_vendedor tv LEFT JOIN vendedor v ON v.id_tipo_vendedor = tv.id_tipo_vendedor
      WHERE tv.activo = 1 GROUP BY tv.id_tipo_vendedor, tv.clave, tv.nombre ORDER BY tv.id_tipo_vendedor`
  );
  const [[{ en_ruta_ahora: enRuta }]] = await pool.query(
    `SELECT COUNT(DISTINCT j.id_vendedor) AS en_ruta_ahora FROM jornada j
       JOIN ubicacion_actual ua ON ua.id_vendedor = j.id_vendedor
      WHERE j.fin IS NULL AND ua.actualizado >= DATE_SUB(CURRENT_TIMESTAMP(3), INTERVAL 60 SECOND)`
  );
  const [altasPorMes] = await pool.query(
    `SELECT DATE_FORMAT(fecha_creacion, '%Y-%m') AS mes, COUNT(*) AS total
       FROM usuario GROUP BY DATE_FORMAT(fecha_creacion, '%Y-%m') ORDER BY mes`
  );
  const [[calidad]] = await pool.query(
    `SELECT
       (SELECT COUNT(*) FROM reporte WHERE fecha >= DATE_SUB(CURRENT_TIMESTAMP, INTERVAL 30 DAY)) AS reportes_30d,
       (SELECT COUNT(*) FROM usuario u JOIN estado_cuenta ec ON ec.id_estado_cuenta = u.id_estado_cuenta
         WHERE ec.nombre <> 'Activa') AS suspendidas,
       (SELECT COUNT(*) FROM vendedor v WHERE NOT EXISTS (
          SELECT 1 FROM catalogo c WHERE c.id_vendedor = v.id_vendedor)) AS vendedores_sin_catalogo`
  );
  res.json({
    embudo: {
      vendedores: Number(vendedores),
      premium: Number(premium),
      conversion,
    },
    ingresos: { total: Number(Number(ingresos).toFixed(2)), por_mes: porMes },
    por_rol: porRol,
    por_tipo_vendedor: porTipoVendedor,
    en_ruta_ahora: Number(enRuta),
    altas_por_mes: altasPorMes,
    calidad: {
      reportes_30d: Number(calidad.reportes_30d),
      suspendidas: Number(calidad.suspendidas),
      vendedores_sin_catalogo: Number(calidad.vendedores_sin_catalogo),
    },
  });
});

module.exports = { consultarEstadisticas };
