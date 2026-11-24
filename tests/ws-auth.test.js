// Prueba de integracion del canal /ws/auth + login HTTP + middleware, con el servidor real
// y una BD simulada en memoria (no hace falta MySQL). Ejecutar: npm test
process.env.JWT_SECRET = 'prueba-secreto-de-al-menos-32-caracteres!!';
process.env.WS_REVALIDAR_MS = '300';
delete process.env.CORS_ORIGIN;

const test = require('node:test');
const assert = require('node:assert/strict');
const http = require('http');
const path = require('path');
const bcrypt = require('bcrypt');
const express = require('express');
const WebSocket = require('ws');

// ------------------------------------------------------------------ BD simulada
const HASH = bcrypt.hashSync('Secreta123', 4);
const bd = {
  usuarios: new Map(), control: new Map(), sesiones: [], logins: [], sigId: 1,
};
function reiniciarBd() {
  bd.usuarios.clear(); bd.control.clear(); bd.sesiones.length = 0; bd.logins.length = 0; bd.sigId = 1;
  const base = { contrasena: HASH, tipo: 'Cliente', plan: 'Gratuito', rol: 'Cliente Gratuito',
    estado_cuenta: 'Activa', zona_horaria: 'America/Mexico_City', telefono: null, foto_perfil: null, fin_suspension: null };
  bd.usuarios.set(1, { ...base, id_usuario: 1, nombre_completo: 'Ana', correo: 'ana@correo.com' });
  bd.usuarios.set(2, { ...base, id_usuario: 2, nombre_completo: 'Beto', correo: 'beto@correo.com' });
  bd.usuarios.set(3, { ...base, id_usuario: 3, nombre_completo: 'Admin', correo: 'admin@correo.com', tipo: 'Administrador', plan: null, rol: 'Administrador' });
  bd.usuarios.set(4, { ...base, id_usuario: 4, nombre_completo: 'Susp', correo: 'susp@correo.com', estado_cuenta: 'Suspendida indefinidamente' });
}
const filaUsuario = (u) => ({ ...u });

const pool = {
  async query(sql, p = []) {
    const s = sql.replace(/\s+/g, ' ').trim();
    if (s.includes('FROM vw_usuario u LEFT JOIN control_acceso')) {
      const u = [...bd.usuarios.values()].find((x) => x.correo === p[0]);
      if (!u) return [[]];
      const c = bd.control.get(u.id_usuario) || { intentos_fallidos: 0, ultimo_intento: null };
      return [[{ ...filaUsuario(u), intentos_fallidos: c.intentos_fallidos, ultimo_intento: c.ultimo_intento }]];
    }
    if (s.startsWith('INSERT INTO control_acceso')) {
      const [id, ahora, , limite] = p;
      const c = bd.control.get(id);
      if (!c || !c.inicio_intentos || c.inicio_intentos < limite) bd.control.set(id, { intentos_fallidos: 1, inicio_intentos: ahora, ultimo_intento: ahora });
      else bd.control.set(id, { ...c, intentos_fallidos: c.intentos_fallidos + 1, ultimo_intento: ahora });
      return [{ affectedRows: 1 }];
    }
    if (s.startsWith('UPDATE control_acceso SET intentos_fallidos = 0')) { bd.control.delete(p[0]); return [{ affectedRows: 1 }]; }
    if (s.startsWith('UPDATE usuarios SET zona_horaria')) { bd.usuarios.get(p[1]).zona_horaria = p[0]; return [{ affectedRows: 1 }]; }
    if (s.startsWith('UPDATE usuarios SET estado_cuenta')) return [{ affectedRows: 0 }];
    if (s.startsWith('INSERT INTO sesion')) {
      const fila = { id_sesion: bd.sigId++, id_usuario: p[0], token: p[1], fecha_expiracion: p[3] };
      bd.sesiones.push(fila);
      return [{ insertId: fila.id_sesion }];
    }
    if (s.startsWith('INSERT INTO registro_login')) { bd.logins.push(p[0]); return [{ affectedRows: 1 }]; }
    if (s.includes('FROM sesion s JOIN vw_usuario u')) {
      const ses = bd.sesiones.find((x) => x.token === p[0] && x.id_usuario === p[1]);
      if (!ses) return [[]];
      return [[{ ...filaUsuario(bd.usuarios.get(ses.id_usuario)), id_sesion: ses.id_sesion, fecha_expiracion: ses.fecha_expiracion }]];
    }
    if (s.includes('FROM sesion s JOIN usuarios u')) {
      const hashes = p[0][0];
      return [bd.sesiones.filter((x) => hashes.includes(x.token)).map((x) => ({
        token: x.token, fecha_expiracion: x.fecha_expiracion, estado_cuenta: bd.usuarios.get(x.id_usuario).estado_cuenta,
      }))];
    }
    if (s.startsWith('DELETE FROM sesion WHERE id_sesion')) {
      const antes = bd.sesiones.length;
      bd.sesiones.splice(0, antes, ...bd.sesiones.filter((x) => x.id_sesion !== p[0]));
      return [{ affectedRows: antes - bd.sesiones.length }];
    }
    if (s.startsWith('DELETE FROM sesion WHERE id_usuario')) {
      const antes = bd.sesiones.length;
      bd.sesiones.splice(0, antes, ...bd.sesiones.filter((x) => x.id_usuario !== p[0]));
      return [{ affectedRows: antes - bd.sesiones.length }];
    }
    throw new Error(`SQL no simulado en la prueba: ${s.slice(0, 90)}`);
  },
};
const rutaDb = path.resolve(__dirname, '../js/config/db.js');
require.cache[rutaDb] = { id: rutaDb, filename: rutaDb, loaded: true, exports: { pool, transaccion: async (fn) => fn(pool) } };

