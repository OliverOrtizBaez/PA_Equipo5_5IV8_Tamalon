const { pool, transaccion } = require('../config/db');
const C = require('../config/constantes');
const MSG = require('../utils/mensajes');
const { ErrorHttp, ErrorValidacion, asyncHandler } = require('../utils/errores');
const { entero } = require('../utils/validaciones');

const RADIO_MAX_KM = 10;
const TIERRA_KM = 6371;

function numeroQuery(valor) {
  if (typeof valor !== 'string' || !valor.trim()) return null;
  const numero = Number(valor);
  return Number.isFinite(numero) ? numero : null;
}

function validarCoordenadas(latitud, longitud) {
  return typeof latitud === 'number' && Number.isFinite(latitud) && latitud >= -90 && latitud <= 90 &&
    typeof longitud === 'number' && Number.isFinite(longitud) && longitud >= -180 && longitud <= 180;
}

function buscarLike(valor) {
  return `%${valor.replace(/[!%_]/g, '!$&')}%`;
}

function distanciaMetros(a, b) {
  const rad = (grados) => grados * Math.PI / 180;
  const dLat = rad(b.latitud - a.latitud);
  const dLng = rad(b.longitud - a.longitud);
  const h = Math.sin(dLat / 2) ** 2 +
    Math.cos(rad(a.latitud)) * Math.cos(rad(b.latitud)) * Math.sin(dLng / 2) ** 2;
  return 2 * TIERRA_KM * 1000 * Math.atan2(Math.sqrt(h), Math.sqrt(1 - h));
}

async function distanciaJornada(conn, idJornada) {
  const [puntos] = await conn.query(
    'SELECT latitud, longitud FROM ubicacion_historial WHERE id_jornada = ? ORDER BY registrado, id_punto',
    [idJornada]
  );
  let metros = 0;
  for (let i = 1; i < puntos.length; i += 1) {
    metros += distanciaMetros(
      { latitud: Number(puntos[i - 1].latitud), longitud: Number(puntos[i - 1].longitud) },
      { latitud: Number(puntos[i].latitud), longitud: Number(puntos[i].longitud) }
    );
  }
  return metros / 1000;
}

const iniciarJornada = asyncHandler(async (req, res) => {
  const jornada = await transaccion(async (conn) => {
    const [[vendedor]] = await conn.query(
      'SELECT id_vendedor FROM vendedor WHERE id_vendedor = ? FOR UPDATE',
      [req.usuario.id]
    );
    if (!vendedor) throw new ErrorHttp(404, MSG.VENDEDOR_NO_ENCONTRADO);
    const [[abierta]] = await conn.query(
      'SELECT id_jornada, inicio FROM jornada WHERE id_vendedor = ? AND fin IS NULL FOR UPDATE',
      [req.usuario.id]
    );
    if (abierta) return { jornada: abierta, existente: true };
    const [insertado] = await conn.query(
      'INSERT INTO jornada (id_vendedor, inicio) VALUES (?, ?)',
      [req.usuario.id, new Date()]
    );
    const [[nueva]] = await conn.query(
      'SELECT id_jornada, inicio FROM jornada WHERE id_jornada = ?',
      [insertado.insertId]
    );
    return { jornada: nueva, existente: false };
  });
  res.status(jornada.existente ? 200 : 201).json({ ...jornada.jornada, en_ruta: true });
});

const finalizarJornada = asyncHandler(async (req, res) => {
  const resultado = await transaccion(async (conn) => {
    const [[jornada]] = await conn.query(
      'SELECT id_jornada, inicio FROM jornada WHERE id_vendedor = ? AND fin IS NULL FOR UPDATE',
      [req.usuario.id]
    );
    if (!jornada) throw new ErrorHttp(409, 'No hay una jornada abierta');
    const fin = new Date();
    await conn.query('UPDATE jornada SET fin = ? WHERE id_jornada = ?', [fin, jornada.id_jornada]);
    const km = await distanciaJornada(conn, jornada.id_jornada);
    const minutos = Math.max(0, Math.round((fin.getTime() - new Date(jornada.inicio).getTime()) / 60000));
    return { id_jornada: jornada.id_jornada, fin, minutos, kilometros: Number(km.toFixed(3)) };
  });
  res.json(resultado);
});

