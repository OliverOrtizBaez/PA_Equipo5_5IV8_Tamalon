const test = require('node:test');
const assert = require('node:assert/strict');
const path = require('node:path');

let queryHandler;
const fakePool = {
  query: (...args) => queryHandler(...args),
};
const fakeConnection = { query: (...args) => queryHandler(...args) };
const dbPath = path.resolve(__dirname, '../js/config/db.js');
require.cache[dbPath] = {
  id: dbPath,
  filename: dbPath,
  loaded: true,
  exports: {
    pool: fakePool,
    transaccion: (fn) => fn(fakeConnection),
  },
};

const { crearCuenta } = require('../js/services/cuentasService');
const pagos = require('../js/Controladores/suscripcionController');
const ubicacion = require('../js/Controladores/ubicacionController');
const estadisticas = require('../js/Controladores/estadisticasController');
const admin = require('../js/Controladores/adminController');

async function llamar(handler, req) {
  const res = {
    statusCode: 200,
    body: undefined,
    status(codigo) { this.statusCode = codigo; return this; },
    json(body) { this.body = body; return this; },
    end() { return this; },
  };
  let error;
  await handler(req, res, (err) => { error = err; });
  return { res, error };
}

test('registro de vendedor guarda el tipo y control de acceso dentro de la transacción', async () => {
  const llamadas = [];
  queryHandler = async (sql, params = []) => {
    llamadas.push({ sql, params });
    if (sql.includes('FROM tipo_vendedor')) return [[{ id_tipo_vendedor: 3, nombre: 'Basura', icono: 'ic_tipo_basura' }]];
    if (sql.startsWith('INSERT INTO usuario')) return [{ insertId: 24 }];
    return [{ affectedRows: 1 }];
  };
  const cuenta = await crearCuenta({
    nombre: 'Rosa',
    apellidos: 'Pérez',
    correo: 'rosa@example.test',
    contrasena: 'Secreta123',
    confirmar_contrasena: 'Secreta123',
    id_tipo_vendedor: 3,
  }, 'Vendedor');
  assert.equal(cuenta.rol, 'Vendedor');
  assert.equal(cuenta.es_premium, false);
  assert.equal(cuenta.id_tipo_vendedor, 3);
  assert.ok(llamadas.some(({ sql }) => sql.startsWith('INSERT INTO vendedor')));
  assert.ok(llamadas.some(({ sql }) => sql.startsWith('INSERT INTO control_acceso')));
});

test('pago aprobado Premium es único y el segundo intento responde 409', async () => {
  process.env.PAGOS_MODO = 'demo';
  let premium = false;
  queryHandler = async (sql) => {
    if (sql.startsWith('SELECT id_vendedor FROM vendedor')) return [[{ id_vendedor: 7 }]];
    if (sql.startsWith('SELECT id_vendedor FROM premium_vendedor')) {
      return [premium ? [{ id_vendedor: 7 }] : []];
    }
    if (sql.startsWith('SELECT id_catalogo, visible FROM catalogo')) return [[]];
    if (sql.startsWith('SELECT id_producto')) return [[]];
    if (sql.startsWith('INSERT INTO pago')) return [{ insertId: 90 }];
    if (sql.startsWith('INSERT INTO premium_vendedor')) { premium = true; return [{ affectedRows: 1 }]; }
    return [{ affectedRows: 1 }];
  };
  const req = { usuario: { id: 7, tipo: 'Vendedor' }, body: {} };
  const primero = await llamar(pagos.procesarPagoPremium, req);
  assert.equal(primero.res.statusCode, 201);
  assert.deepEqual(primero.res.body, { es_premium: true, id_pago: 90 });
  const segundo = await llamar(pagos.procesarPagoPremium, req);
  assert.equal(segundo.error.status, 409);
});

test('estadísticas responde 403 con código PREMIUM_REQUERIDO', async () => {
  queryHandler = async () => [[{ es_premium: 0 }]];
  const { error } = await llamar(estadisticas.obtenerEstadisticasVendedor, {
    usuario: { id: 18, tipo: 'Vendedor' },
    query: { desde: '2026-01-01', hasta: '2026-01-07' },
  });
  assert.equal(error.status, 403);
  assert.equal(error.extra.codigo, 'PREMIUM_REQUERIDO');
});

test('actualizar ubicación acepta latitud y longitud y conserva el filtrado del historial GPS', async () => {
  let inserts = 0;
  queryHandler = async (sql) => {
    if (sql.includes('FROM jornada WHERE id_vendedor')) return [[{ id_jornada: 55 }]];
    if (sql.includes('FROM ubicacion_historial')) {
      return [[{ latitud: 19.43, longitud: -99.13, registrado: new Date() }]];
    }
    if (sql.startsWith('INSERT INTO ubicacion_historial')) { inserts += 1; return [{ affectedRows: 1 }]; }
    if (sql.startsWith('SELECT latitud, longitud, actualizado FROM ubicacion_actual')) {
      return [[{ latitud: '19.43', longitud: '-99.13', actualizado: new Date() }]];
    }
    return [{ affectedRows: 1 }];
  };
  const { res, error } = await llamar(ubicacion.actualizarUbicacion, {
    usuario: { id: 9, tipo: 'Vendedor' },
    body: { latitud: 19.43, longitud: -99.13 },
  });
  assert.equal(error, undefined);
  assert.equal(inserts, 0);
  assert.equal(res.body.ubicacion.latitud, 19.43);
  assert.equal(res.body.ubicacion.longitud, -99.13);
  assert.equal(res.body.ubicacion.disponibilidad, 'En ruta');
  assert.ok(res.body.ubicacion.ultima_actualizacion instanceof Date);
});