// ------------------------------------------------------------------ servidor real
const authRoutes = require('../js/routes/authRoutes');
const { manejadorErrores } = require('../js/middlewares/errores');
const { iniciarWebSocket } = require('../js/ws/authSocket');
const sesiones = require('../js/services/sesionesService');
const { firmarJwt } = require('../js/utils/jwt');
const { hashToken } = require('../js/utils/tokens');

let servidor; let detener; let base; let urlWs;
test.before(async () => {
  const app = express();
  app.use(express.json());
  app.use('/api/auth', authRoutes);
  app.use(manejadorErrores);
  servidor = http.createServer(app);
  detener = iniciarWebSocket(servidor).detener;
  await new Promise((r) => servidor.listen(0, r));
  const { port } = servidor.address();
  base = `http://127.0.0.1:${port}`;
  urlWs = `ws://127.0.0.1:${port}/ws/auth`;
});
test.after(() => { detener(); servidor.close(); });
test.beforeEach(reiniciarBd);

// ------------------------------------------------------------------ ayudas
const POST = (ruta, cuerpo, token) => fetch(`${base}${ruta}`, {
  method: 'POST',
  headers: { 'Content-Type': 'application/json', ...(token && { Authorization: `Bearer ${token}` }) },
  body: JSON.stringify(cuerpo || {}),
}).then(async (r) => ({ status: r.status, cuerpo: await r.json() }));

const loginHttp = (correo = 'ana@correo.com', contrasena = 'Secreta123') => POST('/api/auth/login', { correo, contrasena });

function cliente(opts = {}) {
  return new Promise((resolve, reject) => {
    const ws = new WebSocket(opts.url || urlWs, opts.ws);
    const cola = []; const esperas = []; const cierres = []; let cierre = null;
    ws.on('message', (d) => {
      const m = JSON.parse(d.toString());
      const i = esperas.findIndex((e) => e.tipo === m.type);
      if (i >= 0) esperas.splice(i, 1)[0].res(m); else cola.push(m);
    });
    ws.on('close', (code) => { cierre = code; cierres.splice(0).forEach((r) => r(code)); });
    ws.on('unexpected-response', (_, res) => reject(Object.assign(new Error('rechazado'), { status: res.statusCode })));
    ws.on('error', () => {});
    ws.on('open', () => resolve({
      ws,
      enviar: (o) => ws.send(typeof o === 'string' ? o : JSON.stringify(o)),
      recibir: (tipo, ms = 3000) => new Promise((res, rej) => {
        const i = cola.findIndex((m) => m.type === tipo);
        if (i >= 0) return res(cola.splice(i, 1)[0]);
        const t = setTimeout(() => rej(new Error(`no llego "${tipo}"`)), ms);
        esperas.push({ tipo, res: (m) => { clearTimeout(t); res(m); } });
        return undefined;
      }),
      cerrado: (ms = 3000) => new Promise((res, rej) => {
        if (cierre !== null) return res(cierre);
        const t = setTimeout(() => rej(new Error('no se cerro')), ms);
        cierres.push((c) => { clearTimeout(t); res(c); });
        return undefined;
      }),
    }));
  });
}
const esperar = (ms) => new Promise((r) => setTimeout(r, ms));

