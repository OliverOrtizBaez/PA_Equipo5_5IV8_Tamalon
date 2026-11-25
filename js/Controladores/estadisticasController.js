const { pool } = require('../config/db');
const { ErrorHttp, ErrorValidacion, asyncHandler } = require('../utils/errores');

const HORA = 3600000;
const DIA = 86400000;
const TIERRA_KM = 6371;

function parsearRango(query) {
  const { desde, hasta } = query;
  const formato = /^\d{4}-\d{2}-\d{2}$/;
  if (!formato.test(desde || '') || !formato.test(hasta || '')) {
    throw new ErrorValidacion(['Indica desde y hasta con formato YYYY-MM-DD']);
  }
  const inicio = new Date(`${desde}T00:00:00.000Z`);
  const fin = new Date(`${hasta}T23:59:59.999Z`);
  if (Number.isNaN(inicio.getTime()) || Number.isNaN(fin.getTime()) ||
      inicio.toISOString().slice(0, 10) !== desde || fin.toISOString().slice(0, 10) !== hasta ||
      inicio > fin || fin - inicio > 366 * DIA) {
    throw new ErrorValidacion(['El rango de fechas no es válido (máximo 366 días)']);
  }
  return { desde, hasta, inicio, fin };
}

function kmEntre(a, b) {
  const rad = (n) => n * Math.PI / 180;
  const dLat = rad(Number(b.latitud) - Number(a.latitud));
  const dLng = rad(Number(b.longitud) - Number(a.longitud));
  const h = Math.sin(dLat / 2) ** 2 + Math.cos(rad(Number(a.latitud))) *
    Math.cos(rad(Number(b.latitud))) * Math.sin(dLng / 2) ** 2;
  return 2 * TIERRA_KM * Math.atan2(Math.sqrt(h), Math.sqrt(1 - h));
}