const actualizarUbicacion = asyncHandler(async (req, res) => {
  const puntos = Array.isArray(req.body?.puntos)
    ? req.body.puntos
    : [{ latitud: req.body?.latitud, longitud: req.body?.longitud, precision_m: req.body?.precision_m, t: Date.now() }];
  if (!Array.isArray(puntos) || puntos.length < 1 || puntos.length > 20) {
    throw new ErrorValidacion(['Dato inválido en Puntos: debe contener de 1 a 20 ubicaciones']);
  }
  const validos = puntos.map((punto) => {
    const t = punto?.t;
    const fecha = new Date(t);
    if (!validarCoordenadas(punto?.latitud, punto?.longitud) ||
        !Number.isSafeInteger(t) || Number.isNaN(fecha.getTime()) ||
        (punto.precision_m !== undefined &&
          (typeof punto.precision_m !== 'number' || !Number.isFinite(punto.precision_m) || punto.precision_m < 0))) {
      throw new ErrorValidacion([MSG.UBICACION_INVALIDA]);
    }
    return { latitud: punto.latitud, longitud: punto.longitud, precision_m: punto.precision_m ?? null, fecha };
  }).sort((a, b) => a.fecha - b.fecha);

  const recibidos = await transaccion(async (conn) => {
    const [[jornada]] = await conn.query(
      'SELECT id_jornada FROM jornada WHERE id_vendedor = ? AND fin IS NULL FOR UPDATE',
      [req.usuario.id]
    );
    if (!jornada) throw new ErrorHttp(409, 'Debes iniciar una jornada antes de reportar ubicación');

    let [[ultimo]] = await conn.query(
      `SELECT latitud, longitud, registrado
         FROM ubicacion_historial
        WHERE id_jornada = ?
        ORDER BY registrado DESC, id_punto DESC LIMIT 1`,
      [jornada.id_jornada]
    );
    for (const punto of validos) {
      const distancia = ultimo ? distanciaMetros(
        { latitud: Number(ultimo.latitud), longitud: Number(ultimo.longitud) },
        punto
      ) : Infinity;
      const segundos = ultimo ? (punto.fecha.getTime() - new Date(ultimo.registrado).getTime()) / 1000 : Infinity;
      if (!ultimo || distancia >= C.MIN_GPS_METROS || segundos >= C.MIN_GPS_SEG) {
        await conn.query(
          `INSERT INTO ubicacion_historial (id_jornada, latitud, longitud, registrado)
           VALUES (?, ?, ?, ?)`,
          [jornada.id_jornada, punto.latitud, punto.longitud, punto.fecha]
        );
        ultimo = { ...punto, registrado: punto.fecha };
      }
    }

    const masReciente = validos[validos.length - 1];
    await conn.query(
      `INSERT INTO ubicacion_actual (id_vendedor, latitud, longitud, precision_m)
       VALUES (?, ?, ?, ?)
       ON DUPLICATE KEY UPDATE latitud = VALUES(latitud), longitud = VALUES(longitud),
                               precision_m = VALUES(precision_m), actualizado = CURRENT_TIMESTAMP(3)`,
      [req.usuario.id, masReciente.latitud, masReciente.longitud, masReciente.precision_m]
    );
    const [[ubicacion]] = await conn.query(
      'SELECT latitud, longitud, actualizado FROM ubicacion_actual WHERE id_vendedor = ?',
      [req.usuario.id]
    );
    return { recibidos: validos.length, ubicacion };
  });
  res.json({
    mensaje: 'Ubicación actualizada',
    ubicacion: {
      latitud: Number(recibidos.ubicacion.latitud),
      longitud: Number(recibidos.ubicacion.longitud),
      disponibilidad: 'En ruta',
      ultima_actualizacion: recibidos.ubicacion.actualizado,
    },
  });
});

