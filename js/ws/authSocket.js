
const { WebSocketServer } = require('ws');
const MSG = require('../utils/mensajes');
const { ErrorHttp } = require('../utils/errores');
const sesiones = require('../services/sesionesService');
const { hashToken } = require('../utils/tokens');
const hub = require('./hub');

const RUTA = '/ws/auth';
const MAX_PAYLOAD = 4 * 1024;           // un mensaje de login pesa < 300 bytes
const MAX_INTENTOS_POR_CONEXION = 5;    // login + auth; el bloqueo por cuenta (RF2.3b) sigue aplicando
const TIEMPO_PARA_AUTENTICAR_MS = 15_000;
const LATIDO_MS = 30_000;
const MAX_TIMEOUT = 2 ** 31 - 1;        // limite de setTimeout

function origenPermitido(req) {
  const origen = req.headers.origin;
  if (!origen) return true; // las apps nativas (Android) no envian Origin
  const permitidos = (process.env.CORS_ORIGIN || '').split(',').map((o) => o.trim()).filter(Boolean);
  if (permitidos.length === 0) return true; // misma politica que el CORS de server.js
  if (permitidos.includes(origen)) return true;
  try { return new URL(origen).host === req.headers.host; } catch (_) { return false; }
}

function cerrar(conn, codigo, motivo) {
  clearTimeout(conn.timerAuth);
  conn.ws.close(codigo, motivo);
}

function vincular(conn, datos) {
  clearTimeout(conn.timerAuth);
  conn.autenticada = true;
  conn.idUsuario = datos.idUsuario;
  conn.idSesion = datos.idSesion;
  conn.jti = datos.jti;
  conn.hashJti = hashToken(datos.jti);
  conn.expiraMs = datos.expiraMs;
  hub.registrar(conn);
  // Cuando vence el token, se avisa y se cierra solo (sin esperar a que el cliente pregunte)
  const ms = Math.min(Math.max(datos.expiraMs - Date.now(), 0), MAX_TIMEOUT);
  conn.timerExpira = setTimeout(() => hub.expulsarConexion(conn, 'expirada'), ms);
  conn.timerExpira.unref();
}

const errorDe = (err) => (err instanceof ErrorHttp
  ? { status: err.status, mensaje: err.message }
  : { status: 500, mensaje: MSG.ERROR_INESPERADO });

async function procesar(conn, texto) {
  let msg;
  try { msg = JSON.parse(texto); } catch (_) { return cerrar(conn, hub.CIERRE.PROTOCOLO, 'JSON invalido'); }
  if (!msg || typeof msg !== 'object' || typeof msg.type !== 'string') {
    return cerrar(conn, hub.CIERRE.PROTOCOLO, 'Mensaje invalido');
  }
  const responder = (obj) => hub.enviar(conn.ws, obj);

  switch (msg.type) {
    case 'ping':
      return responder({ type: 'pong' });

    case 'login':
    case 'auth': {
      const esLogin = msg.type === 'login';
      if (conn.autenticada) return responder({ type: 'error', mensaje: 'La conexión ya está autenticada' });
      if (++conn.intentos > MAX_INTENTOS_POR_CONEXION) {
        responder({ type: esLogin ? 'login_error' : 'auth_error', status: 429, mensaje: MSG.BLOQUEO_TEMPORAL });
        return cerrar(conn, hub.CIERRE.DEMASIADOS_INTENTOS, 'Demasiados intentos');
      }
      try {
        if (esLogin) {
          const r = await sesiones.iniciarSesion(msg);
          if (conn.ws.readyState !== 1) return; // el cliente se fue mientras se validaba
          vincular(conn, r);
          return responder({ type: 'login_ok', token: r.token, expira: r.expira, usuario: r.usuario });
        }
        const r = await sesiones.validarToken(msg.token);
        if (conn.ws.readyState !== 1) return;
        vincular(conn, r);
        return responder({ type: 'auth_ok', expira: new Date(r.expiraMs).toISOString(), usuario: r.perfil });
      } catch (err) {
        if (!(err instanceof ErrorHttp)) console.error('[ws/auth] error inesperado:', err);
        responder({ type: esLogin ? 'login_error' : 'auth_error', ...errorDe(err) });
        // un login fallido deja reintentar en la misma conexion; un auth fallido no (el token ya no sirve)
        if (!esLogin) {
          // 401/403: el token ya no sirve. Error interno: cierre reintentable para que el cliente reconecte
          if (err instanceof ErrorHttp) cerrar(conn, hub.CIERRE.NO_AUTORIZADO, 'No autorizado');
          else cerrar(conn, 1011, 'Error interno');
        }
        return undefined;
      }
    }

    case 'logout': {
      if (!conn.autenticada) return responder({ type: 'error', mensaje: 'No hay sesión iniciada' });
      hub.quitar(conn); // esta conexion no necesita el aviso "session_revoked": ella misma pidio salir
      clearTimeout(conn.timerExpira);
      try {
        await sesiones.cerrarSesion({ idUsuario: conn.idUsuario, idSesion: conn.idSesion, jti: conn.jti });
      } catch (err) {
        console.error('[ws/auth] logout:', err.message);
      }
      responder({ type: 'logout_ok' });
      return cerrar(conn, 1000, 'logout');
    }

    default:
      return responder({ type: 'error', mensaje: 'Tipo de mensaje no soportado' });
  }
}