const obtenerEstadisticasVendedor = asyncHandler(async (req, res) => {
  const [[cuenta]] = await pool.query(
    'SELECT es_premium FROM vw_usuario WHERE id_usuario = ?',
    [req.usuario.id]
  );
  if (!cuenta) throw new ErrorHttp(404, 'La cuenta no existe');
  if (!cuenta.es_premium) {
    throw new ErrorHttp(403, 'Premium requerido', { codigo: 'PREMIUM_REQUERIDO' });
  }
  const rango = parsearRango(req.query);
  const [jornadas] = await pool.query(
    `SELECT id_jornada, inicio, fin FROM jornada
      WHERE id_vendedor = ? AND inicio <= ? AND COALESCE(fin, CURRENT_TIMESTAMP) >= ?`,
    [req.usuario.id, rango.fin, rango.inicio]
  );
  const [puntos] = await pool.query(
    `SELECT id_jornada, latitud, longitud, registrado FROM (
       SELECT uh.id_jornada, uh.latitud, uh.longitud, uh.registrado, uh.id_punto,
              ROW_NUMBER() OVER (PARTITION BY uh.id_jornada ORDER BY uh.registrado DESC, uh.id_punto DESC) AS rn,
              0 AS en_rango
         FROM ubicacion_historial uh JOIN jornada j ON j.id_jornada = uh.id_jornada
        WHERE j.id_vendedor = ? AND uh.registrado < ?
          AND uh.registrado >= DATE_SUB(?, INTERVAL 90 DAY)
       UNION ALL
       SELECT uh.id_jornada, uh.latitud, uh.longitud, uh.registrado, uh.id_punto, 1 AS rn, 1 AS en_rango
         FROM ubicacion_historial uh JOIN jornada j ON j.id_jornada = uh.id_jornada
        WHERE j.id_vendedor = ? AND uh.registrado BETWEEN ? AND ?
     ) puntos
      WHERE en_rango = 1 OR rn = 1
      ORDER BY id_jornada, registrado, id_punto`,
    [req.usuario.id, rango.inicio, rango.inicio, req.usuario.id, rango.inicio, rango.fin]
  );

  const dias = [];
  for (let t = rango.inicio.getTime(); t <= rango.fin.getTime(); t += DIA) {
    const fecha = new Date(t).toISOString().slice(0, 10);
    const inicioDia = Math.max(t, rango.inicio.getTime());
    const finDia = Math.min(t + DIA, rango.fin.getTime() + 1);
    let segundos = 0;
    for (const jornada of jornadas) {
      const inicio = new Date(jornada.inicio).getTime();
      const fin = jornada.fin ? new Date(jornada.fin).getTime() : Date.now();
      segundos += Math.max(0, Math.min(fin, finDia) - Math.max(inicio, inicioDia)) / 1000;
    }
    dias.push({ fecha, horas: Number((segundos / 3600).toFixed(2)), km: 0 });
  }
  const indiceDia = new Map(dias.map((dia, i) => [dia.fecha, i]));
  const puntosPorJornada = new Map();
  for (const punto of puntos) {
    if (!puntosPorJornada.has(punto.id_jornada)) puntosPorJornada.set(punto.id_jornada, []);
    puntosPorJornada.get(punto.id_jornada).push(punto);
  }
  for (const lista of puntosPorJornada.values()) {
    for (let i = 1; i < lista.length; i += 1) {
      const fecha = new Date(lista[i].registrado).toISOString().slice(0, 10);
      const indice = indiceDia.get(fecha);
      if (indice !== undefined) dias[indice].km += kmEntre(lista[i - 1], lista[i]);
    }
  }
  dias.forEach((dia) => { dia.km = Number(dia.km.toFixed(3)); });
  const km = Number(dias.reduce((sum, dia) => sum + dia.km, 0).toFixed(3));
  const horas = Number(dias.reduce((sum, dia) => sum + dia.horas, 0).toFixed(2));

  const duracion = Math.ceil((rango.fin.getTime() - rango.inicio.getTime() + 1) / DIA);
  const previoDesde = new Date(rango.inicio.getTime() - duracion * DIA);
  const previoHasta = new Date(rango.inicio.getTime() - 1);
  const [eventos] = await pool.query(
    `SELECT te.clave, ev.id_producto, ev.fecha
       FROM evento_vendedor ev JOIN tipo_evento te ON te.id_tipo_evento = ev.id_tipo_evento
      WHERE ev.id_vendedor = ? AND ev.fecha BETWEEN ? AND ?`,
    [req.usuario.id, rango.inicio, rango.fin]
  );
  const [eventosPrevios] = await pool.query(
    `SELECT COUNT(*) AS total FROM evento_vendedor ev
       JOIN tipo_evento te ON te.id_tipo_evento = ev.id_tipo_evento
      WHERE ev.id_vendedor = ? AND te.clave IN ('vista_perfil','vista_catalogo','vista_producto')
        AND ev.fecha BETWEEN ? AND ?`,
    [req.usuario.id, previoDesde, previoHasta]
  );
  const visitas = eventos.filter((evento) => evento.clave.startsWith('vista_'));
  const porHora = Array(24).fill(0);
  const porDiaSemana = Array(7).fill(0);
  for (const visita of visitas) {
    const fecha = new Date(visita.fecha);
    porHora[fecha.getUTCHours()] += 1;
    porDiaSemana[(fecha.getUTCDay() + 6) % 7] += 1;
  }
  const [[favoritos]] = await pool.query(
    'SELECT COUNT(*) AS total FROM favorito WHERE id_vendedor = ?',
    [req.usuario.id]
  );
  const nuevos = eventos.filter((evento) => evento.clave === 'favorito_agregado').length;
  const [topProductos] = await pool.query(
    `SELECT p.id_producto, p.nombre, COUNT(*) AS vistas
       FROM evento_vendedor ev JOIN tipo_evento te ON te.id_tipo_evento = ev.id_tipo_evento
       JOIN producto p ON p.id_producto = ev.id_producto
      WHERE ev.id_vendedor = ? AND te.clave = 'vista_producto' AND ev.fecha BETWEEN ? AND ?
      GROUP BY p.id_producto, p.nombre ORDER BY vistas DESC, p.id_producto LIMIT 5`,
    [req.usuario.id, rango.inicio, rango.fin]
  );
  const [sinVistas] = await pool.query(
    `SELECT p.id_producto, p.nombre FROM producto p
       JOIN catalogo c ON c.id_catalogo = p.id_catalogo
      WHERE c.id_vendedor = ? AND c.visible = 1 AND p.visible = 1
        AND NOT EXISTS (
          SELECT 1 FROM evento_vendedor ev JOIN tipo_evento te ON te.id_tipo_evento = ev.id_tipo_evento
           WHERE ev.id_producto = p.id_producto AND te.clave = 'vista_producto'
             AND ev.fecha BETWEEN ? AND ?
        )
      ORDER BY p.id_producto`,
    [req.usuario.id, rango.inicio, rango.fin]
  );
  res.json({
    recorrido: { horas, km, por_dia: dias },
    visitas: { total: visitas.length, previo: Number(eventosPrevios[0].total), por_hora: porHora, por_dia_semana: porDiaSemana },
    favoritos: { total: Number(favoritos.total), nuevos },
    top_productos: topProductos,
    sin_vistas: sinVistas,
  });
});

module.exports = { obtenerEstadisticasVendedor, parsearRango, kmEntre };
