// JWT (HS256) sin dependencias: solo el modulo crypto de Node.
// Claims: iss, sub (id_usuario), jti (id aleatorio de la sesion), iat, exp.
// IMPORTANTE: los claims NO se usan para decidir permisos; el rol y el estado de la cuenta
// se leen de la BD en cada validacion (ver services/sesionesService.js).
const crypto = require('crypto');

const EMISOR = 'tamalon';
const MAX_LARGO = 1024;          // un JWT normal mide ~250 caracteres
const CABECERA = Buffer.from(JSON.stringify({ alg: 'HS256', typ: 'JWT' })).toString('base64url');

class ErrorJwt extends Error {
  constructor(mensaje) { super(mensaje); this.name = 'ErrorJwt'; }
}

function secreto() {
  const s = process.env.JWT_SECRET;
  if (!s || s.length < 32) {
    throw new Error(
      'Falta JWT_SECRET (minimo 32 caracteres). Generalo con: ' +
      'node -e "console.log(require(\'crypto\').randomBytes(48).toString(\'hex\'))"'
    );
  }
  return s;
}

// Se llama al arrancar para fallar rapido si falta el secreto (en vez de fallar en el primer login).
const asegurarSecreto = () => { secreto(); };

const firma = (data) => crypto.createHmac('sha256', secreto()).update(data).digest('base64url');

/** Firma un JWT. expira es un Date; el claim exp va en segundos (RFC 7519). */
function firmarJwt({ idUsuario, jti }, expira) {
  const payload = {
    iss: EMISOR,
    sub: String(idUsuario),
    jti,
    iat: Math.floor(Date.now() / 1000),
    exp: Math.floor(expira.getTime() / 1000),
  };
  const cuerpo = Buffer.from(JSON.stringify(payload)).toString('base64url');
  const datos = `${CABECERA}.${cuerpo}`;
  return `${datos}.${firma(datos)}`;
}

/** Verifica firma, algoritmo, emisor y expiracion. Devuelve { idUsuario, jti, expiraMs } o lanza ErrorJwt. */
function verificarJwt(token) {
  if (typeof token !== 'string' || token.length > MAX_LARGO) throw new ErrorJwt('Token invalido');
  const partes = token.split('.');
  if (partes.length !== 3) throw new ErrorJwt('Token invalido');
  const [cabeceraB64, cuerpoB64, firmaRecibida] = partes;

  let cabecera;
  try { cabecera = JSON.parse(Buffer.from(cabeceraB64, 'base64url').toString('utf8')); }
  catch (_) { throw new ErrorJwt('Token invalido'); }
  // Solo HS256: rechaza "none" y cualquier otro algoritmo (ataque de confusion de algoritmo)
  if (!cabecera || cabecera.alg !== 'HS256') throw new ErrorJwt('Algoritmo no permitido');

  const esperada = Buffer.from(firma(`${cabeceraB64}.${cuerpoB64}`));
  const recibida = Buffer.from(firmaRecibida);
  if (esperada.length !== recibida.length || !crypto.timingSafeEqual(esperada, recibida)) {
    throw new ErrorJwt('Firma invalida');
  }

  let p;
  try { p = JSON.parse(Buffer.from(cuerpoB64, 'base64url').toString('utf8')); }
  catch (_) { throw new ErrorJwt('Token invalido'); }

  const idUsuario = Number(p.sub);
  if (p.iss !== EMISOR || !Number.isInteger(idUsuario) || idUsuario <= 0 ||
      typeof p.jti !== 'string' || !p.jti || !Number.isFinite(p.exp)) {
    throw new ErrorJwt('Token invalido');
  }
  if (Date.now() >= p.exp * 1000) throw new ErrorJwt('Token expirado');

  return { idUsuario, jti: p.jti, expiraMs: p.exp * 1000 };
}

module.exports = { firmarJwt, verificarJwt, asegurarSecreto, ErrorJwt };