async function canalAutenticado(token) {
  const c = await cliente();
  c.enviar({ type: 'auth', token });
  await c.recibir('auth_ok');
  return c;
}

// ------------------------------------------------------------------ login HTTP (compatibilidad con la app movil)
test('HTTP: el login devuelve un JWT en "token" y la forma de siempre (la app movil no cambia)', async () => {
  const r = await loginHttp();
  assert.equal(r.status, 200);
  assert.equal(r.cuerpo.token.split('.').length, 3);
  assert.ok(r.cuerpo.expira);
  assert.equal(r.cuerpo.usuario.correo, 'ana@correo.com');
  assert.equal(r.cuerpo.usuario.rol, 'Cliente Gratuito');
  assert.equal(r.cuerpo.usuario.contrasena, undefined);
  assert.equal(bd.sesiones.length, 1);
  assert.equal(bd.sesiones[0].token, hashToken(JSON.parse(Buffer.from(r.cuerpo.token.split('.')[1], 'base64url')).jti)); // la BD guarda solo el hash del jti
  assert.deepEqual(bd.logins, [1]);
});

test('HTTP: el JWT sirve como Bearer y el logout lo invalida', async () => {
  const { cuerpo: { token } } = await loginHttp();
  assert.equal((await POST('/api/auth/logout', {}, token)).status, 200);
  assert.equal(bd.sesiones.length, 0);
  assert.equal((await POST('/api/auth/logout', {}, token)).status, 401); // ya no existe la sesion
});

test('HTTP: credenciales malas y correo inexistente dan la misma respuesta 401', async () => {
  const a = await loginHttp('ana@correo.com', 'mala-contrasena');
  const b = await loginHttp('nadie@correo.com', 'mala-contrasena');
  assert.equal(a.status, 401); assert.equal(b.status, 401);
  assert.deepEqual(a.cuerpo, b.cuerpo);
  assert.equal(a.cuerpo.mensaje, 'Correo o contraseña incorrectos');
});

test('HTTP: token manipulado, basura o sin esquema Bearer -> 401', async () => {
  const { cuerpo: { token } } = await loginHttp();
  const [h, , f] = token.split('.');
  const cuerpoFalso = Buffer.from(JSON.stringify({ iss: 'tamalon', sub: '3', jti: 'x', iat: 1, exp: 9999999999 })).toString('base64url');
  for (const malo of [`${h}.${cuerpoFalso}.${f}`, 'basura', token.slice(0, -3)]) {
    assert.equal((await POST('/api/auth/logout', {}, malo)).status, 401);
  }
  const sinBearer = await fetch(`${base}/api/auth/logout`, { method: 'POST', headers: { Authorization: token } });
  assert.equal(sinBearer.status, 401);
});

test('HTTP: un JWT con firma valida pero SIN fila en sesion (revocado) -> 401', async () => {
  const { cuerpo: { token } } = await loginHttp();
  bd.sesiones.length = 0;
  assert.equal((await POST('/api/auth/logout', {}, token)).status, 401);
});

test('HTTP: un JWT firmado pero con sub de otro usuario no abre la sesion de ese usuario', async () => {
  const { cuerpo: { token } } = await loginHttp(); // sesion de Ana (id 1)
  const jti = JSON.parse(Buffer.from(token.split('.')[1], 'base64url')).jti;
  const robado = firmarJwt({ idUsuario: 3, jti }, new Date(Date.now() + 60_000)); // mismo jti, firmado para el admin
  assert.equal((await POST('/api/auth/logout', {}, robado)).status, 401);
});

