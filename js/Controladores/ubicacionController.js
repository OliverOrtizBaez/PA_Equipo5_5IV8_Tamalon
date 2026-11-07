const { pool } = require('../config/db');
const C = require('../config/constantes');
const MSG = require('../utils/mensajes');
const { ErrorHttp, ErrorValidacion, asyncHandler } = require('../utils/errores');
const { entero } = require('../utils/validaciones');

const ROLES_VENDEDOR = [C.ROLES.VENDEDOR_GRATUITO, C.ROLES.VENDEDOR_PREMIUM];
const RADIO_MAX_KM = 10;

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

function cajaGeografica(latitud, longitud, radioKm) {
  const deltaLat = radioKm / 110.574;
  const minLat = Math.max(-90, latitud - deltaLat);
  const maxLat = Math.min(90, latitud + deltaLat);
  const maxAbsLat = Math.max(Math.abs(minLat), Math.abs(maxLat));
  const cosLat = maxAbsLat >= 90 ? 0 : Math.cos(maxAbsLat * Math.PI / 180);
  const deltaLng = cosLat < 1e-12 ? 180 : Math.min(180, radioKm / (111.32 * cosLat));
  const minLng = longitud - deltaLng;
  const maxLng = longitud + deltaLng;

  let condicionLongitud = 'uv.longitud BETWEEN ? AND ?';
  let parametrosLongitud = [minLng, maxLng];
  if (deltaLng >= 180) {
    condicionLongitud = 'uv.longitud BETWEEN -180 AND 180';
    parametrosLongitud = [];
  } else if (minLng < -180) {
    condicionLongitud = '(uv.longitud >= ? OR uv.longitud <= ?)';
    parametrosLongitud = [minLng + 360, maxLng];
  } else if (maxLng > 180) {
    condicionLongitud = '(uv.longitud >= ? OR uv.longitud <= ?)';
    parametrosLongitud = [minLng, maxLng - 360];
  }

  return { minLat, maxLat, condicionLongitud, parametrosLongitud };
}

const actualizarUbicacion = asyncHandler(async (req, res) => {
  const { latitud, longitud } = req.body || {};
  if (!validarCoordenadas(latitud, longitud)) {
    throw new ErrorValidacion([MSG.UBICACION_INVALIDA]);
  }

  const ahora = new Date();
  await pool.query(
    `INSERT INTO ubi_vendedor
       (id_vendedor, latitud, longitud, disponibilidad, ultima_actualizacion)
     VALUES (?, ?, ?, ?, ?)
     ON DUPLICATE KEY UPDATE
       latitud = VALUES(latitud),
       longitud = VALUES(longitud),
       disponibilidad = VALUES(disponibilidad),
       ultima_actualizacion = VALUES(ultima_actualizacion)`,
    [req.usuario.id, latitud, longitud, 'En ruta', ahora]
  );

  res.json({
    mensaje: 'Ubicación actualizada',
    ubicacion: {
      latitud,
      longitud,
      disponibilidad: 'En ruta',
      ultima_actualizacion: ahora,
    },
  });
});

const fueraDeRuta = asyncHandler(async (req, res) => {
  await pool.query(
    'UPDATE ubi_vendedor SET disponibilidad = ? WHERE id_vendedor = ?',
    ['Fuera de ruta', req.usuario.id]
  );
  res.json({ mensaje: 'Fuera de ruta', disponibilidad: 'Fuera de ruta' });
});

const obtenerCercanos = asyncHandler(async (req, res) => {
  const latitud = numeroQuery(req.query.lat);
  const longitud = numeroQuery(req.query.lng);
  const radioKm = req.query.radio_km === undefined ? 3 : numeroQuery(req.query.radio_km);
  const categoria = req.query.categoria;
  const buscar = req.query.buscar;
  if (!validarCoordenadas(latitud, longitud) ||
      radioKm === null || radioKm <= 0 || radioKm > RADIO_MAX_KM ||
      (categoria !== undefined && (typeof categoria !== 'string' || categoria.length > 50)) ||
      (buscar !== undefined && (typeof buscar !== 'string' || buscar.length > 100))) {
    throw new ErrorValidacion([MSG.FILTRO_UBICACION_INVALIDO]);
  }

  const caja = cajaGeografica(latitud, longitud, radioKm);
  const ahora = new Date();
  const limiteVigencia = new Date(ahora.getTime() - C.UBICACION_VIGENCIA_SEG * 1000);
  const condiciones = [
    'u.rol IN (?, ?)',
    'u.estado_cuenta = ?',
    'uv.disponibilidad = ?',
    'uv.ultima_actualizacion >= ?',
    'uv.latitud BETWEEN ? AND ?',
    caja.condicionLongitud,
  ];
  const parametros = [
    C.ROLES.VENDEDOR_GRATUITO,
    C.ROLES.VENDEDOR_PREMIUM,
    C.ESTADOS_CUENTA.ACTIVA,
    'En ruta',
    limiteVigencia,
    caja.minLat,
    caja.maxLat,
    ...caja.parametrosLongitud,
  ];

  if (categoria) {
    condiciones.push(
      `EXISTS (
         SELECT 1 FROM catalogo c
         JOIN producto p ON p.id_catalogo = c.id_catalogo
        WHERE c.id_vendedor = u.id_usuario AND c.estado = 'Visible'
          AND p.estado = 'Visible' AND p.categoria = ?
       )`
    );
    parametros.push(categoria);
  }

  if (buscar) {
    const patron = buscarLike(buscar);
    condiciones.push(
      `(u.nombre_completo LIKE ? ESCAPE '!'
        OR EXISTS (
          SELECT 1 FROM catalogo c
          JOIN producto p ON p.id_catalogo = c.id_catalogo
         WHERE c.id_vendedor = u.id_usuario AND c.estado = 'Visible'
           AND p.estado = 'Visible' AND p.nombre LIKE ? ESCAPE '!'
        ))`
    );
    parametros.push(patron, patron);
  }

  const [vendedores] = await pool.query(
    `SELECT * FROM (
       SELECT u.id_usuario, u.nombre_completo, u.foto_perfil, u.rol,
              uv.latitud, uv.longitud, uv.ultima_actualizacion,
              ST_Distance_Sphere(POINT(?, ?), POINT(uv.longitud, uv.latitud)) AS distancia_m
         FROM ubi_vendedor uv
         JOIN usuarios u ON u.id_usuario = uv.id_vendedor
        WHERE ${condiciones.join(' AND ')}
     ) AS candidatos
      WHERE distancia_m <= ?
      ORDER BY distancia_m ASC
      LIMIT 50`,
    [longitud, latitud, ...parametros, radioKm * 1000]
  );

  res.json({
    vendedores: vendedores.map((vendedor) => ({
      ...vendedor,
      distancia_m: Number(vendedor.distancia_m),
    })),
  });
});