const obtenerCercanos = asyncHandler(async (req, res) => {
  const latitud = numeroQuery(req.query.lat);
  const longitud = numeroQuery(req.query.lng);
  const radioKm = req.query.radio_km === undefined ? 3 : numeroQuery(req.query.radio_km);
  const idTipo = req.query.id_tipo_vendedor === undefined ? null : entero(req.query.id_tipo_vendedor);
  const buscar = req.query.buscar;
  if (!validarCoordenadas(latitud, longitud) ||
      radioKm === null || radioKm <= 0 || radioKm > RADIO_MAX_KM ||
      (req.query.id_tipo_vendedor !== undefined && idTipo === null) ||
      (buscar !== undefined && (typeof buscar !== 'string' || buscar.length > 100))) {
    throw new ErrorValidacion([MSG.FILTRO_UBICACION_INVALIDO]);
  }

  const limiteVigencia = new Date(Date.now() - C.UBICACION_VIGENCIA_SEG * 1000);
  const condiciones = ['j.fin IS NULL', 'ua.actualizado >= ?', 'u.id_estado_cuenta = 1'];
  const parametros = [limiteVigencia];
  if (idTipo !== null) {
    condiciones.push('v.id_tipo_vendedor = ?');
    parametros.push(idTipo);
  }
  if (buscar) {
    const patron = buscarLike(buscar);
    condiciones.push(`(CONCAT(u.nombre, ' ', u.apellidos) LIKE ? ESCAPE '!'
      OR EXISTS (SELECT 1 FROM catalogo c JOIN producto p ON p.id_catalogo = c.id_catalogo
        WHERE c.id_vendedor = v.id_vendedor AND c.visible = 1 AND p.visible = 1
          AND p.nombre LIKE ? ESCAPE '!'))`);
    parametros.push(patron, patron);
  }
  const [filas] = await pool.query(
    `SELECT * FROM (
       SELECT u.id_usuario, CONCAT(u.nombre, ' ', u.apellidos) AS nombre_completo,
              u.foto_perfil, 'vendedor' AS rol,
              CASE WHEN pv.id_vendedor IS NOT NULL THEN 'premium' ELSE 'gratuito' END AS plan,
              ua.latitud, ua.longitud, ua.actualizado AS ultima_actualizacion,
              ST_Distance_Sphere(POINT(?, ?), POINT(ua.longitud, ua.latitud)) AS distancia_m
         FROM jornada j
         JOIN vendedor v ON v.id_vendedor = j.id_vendedor
         JOIN usuario u ON u.id_usuario = v.id_vendedor
         LEFT JOIN premium_vendedor pv ON pv.id_vendedor = v.id_vendedor
         JOIN ubicacion_actual ua ON ua.id_vendedor = v.id_vendedor
        WHERE ${condiciones.join(' AND ')}
     ) candidatos
      WHERE distancia_m <= ?
      ORDER BY distancia_m LIMIT 50`,
    [longitud, latitud, ...parametros, radioKm * 1000]
  );
  res.json({
    vendedores: filas.map((fila) => ({
      id_usuario: Number(fila.id_usuario),
      nombre_completo: fila.nombre_completo,
      foto_perfil: fila.foto_perfil,
      rol: fila.rol,
      plan: fila.plan,
      latitud: Number(fila.latitud),
      longitud: Number(fila.longitud),
      distancia_m: Number(fila.distancia_m),
      ultima_actualizacion: fila.ultima_actualizacion,
    })),
  });
});

