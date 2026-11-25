
const MSG = require('../utils/mensajes');

// Codigos de cierre WebSocket (4000-4999 son de uso privado de la aplicacion)
const CIERRE = {
  PROTOCOLO: 4000,
  REVOCADA: 4001,
  NO_AUTORIZADO: 4401,
  TIEMPO_AGOTADO: 4408,
  DEMASIADOS_INTENTOS: 4429,
};

const MENSAJES_MOTIVO = {
  suspendido: MSG.USUARIO_SUSPENDIDO,                      // el panel web compara este texto exacto
  contrasena_cambiada: 'Tu contraseña fue cambiada. Inicia sesión de nuevo',
  logout: 'Sesión cerrada',
  expirada: 'Tu sesión expiró. Inicia sesión de nuevo',
  sesion_invalida: MSG.SESION_INVALIDA,
};

const porUsuario = new Map(); // id_usuario -> Set<conn>

function registrar(conn) {
  if (!porUsuario.has(conn.idUsuario)) porUsuario.set(conn.idUsuario, new Set());
  porUsuario.get(conn.idUsuario).add(conn);
}

function quitar(conn) {
  const set = porUsuario.get(conn.idUsuario);
  if (!set) return;
  set.delete(conn);
  if (set.size === 0) porUsuario.delete(conn.idUsuario);
}

function enviar(ws, objeto) {
  if (ws.readyState === 1) ws.send(JSON.stringify(objeto));
}

/** Avisa "session_revoked" y cierra la conexion. */
function expulsarConexion(conn, motivo) {
  quitar(conn);
  clearTimeout(conn.timerExpira);
  enviar(conn.ws, {
    type: 'session_revoked',
    motivo,
    mensaje: MENSAJES_MOTIVO[motivo] || MSG.SESION_INVALIDA,
  });
  conn.ws.close(CIERRE.REVOCADA, motivo);
}

/** Expulsa todas las conexiones de un usuario (todas sus pestanas y dispositivos). */
function expulsarUsuario(idUsuario, motivo) {
  for (const conn of [...(porUsuario.get(idUsuario) || [])]) expulsarConexion(conn, motivo);
}

/** Expulsa solo las conexiones de UNA sesion (identificada por su jti). */
function expulsarSesion(idUsuario, jti, motivo) {
  for (const conn of [...(porUsuario.get(idUsuario) || [])]) {
    if (conn.jti === jti) expulsarConexion(conn, motivo);
  }
}

const todas = () => [...porUsuario.values()].flatMap((set) => [...set]);
const totalConexiones = () => todas().length;

module.exports = {
  CIERRE, MENSAJES_MOTIVO, registrar, quitar, enviar,
  expulsarConexion, expulsarUsuario, expulsarSesion, todas, totalConexiones,
};