test('HTTP: cuenta suspendida -> 403 "Usuario suspendido"', async () => {
  const r = await loginHttp('susp@correo.com');
  assert.equal(r.status, 403);
  assert.equal(r.cuerpo.mensaje, 'Usuario suspendido');
});

test('HTTP: 5 fallos bloquean la cuenta (429) tambien con la contrasena correcta', async () => {
  for (let i = 0; i < 5; i++) assert.equal((await loginHttp('beto@correo.com', 'mala')).status, 401);
  assert.equal((await loginHttp('beto@correo.com', 'Secreta123')).status, 429);
});

// ------------------------------------------------------------------ login por WebSocket
test('WS: login correcto devuelve login_ok con JWT y crea la sesion', async () => {
  const c = await cliente();
  c.enviar({ type: 'login', correo: 'ana@correo.com', contrasena: 'Secreta123', zona_horaria: 'America/Monterrey' });
  const m = await c.recibir('login_ok');
  assert.equal(m.token.split('.').length, 3);
  assert.equal(m.usuario.zona_horaria, 'America/Monterrey');
  assert.equal(bd.sesiones.length, 1);
  assert.deepEqual(bd.logins, [1]);
  // el token del WS tambien sirve en la API REST
  assert.equal((await POST('/api/auth/logout', {}, m.token)).status, 200);
});

test('WS: login incorrecto -> login_error 401 y se puede reintentar en la misma conexion', async () => {
  const c = await cliente();
  c.enviar({ type: 'login', correo: 'ana@correo.com', contrasena: 'mala' });
  const e = await c.recibir('login_error');
  assert.equal(e.status, 401);
  assert.equal(e.mensaje, 'Correo o contraseña incorrectos');
  c.enviar({ type: 'login', correo: 'ana@correo.com', contrasena: 'Secreta123' });
  await c.recibir('login_ok');
  c.ws.close();
});

test('WS: correo inexistente y contrasena mala son indistinguibles', async () => {
  const c = await cliente();
  c.enviar({ type: 'login', correo: 'ana@correo.com', contrasena: 'mala' });
  const a = await c.recibir('login_error');
  c.enviar({ type: 'login', correo: 'nadie@correo.com', contrasena: 'mala' });
  const b = await c.recibir('login_error');
  assert.deepEqual(a, b);
  c.ws.close();
});

test('WS: cuenta suspendida -> login_error 403', async () => {
  const c = await cliente();
  c.enviar({ type: 'login', correo: 'susp@correo.com', contrasena: 'Secreta123' });
  const e = await c.recibir('login_error');
  assert.equal(e.status, 403);
  c.ws.close();
});

test('WS: el bloqueo por intentos es el mismo en HTTP y WebSocket (RF2.3b)', async () => {
  for (let i = 0; i < 5; i++) {
    const c = await cliente();
    c.enviar({ type: 'login', correo: 'beto@correo.com', contrasena: 'mala' });
    assert.equal((await c.recibir('login_error')).status, 401);
    c.ws.close();
  }
  const r = await loginHttp('beto@correo.com', 'Secreta123');
  assert.equal(r.status, 429);
  const c = await cliente();
  c.enviar({ type: 'login', correo: 'beto@correo.com', contrasena: 'Secreta123' });
  assert.equal((await c.recibir('login_error')).status, 429);
  c.ws.close();
});

test('WS: maximo de intentos por conexion -> 429 y cierre 4429', async () => {
  const c = await cliente();
  for (let i = 0; i < 6; i++) c.enviar({ type: 'login', correo: 'nadie@correo.com', contrasena: 'x' });
  assert.equal(await c.cerrado(), 4429);
});

// ------------------------------------------------------------------ canal autenticado y revocacion en vivo
test('WS: auth con un JWT valido -> auth_ok con el perfil', async () => {
  const { cuerpo: { token } } = await loginHttp();
  const c = await cliente();
  c.enviar({ type: 'auth', token });
  const m = await c.recibir('auth_ok');
  assert.equal(m.usuario.correo, 'ana@correo.com');
  assert.ok(m.expira);
  c.ws.close();
});