const obtenerUbicacionVendedor = asyncHandler(async (req, res) => {
  const idVendedor = entero(req.params.id);
  if (idVendedor === null) throw new ErrorHttp(400, MSG.FILTRO_UBICACION_INVALIDO);
  const limiteVigencia = new Date(Date.now() - C.UBICACION_VIGENCIA_SEG * 1000);
  const [[vendedor]] = await pool.query(
    `SELECT (j.id_jornada IS NOT NULL AND ua.actualizado >= ?) AS en_ruta,
            ua.latitud, ua.longitud, ua.actualizado AS ultima_actualizacion
       FROM vendedor v JOIN usuario u ON u.id_usuario = v.id_vendedor
       LEFT JOIN jornada j ON j.id_vendedor = v.id_vendedor AND j.fin IS NULL
       LEFT JOIN ubicacion_actual ua ON ua.id_vendedor = v.id_vendedor
      WHERE v.id_vendedor = ? AND u.id_estado_cuenta = 1`,
    [limiteVigencia, idVendedor]
  );
  if (!vendedor) throw new ErrorHttp(404, MSG.VENDEDOR_NO_ENCONTRADO);
  if (!vendedor.en_ruta) return res.json({ en_ruta: false });
  res.json({
    en_ruta: true,
    latitud: vendedor.latitud,
    longitud: vendedor.longitud,
    ultima_actualizacion: vendedor.ultima_actualizacion,
  });
});

const listarVendedores = asyncHandler(async (req, res) => {
  const pagina = req.query.pagina === undefined ? 1 : entero(req.query.pagina);
  const limite = req.query.limite === undefined ? 20 : entero(req.query.limite);
  const buscar = req.query.buscar;
  if (!Number.isSafeInteger(pagina) || pagina < 1 || !Number.isSafeInteger(limite) || limite < 1 || limite > 50 ||
      (buscar !== undefined && (typeof buscar !== 'string' || buscar.length > 100))) {
    throw new ErrorValidacion([MSG.FILTRO_UBICACION_INVALIDO]);
  }
  const where = ['u.id_estado_cuenta = 1'];
  const params = [];
  if (buscar) {
    where.push("CONCAT(u.nombre, ' ', u.apellidos) LIKE ? ESCAPE '!'");
    params.push(buscarLike(buscar));
  }
  const offset = (pagina - 1) * limite;
  const [filas] = await pool.query(
    `SELECT u.id_usuario, CONCAT(u.nombre, ' ', u.apellidos) AS nombre_completo,
            u.foto_perfil, (pv.id_vendedor IS NOT NULL) AS es_premium,
            v.id_tipo_vendedor, tv.nombre AS tipo_vendedor, tv.icono AS icono_vendedor,
            (j.id_jornada IS NOT NULL AND ua.actualizado >= ?) AS en_ruta
       FROM vendedor v JOIN usuario u ON u.id_usuario = v.id_vendedor
       JOIN tipo_vendedor tv ON tv.id_tipo_vendedor = v.id_tipo_vendedor
       LEFT JOIN premium_vendedor pv ON pv.id_vendedor = v.id_vendedor
       LEFT JOIN jornada j ON j.id_vendedor = v.id_vendedor AND j.fin IS NULL
       LEFT JOIN ubicacion_actual ua ON ua.id_vendedor = v.id_vendedor
      WHERE ${where.join(' AND ')}
      ORDER BY u.nombre, u.apellidos, u.id_usuario LIMIT ? OFFSET ?`,
    [new Date(Date.now() - C.UBICACION_VIGENCIA_SEG * 1000), ...params, limite, offset]
  );
  res.json({
    vendedores: filas.map((fila) => ({ ...fila, es_premium: Boolean(fila.es_premium), en_ruta: Boolean(fila.en_ruta) })),
    pagina,
    limite,
  });
});

module.exports = {
  iniciarJornada, finalizarJornada, actualizarUbicacion, obtenerCercanos,
  obtenerUbicacionVendedor, listarVendedores,
};
