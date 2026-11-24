// Cliente del canal de sesion /ws/auth del panel web (protocolo en js/ws/authSocket.js).
//   TamalonSesion.login({ correo, contrasena, zona_horaria })
//       Inicia sesion por WebSocket. Si el WebSocket no se puede abrir (o se corta antes de
//       responder) hace el mismo login por HTTP (POST /api/auth/login). Resuelve { token, expira, usuario }.
//   TamalonSesion.escuchar({ getToken, onRevocada })
//       Abre el canal con el JWT y avisa en el instante en que el servidor revoca la sesion
//       (suspension, contrasena cambiada, logout en otra pestana, expiracion). Reconecta solo si
//       se cae la red; al reconectar el servidor revalida el token, asi que no se pierde ninguna revocacion.
(function (global) {
  'use strict';

  const RUTA = '/ws/auth';
  // Cierres que NO se reintentan: protocolo invalido, sesion revocada, no autorizado, tiempo agotado, demasiados intentos
  const CIERRES_DEFINITIVOS = [4000, 4001, 4401, 4408, 4429];
  const MSG_ERROR = 'No se pudo completar la solicitud.';

  const urlWs = () => `${location.protocol === 'https:' ? 'wss:' : 'ws:'}//${location.host}${RUTA}`;
  const parsear = (texto) => { try { return JSON.parse(texto); } catch (_) { return null; } };

  function abrir(timeoutMs) {
    return new Promise((resolve, reject) => {
      if (typeof WebSocket === 'undefined') return reject(new Error('WebSocket no disponible'));
      let ws;
      try { ws = new WebSocket(urlWs()); } catch (err) { return reject(err); }
      const t = setTimeout(() => { try { ws.close(); } catch (_) { /* ya cerrado */ } reject(new Error('Tiempo agotado')); }, timeoutMs);
      ws.onopen = () => { clearTimeout(t); resolve(ws); };
      ws.onerror = () => { clearTimeout(t); reject(new Error('No se pudo conectar')); };
    });
  }

  async function loginHttp(datos) {
    const resp = await fetch('/api/auth/login', {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify(datos),
    });
    const resultado = await resp.json();
    if (!resp.ok) {
      const err = new Error(resultado.mensaje || MSG_ERROR);
      err.status = resp.status;
      throw err;
    }
    return resultado;
  }

  async function login(datos) {
    let ws;
    try { ws = await abrir(8000); } catch (_) { return loginHttp(datos); }

    return new Promise((resolve, reject) => {
      let terminado = false;
      const terminar = (accion, valor) => {
        if (terminado) return;
        terminado = true;
        clearTimeout(espera);
        try { ws.close(1000); } catch (_) { /* ya cerrado */ }
        accion(valor);
      };
      const alternativa = () => {
        if (terminado) return;
        terminado = true;
        clearTimeout(espera);
        loginHttp(datos).then(resolve, reject);
      };
      const espera = setTimeout(alternativa, 20000);

      ws.onmessage = (ev) => {
        const m = parsear(ev.data);
        if (!m) return;
        if (m.type === 'login_ok') {
          terminar(resolve, { token: m.token, expira: m.expira, usuario: m.usuario });
        } else if (m.type === 'login_error') {
          const err = new Error(m.mensaje || MSG_ERROR);
          err.status = m.status;
          terminar(reject, err);
        }
      };
      ws.onclose = alternativa; // se corto antes de responder: se intenta por HTTP
      ws.send(JSON.stringify({ type: 'login', ...datos }));
    });
  }

  function escuchar({ getToken, onRevocada }) {
    let ws = null;
    let cerrado = false;
    let intento = 0;
    let temporizador = null;

    const cerrarSocket = () => { if (ws) { try { ws.close(1000); } catch (_) { /* ya cerrado */ } } };

    const revocar = (motivo, mensaje) => {
      if (cerrado) return;
      cerrado = true;
      clearTimeout(temporizador);
      cerrarSocket();
      onRevocada(motivo, mensaje);
    };

    const reintentar = () => {
      if (cerrado) return;
      const espera = Math.min(30000, 1000 * 2 ** intento++) + Math.random() * 500;
      temporizador = setTimeout(conectar, espera);
    };

    async function conectar() {
      if (cerrado) return;
      const token = getToken();
      if (!token) { revocar('sesion_invalida', 'Sesión no válida. Inicia sesión de nuevo'); return; }
      try { ws = await abrir(10000); } catch (_) { reintentar(); return; }
      if (cerrado) { cerrarSocket(); return; }

      ws.onmessage = (ev) => {
        const m = parsear(ev.data);
        if (!m) return;
        if (m.type === 'auth_ok') intento = 0;
        else if (m.type === 'session_revoked') revocar(m.motivo, m.mensaje);
        else if (m.type === 'auth_error' && (m.status === 401 || m.status === 403)) {
          revocar(m.status === 403 ? 'suspendido' : 'sesion_invalida', m.mensaje);
        }
      };
      ws.onclose = (ev) => { if (!cerrado && !CIERRES_DEFINITIVOS.includes(ev.code)) reintentar(); };
      ws.send(JSON.stringify({ type: 'auth', token }));
    }

    conectar();
    return {
      cerrar() { cerrado = true; clearTimeout(temporizador); cerrarSocket(); },
    };
  }

  global.TamalonSesion = { login, escuchar };
})(window);