test('actualizar ubicación conserva el formato histórico de puntos', async () => {
  let inserts = 0;
  queryHandler = async (sql) => {
    if (sql.includes('FROM jornada WHERE id_vendedor')) return [[{ id_jornada: 55 }]];
    if (sql.includes('FROM ubicacion_historial')) {
      return [[{ latitud: 0, longitud: 0, registrado: new Date() }]];
    }
    if (sql.startsWith('INSERT INTO ubicacion_historial')) { inserts += 1; return [{ affectedRows: 1 }]; }
    if (sql.startsWith('SELECT latitud, longitud, actualizado FROM ubicacion_actual')) {
      return [[{ latitud: 0, longitud: 0.00005, actualizado: new Date() }]];
    }
    return [{ affectedRows: 1 }];
  };
  const { res, error } = await llamar(ubicacion.actualizarUbicacion, {
    usuario: { id: 9, tipo: 'Vendedor' },
    body: { puntos: [{ latitud: 0, longitud: 0.00005, t: Date.now() }] },
  });
  assert.equal(error, undefined);
  assert.equal(inserts, 0);
  assert.equal(res.body.ubicacion.disponibilidad, 'En ruta');
});

test('cercanos filtra jornada abierta y ubicación reciente con el contrato móvil', async () => {
  let sqlCercanos = '';
  let paramsCercanos = [];
  queryHandler = async (sql, params = []) => {
    sqlCercanos = sql;
    paramsCercanos = params;
    return [[{
      id_usuario: 14,
      nombre_completo: 'Vendedora Ejemplo',
      foto_perfil: 'foto.jpg',
      rol: 'vendedor',
      plan: 'premium',
      latitud: '19.43',
      longitud: '-99.13',
      distancia_m: '150',
      ultima_actualizacion: new Date(),
    }]];
  };
  const { res, error } = await llamar(ubicacion.obtenerCercanos, {
    query: { lat: '19.4', lng: '-99.1', radio_km: '2' },
  });
  assert.equal(error, undefined);
  assert.match(sqlCercanos, /j\.fin IS NULL/);
  assert.match(sqlCercanos, /ua\.actualizado >= \?/);
  assert.match(sqlCercanos, /u\.id_estado_cuenta = 1/);
  assert.equal(paramsCercanos[0], -99.1);
  assert.equal(paramsCercanos[1], 19.4);
  assert.ok(paramsCercanos[2] instanceof Date);
  assert.equal(paramsCercanos[3], 2000);
  assert.deepEqual(Object.keys(res.body.vendedores[0]), [
    'id_usuario', 'nombre_completo', 'foto_perfil', 'rol', 'plan',
    'latitud', 'longitud', 'distancia_m', 'ultima_actualizacion',
  ]);
  assert.equal(res.body.vendedores[0].latitud, 19.43);
  assert.equal(res.body.vendedores[0].distancia_m, 150);
});

test('finalizar jornada responde kilometros con el nombre del contrato Android', async () => {
  queryHandler = async (sql) => {
    if (sql.startsWith('SELECT id_jornada, inicio FROM jornada')) {
      return [[{ id_jornada: 55, inicio: new Date(Date.now() - 60000) }]];
    }
    if (sql.startsWith('SELECT latitud, longitud FROM ubicacion_historial')) {
      return [[
        { latitud: 0, longitud: 0 },
        { latitud: 0, longitud: 0.01 },
      ]];
    }
    return [{ affectedRows: 1 }];
  };
  const { res, error } = await llamar(ubicacion.finalizarJornada, {
    usuario: { id: 9, tipo: 'Vendedor' },
  });
  assert.equal(error, undefined);
  assert.equal(typeof res.body.minutos, 'number');
  assert.equal(typeof res.body.kilometros, 'number');
  assert.equal(Object.prototype.hasOwnProperty.call(res.body, 'km'), false);
});

test('listado admin sin buscar devuelve todos y ordena por nombre, apellidos e ID', async () => {
  let sqlListado = '';
  queryHandler = async (sql) => {
    if (sql.startsWith('SELECT COUNT(*)')) return [[{ total: 1 }]];
    sqlListado = sql;
    return [[{
      id_usuario: 1, nombre_completo: 'Ana Díaz', correo: 'ana@example.test',
      rol: 'Cliente', es_premium: 0, estado_cuenta: 'Activa',
    }]];
  };
  const { res, error } = await llamar(admin.buscarUsuarios, { query: {} });
  assert.equal(error, undefined);
  assert.equal(res.body.usuarios.length, 1);
  assert.match(sqlListado, /ORDER BY nombre, apellidos, id_usuario/);
  assert.doesNotMatch(sqlListado, /WHERE/i);
});