test('WS: auth con token invalido, manipulado o sin sesion -> auth_error y cierre 4401', async () => {
  const { cuerpo: { token } } = await loginHttp();
  bd.sesiones.length = 0; // sesion revocada
  for (const malo of ['basura', token.slice(0, -4) + 'AAAA', token, 12345, undefined]) {
    const c = await cliente();
    c.enviar({ type: 'auth', token: malo });
    const e = await c.recibir('auth_error');
    assert.equal(e.status, 401);
    assert.equal(await c.cerrado(), 4401);
  }
});

test('WS: suspender expulsa en vivo con session_revoked y cierre 4001, y el token deja de servir', async () => {
  const { cuerpo: { token } } = await loginHttp();
  const c = await canalAutenticado(token);
  await sesiones.revocarUsuario(1, 'suspendido'); // lo que hace adminController al suspender
  const m = await c.recibir('session_revoked');
  assert.equal(m.motivo, 'suspendido');
  assert.equal(m.mensaje, 'Usuario suspendido');
  assert.equal(await c.cerrado(), 4001);
  assert.equal((await POST('/api/auth/logout', {}, token)).status, 401);
});

test('WS: la expulsion llega a TODAS las pestanas/dispositivos del usuario y solo a las suyas', async () => {
  const t1 = (await loginHttp('ana@correo.com')).cuerpo.token;
  const t2 = (await loginHttp('ana@correo.com')).cuerpo.token;
  const tBeto = (await loginHttp('beto@correo.com')).cuerpo.token;
  const [a1, a2, b] = await Promise.all([canalAutenticado(t1), canalAutenticado(t2), canalAutenticado(tBeto)]);
  sesiones.expulsarConexiones(1, 'contrasena_cambiada'); // lo que hace restablecerContrasena tras su transaccion
  assert.equal((await a1.recibir('session_revoked')).motivo, 'contrasena_cambiada');
  assert.equal((await a2.recibir('session_revoked')).motivo, 'contrasena_cambiada');
  await esperar(100);
  assert.equal(b.ws.readyState, WebSocket.OPEN);
  b.ws.close();
});

test('WS: logout HTTP expulsa solo la conexion de ESA sesion', async () => {
  const t1 = (await loginHttp()).cuerpo.token;
  const t2 = (await loginHttp()).cuerpo.token;
  const [a1, a2] = await Promise.all([canalAutenticado(t1), canalAutenticado(t2)]);
  assert.equal((await POST('/api/auth/logout', {}, t1)).status, 200);
  assert.equal((await a1.recibir('session_revoked')).motivo, 'logout');
  await esperar(100);
  assert.equal(a2.ws.readyState, WebSocket.OPEN);
  assert.equal(bd.sesiones.length, 1);
  a2.ws.close();
});

test('WS: logout por el socket borra la sesion y cierra normal (sin session_revoked)', async () => {
  const c = await cliente();
  c.enviar({ type: 'login', correo: 'ana@correo.com', contrasena: 'Secreta123' });
  const { token } = await c.recibir('login_ok');
  c.enviar({ type: 'logout' });
  await c.recibir('logout_ok');
  assert.equal(await c.cerrado(), 1000);
  assert.equal(bd.sesiones.length, 0);
  assert.equal((await POST('/api/auth/logout', {}, token)).status, 401);
});

test('WS: red de seguridad - si borran la sesion fuera del proceso, la revalidacion expulsa', async () => {
  const { cuerpo: { token } } = await loginHttp();
  const c = await canalAutenticado(token);
  bd.sesiones.length = 0; // p. ej. otra instancia o SQL directo
  const m = await c.recibir('session_revoked', 2500);
  assert.equal(m.motivo, 'sesion_invalida');
  assert.equal(await c.cerrado(), 4001);
});

test('WS: red de seguridad - cuenta suspendida por fuera -> motivo suspendido', async () => {
  const { cuerpo: { token } } = await loginHttp();
  const c = await canalAutenticado(token);
  bd.usuarios.get(1).estado_cuenta = 'Suspendida indefinidamente';
  assert.equal((await c.recibir('session_revoked', 2500)).motivo, 'suspendido');
});