const obtenerUbicacionVendedor = asyncHandler(async (req, res) => {
  const idVendedor = entero(req.params.id);
  if (idVendedor === null) throw new ErrorHttp(400, MSG.FILTRO_UBICACION_INVALIDO);

  const ahora = new Date();
  const limiteVigencia = new Date(ahora.getTime() - C.UBICACION_VIGENCIA_SEG * 1000);
  const [filas] = await pool.query(
    `SELECT uv.latitud, uv.longitud, uv.ultima_actualizacion
       FROM usuarios u
       LEFT JOIN ubi_vendedor uv
         ON uv.id_vendedor = u.id_usuario
        AND uv.disponibilidad = ?
        AND uv.ultima_actualizacion >= ?
      WHERE u.id_usuario = ? AND u.rol IN (?, ?) AND u.estado_cuenta = ?
      LIMIT 1`,
    ['En ruta', limiteVigencia, idVendedor, ...ROLES_VENDEDOR, C.ESTADOS_CUENTA.ACTIVA]
  );
  const vendedor = filas[0];
  if (!vendedor) throw new ErrorHttp(404, MSG.VENDEDOR_NO_ENCONTRADO);
  if (vendedor.ultima_actualizacion === null) return res.json({ en_ruta: false });

  res.json({
    en_ruta: true,
    latitud: vendedor.latitud,
    longitud: vendedor.longitud,
    ultima_actualizacion: vendedor.ultima_actualizacion,
  });
});

const listarVendedores = asyncHandler(async (req, res) => {
  const pagina = req.query.pagina === undefined ? 1 : entero(req.query.pagina);
  const limiteSolicitado = req.query.limite === undefined ? 20 : entero(req.query.limite);
  const buscar = req.query.buscar;
  if (pagina === null || limiteSolicitado === null || limiteSolicitado > 50 ||
      (buscar !== undefined && (typeof buscar !== 'string' || buscar.length > 100))) {
    throw new ErrorValidacion([MSG.FILTRO_UBICACION_INVALIDO]);
  }

  const limite = limiteSolicitado;
  const offset = (pagina - 1) * limite;
  if (!Number.isSafeInteger(offset)) throw new ErrorValidacion([MSG.FILTRO_UBICACION_INVALIDO]);

  const condiciones = ['u.rol IN (?, ?)', 'u.estado_cuenta = ?'];
  const parametros = [...ROLES_VENDEDOR, C.ESTADOS_CUENTA.ACTIVA];
  if (buscar) {
    condiciones.push("u.nombre_completo LIKE ? ESCAPE '!'");
    parametros.push(buscarLike(buscar));
  }

  const ahora = new Date();
  const limiteVigencia = new Date(ahora.getTime() - C.UBICACION_VIGENCIA_SEG * 1000);
  const [filas] = await pool.query(
    `SELECT u.id_usuario, u.nombre_completo, u.foto_perfil, u.rol,
            CASE WHEN uv.id_vendedor IS NOT NULL THEN 1 ELSE 0 END AS en_ruta
       FROM usuarios u
       LEFT JOIN ubi_vendedor uv
         ON uv.id_vendedor = u.id_usuario
        AND uv.disponibilidad = ?
        AND uv.ultima_actualizacion >= ?
      WHERE ${condiciones.join(' AND ')}
      ORDER BY u.nombre_completo ASC, u.id_usuario ASC
      LIMIT ? OFFSET ?`,
    ['En ruta', limiteVigencia, ...parametros, limite, offset]
  );

  res.json({
    vendedores: filas.map((vendedor) => ({ ...vendedor, en_ruta: Boolean(vendedor.en_ruta) })),
    pagina,
    limite,
  });
});

module.exports = {
  actualizarUbicacion,
  fueraDeRuta,
  obtenerCercanos,
  obtenerUbicacionVendedor,
  listarVendedores,
};