function conectar(ws) {
  const conn = {
    ws, autenticada: false, intentos: 0,
    idUsuario: null, idSesion: null, jti: null, hashJti: null, expiraMs: null,
    timerAuth: null, timerExpira: null, cola: Promise.resolve(),
  };

  conn.timerAuth = setTimeout(() => {
    hub.enviar(ws, { type: 'error', mensaje: 'Tiempo agotado: inicia sesión o autentica la conexión' });
    cerrar(conn, hub.CIERRE.TIEMPO_AGOTADO, 'Tiempo agotado');
  }, TIEMPO_PARA_AUTENTICAR_MS);
  conn.timerAuth.unref();

  ws.vivo = true;
  ws.on('pong', () => { ws.vivo = true; });

  ws.on('message', (data, esBinario) => {
    if (esBinario) return cerrar(conn, hub.CIERRE.PROTOCOLO, 'Solo texto');
    // un mensaje a la vez por conexion (evita dos logins simultaneos en el mismo socket)
    conn.cola = conn.cola
      .then(() => procesar(conn, data.toString('utf8')))
      .catch((err) => console.error('[ws/auth] error procesando mensaje:', err));
  });

  ws.on('close', () => {
    clearTimeout(conn.timerAuth);
    clearTimeout(conn.timerExpira);
    if (conn.autenticada) hub.quitar(conn);
  });

  ws.on('error', () => ws.terminate());
  return conn;
}

/** Engancha el canal al servidor HTTP de Express (comparten el mismo puerto). */
function iniciarWebSocket(httpServer) {
  const wss = new WebSocketServer({ noServer: true, maxPayload: MAX_PAYLOAD });

  httpServer.on('upgrade', (req, socket, head) => {
    let ruta;
    try { ruta = new URL(req.url, 'http://localhost').pathname; } catch (_) { ruta = ''; }
    if (ruta !== RUTA) {
      socket.write('HTTP/1.1 404 Not Found\r\nConnection: close\r\n\r\n');
      return socket.destroy();
    }
    if (!origenPermitido(req)) {
      socket.write('HTTP/1.1 403 Forbidden\r\nConnection: close\r\n\r\n');
      return socket.destroy();
    }
    return wss.handleUpgrade(req, socket, head, (ws) => conectar(ws));
  });

  // Latido: detecta conexiones muertas (cierre de tapa, cambio de red) y mantiene vivo el proxy de Render
  const latido = setInterval(() => {
    for (const ws of wss.clients) {
      if (!ws.vivo) { ws.terminate(); continue; }
      ws.vivo = false;
      ws.ping();
    }
  }, LATIDO_MS);
  latido.unref();

  const revalidar = setInterval(() => {
    sesiones.revalidarConexiones().catch((err) => console.error('[ws/auth] revalidacion:', err.message));
  }, Number(process.env.WS_REVALIDAR_MS || 60_000));
  revalidar.unref();

  return {
    wss,
    detener() {
      clearInterval(latido);
      clearInterval(revalidar);
      for (const ws of wss.clients) ws.terminate();
      wss.close();
    },
  };
}

module.exports = { iniciarWebSocket, RUTA };