test('WS: al vencer el token el servidor avisa solo (motivo expirada)', async () => {
  const jti = 'jti-de-prueba-expiracion';
  const expira = new Date(Date.now() + 1500);
  bd.sesiones.push({ id_sesion: 99, id_usuario: 1, token: hashToken(jti), fecha_expiracion: expira });
  const c = await canalAutenticado(firmarJwt({ idUsuario: 1, jti }, expira));
  const m = await c.recibir('session_revoked', 4000);
  assert.equal(m.motivo, 'expirada');
});

// ------------------------------------------------------------------ robustez del protocolo
test('WS: JSON invalido -> cierre 4000', async () => {
  const c = await cliente();
  c.enviar('esto no es json');
  assert.equal(await c.cerrado(), 4000);
});

test('WS: mensaje sin type o frame binario -> cierre 4000', async () => {
  const c1 = await cliente();
  c1.enviar({ hola: 'mundo' });
  assert.equal(await c1.cerrado(), 4000);
  const c2 = await cliente();
  c2.ws.send(Buffer.from([1, 2, 3]));
  assert.equal(await c2.cerrado(), 4000);
});

test('WS: tipo desconocido -> error sin cerrar; ping -> pong', async () => {
  const c = await cliente();
  c.enviar({ type: 'hackear' });
  await c.recibir('error');
  c.enviar({ type: 'ping' });
  await c.recibir('pong');
  assert.equal(c.ws.readyState, WebSocket.OPEN);
  c.ws.close();
});

test('WS: logout sin sesion y doble login en la misma conexion -> error', async () => {
  const c = await cliente();
  c.enviar({ type: 'logout' });
  await c.recibir('error');
  c.enviar({ type: 'login', correo: 'ana@correo.com', contrasena: 'Secreta123' });
  await c.recibir('login_ok');
  c.enviar({ type: 'login', correo: 'ana@correo.com', contrasena: 'Secreta123' });
  await c.recibir('error');
  assert.equal(bd.sesiones.length, 1);
  c.ws.close();
});

test('WS: mensaje mayor al limite (4 KB) -> la conexion se corta', async () => {
  const c = await cliente();
  c.enviar(JSON.stringify({ type: 'login', correo: 'a'.repeat(6000), contrasena: 'x' }));
  assert.equal(await c.cerrado(), 1009);
});

test('WS: ruta distinta de /ws/auth -> 404', async () => {
  await assert.rejects(cliente({ url: urlWs.replace('/ws/auth', '/ws/otra') }), (e) => e.status === 404);
});

test('WS: Origin no permitido -> 403; permitido o sin Origin -> conecta', async () => {
  process.env.CORS_ORIGIN = 'https://tamalon.example';
  try {
    await assert.rejects(cliente({ ws: { headers: { Origin: 'https://malo.example' } } }), (e) => e.status === 403);
    const bueno = await cliente({ ws: { headers: { Origin: 'https://tamalon.example' } } });
    bueno.ws.close();
    const nativo = await cliente(); // la app Android no manda Origin
    nativo.ws.close();
  } finally {
    delete process.env.CORS_ORIGIN;
  }
});

test('WS: sin autenticar en 15 s se cierra (4408)', { skip: 'tarda 15 s; se verifica a mano o bajando la constante' }, () => {});

// ------------------------------------------------------------------ cliente web (html/sesion-ws.js) contra el servidor real
// Node 22 trae WebSocket nativo (el mismo API que un navegador); solo se simula location.
let TamalonSesion; let fetchReal; let llamadasHttpLogin = 0;
test.before(() => {
  globalThis.window = globalThis;
  // se evalua al usarse (el servidor de pruebas arranca en otro hook y el puerto aun no existe aqui)
  Object.defineProperty(globalThis, 'location', { configurable: true, get: () => ({ protocol: 'http:', host: new URL(base).host }) });
  fetchReal = globalThis.fetch;
  globalThis.fetch = (url, opts) => {
    if (String(url).includes('/api/auth/login')) llamadasHttpLogin++;
    return fetchReal(String(url).startsWith('/') ? `${base}${url}` : url, opts);
  };
  require('../html/sesion-ws.js');
  TamalonSesion = globalThis.TamalonSesion;
});
test.afterEach(() => { llamadasHttpLogin = 0; });

