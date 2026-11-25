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

test('historial GPS descarta un punto a menos de 25 m antes de 60 s', async () => {
  let inserts = 0;
  queryHandler = async (sql) => {
    if (sql.includes('FROM jornada WHERE id_vendedor')) return [[{ id_jornada: 55 }]];
    if (sql.includes('FROM ubicacion_historial')) {
      return [[{ latitud: 0, longitud: 0, registrado: new Date() }]];
    }
    if (sql.startsWith('INSERT INTO ubicacion_historial')) { inserts += 1; return [{ affectedRows: 1 }]; }
    return [{ affectedRows: 1 }];
  };
  const { res, error } = await llamar(ubicacion.actualizarUbicacion, {
    usuario: { id: 9, tipo: 'Vendedor' },
    body: { puntos: [{ latitud: 0, longitud: 0.00005, t: Date.now() }] },
  });
  assert.equal(error, undefined);
  assert.equal(inserts, 0);
  assert.deepEqual(res.body, { en_ruta: true, recibido: 1 });
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