const datosLogin = (extra = {}) => ({ correo: 'ana@correo.com', contrasena: 'Secreta123', zona_horaria: 'America/Mexico_City', ...extra });

test('cliente web: login por WebSocket (sin tocar POST /api/auth/login)', async () => {
  const r = await TamalonSesion.login(datosLogin());
  assert.equal(r.token.split('.').length, 3);
  assert.equal(r.usuario.correo, 'ana@correo.com');
  assert.equal(llamadasHttpLogin, 0);
  assert.equal(bd.sesiones.length, 1);
});

test('cliente web: credenciales malas -> rechaza con message y status, sin caer a HTTP', async () => {
  await assert.rejects(TamalonSesion.login(datosLogin({ contrasena: 'mala' })),
    (e) => e.status === 401 && e.message === 'Correo o contraseña incorrectos');
  assert.equal(llamadasHttpLogin, 0);
  assert.equal(bd.sesiones.length, 0);
});

test('cliente web: sin WebSocket cae al login HTTP y funciona igual', async () => {
  const nativo = globalThis.WebSocket;
  globalThis.WebSocket = undefined;
  try {
    const r = await TamalonSesion.login(datosLogin());
    assert.equal(r.token.split('.').length, 3);
    assert.equal(llamadasHttpLogin, 1);
    await assert.rejects(TamalonSesion.login(datosLogin({ contrasena: 'mala' })), (e) => e.status === 401);
  } finally {
    globalThis.WebSocket = nativo;
  }
});

test('cliente web: escuchar() avisa en el acto cuando se revoca la sesion', async () => {
  const { token } = await TamalonSesion.login(datosLogin());
  const avisos = [];
  const canal = TamalonSesion.escuchar({ getToken: () => token, onRevocada: (motivo, mensaje) => avisos.push([motivo, mensaje]) });
  await esperar(250); // conecta y se autentica
  await sesiones.revocarUsuario(1, 'suspendido');
  await esperar(250);
  assert.deepEqual(avisos, [['suspendido', 'Usuario suspendido']]);
  canal.cerrar();
});

test('cliente web: escuchar() con un token ya revocado avisa al conectar (no se pierde la revocacion)', async () => {
  const { token } = await TamalonSesion.login(datosLogin());
  bd.sesiones.length = 0; // se revoco mientras la pagina estaba cerrada o sin red
  const avisos = [];
  const canal = TamalonSesion.escuchar({ getToken: () => token, onRevocada: (m) => avisos.push(m) });
  await esperar(400);
  assert.deepEqual(avisos, ['sesion_invalida']);
  canal.cerrar();
});

test('cliente web: escuchar() sin token en localStorage avisa de inmediato', async () => {
  const avisos = [];
  TamalonSesion.escuchar({ getToken: () => null, onRevocada: (m) => avisos.push(m) });
  await esperar(50);
  assert.deepEqual(avisos, ['sesion_invalida']);
});

test('cliente web: si se cae la conexion reconecta y la revocacion posterior igual llega', async () => {
  const { token } = await TamalonSesion.login(datosLogin());
  const avisos = [];
  const canal = TamalonSesion.escuchar({ getToken: () => token, onRevocada: (m) => avisos.push(m) });
  await esperar(250);
  require('../js/ws/hub').todas()[0].ws.terminate(); // corte de red
  await esperar(1900);                                // reintento tras ~1 s
  await sesiones.revocarUsuario(1, 'contrasena_cambiada');
  await esperar(300);
  assert.deepEqual(avisos, ['contrasena_cambiada']);
  canal.cerrar();
});

test('cliente web: tras cerrar() ya no se avisa (cierre de sesion local sin alerta doble)', async () => {
  const { token } = await TamalonSesion.login(datosLogin());
  const avisos = [];
  const canal = TamalonSesion.escuchar({ getToken: () => token, onRevocada: (m) => avisos.push(m) });
  await esperar(250);
  canal.cerrar();
  await sesiones.revocarUsuario(1, 'suspendido');
  await esperar(250);
  assert.deepEqual(avisos, []);
});
