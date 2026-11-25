// Sesiones con JWT. Un unico lugar para: iniciar sesion, validar token, cerrar y revocar.
// Lo usan el login HTTP (POST /api/auth/login), el login por WebSocket (/ws/auth) y el
// middleware verificarSesion, asi las tres rutas aplican exactamente las mismas reglas.
//
// Modelo: el JWT va firmado (HS256) y lleva un "jti" aleatorio. En la tabla `sesion` se guarda
// el SHA-256 del jti (columna `token`). Validar = firma correcta + fila viva en la BD.
// Por eso una sesion se puede revocar al instante (borrando la fila) aunque el JWT no haya vencido.
const { pool } = require('../config/db');
const C = require('../config/constantes');
const MSG = require('../utils/mensajes');
const { ErrorHttp } = require('../utils/errores');
const { normalizarCorreo } = require('../utils/validaciones');
const { sumarMinutos, sumarDias, zonaValida } = require('../utils/fechas');
const { generarToken, hashToken } = require('../utils/tokens');
const { comparar, compararFalso } = require('../utils/contrasenas');
const { firmarJwt, verificarJwt, ErrorJwt } = require('../utils/jwt');
const hub = require('../ws/hub');

const sesionInvalida = () => new ErrorHttp(401, MSG.SESION_INVALIDA);

/**
 * Reactiva en el momento una suspension temporal ya vencida (RF22.2b; el job periodico es el respaldo).
 * Devuelve true si la cuenta quedo Activa.
 */
async function reactivarSiVencio(idUsuario) {
  const [r] = await pool.query(
    `UPDATE usuario u
       JOIN suspension s ON s.id_usuario = u.id_usuario
       JOIN estado_cuenta ec ON ec.nombre = ?
        SET u.id_estado_cuenta = ec.id_estado_cuenta
      WHERE u.id_usuario = ? AND s.fin <= ?`,
    [C.ESTADOS_CUENTA.ACTIVA, idUsuario, new Date()]
  );
  if (r.affectedRows) await pool.query('DELETE FROM suspension WHERE id_usuario = ?', [idUsuario]);
  return r.affectedRows > 0;
}

/**
 * Valida credenciales y crea la sesion (RF2.1, RF2.3, RF2.3b).
 * Devuelve { idUsuario, idSesion, jti, expiraMs, token, expira, usuario } o lanza ErrorHttp (401 / 403 / 429).
 */
async function iniciarSesion({ correo, contrasena, zona_horaria } = {}) {
  const rechazar = () => new ErrorHttp(401, MSG.CREDENCIALES_INCORRECTAS);

  if (typeof correo !== 'string' || typeof contrasena !== 'string' || correo.length > 100 || contrasena.length > 64) {
    throw rechazar();
  }

  const [filas] = await pool.query(
    `SELECT u.id_usuario, u.nombre, u.apellidos, u.correo, u.telefono, u.foto_perfil, u.contrasena,
            u.rol, u.es_premium, u.estado_cuenta, u.zona_horaria, u.id_tipo_vendedor,
            u.tipo_vendedor, u.icono_vendedor, u.premium_desde,
            COALESCE(ca.intentos_fallidos, 0) AS intentos_fallidos, ca.ultimo_intento
       FROM vw_usuario u
       LEFT JOIN control_acceso ca ON ca.id_usuario = u.id_usuario
      WHERE u.correo = ? LIMIT 1`,
    [normalizarCorreo(correo)]
  );
  const u = filas[0];
  if (!u) {
    await compararFalso(contrasena); // mismo tiempo de respuesta que una contrasena incorrecta
    throw rechazar();
  }

  const ahora = new Date();

  if (u.intentos_fallidos >= C.MAX_INTENTOS_LOGIN && u.ultimo_intento) {
    const finBloqueo = sumarMinutos(new Date(u.ultimo_intento), C.BLOQUEO_MIN);
    if (ahora < finBloqueo) throw new ErrorHttp(429, MSG.BLOQUEO_TEMPORAL);
  }

  if (!(await comparar(contrasena, u.contrasena))) {
    const limiteVentana = sumarMinutos(ahora, -C.VENTANA_INTENTOS_MIN);
    await pool.query(
      `INSERT INTO control_acceso (id_usuario, intentos_fallidos, inicio_intentos, ultimo_intento)
       VALUES (?, 1, ?, ?)
       ON DUPLICATE KEY UPDATE
         intentos_fallidos = IF(inicio_intentos IS NULL OR inicio_intentos < ?, 1, intentos_fallidos + 1),
         inicio_intentos   = IF(inicio_intentos IS NULL OR inicio_intentos < ?, VALUES(inicio_intentos), inicio_intentos),
         ultimo_intento    = VALUES(ultimo_intento)`,
      [u.id_usuario, ahora, ahora, limiteVentana, limiteVentana]
    );
    throw rechazar();
  }

  let estado = u.estado_cuenta;
  if (estado === C.ESTADOS_CUENTA.SUSPENDIDA_TEMP && (await reactivarSiVencio(u.id_usuario))) {
    estado = C.ESTADOS_CUENTA.ACTIVA;
  }
  if (estado !== C.ESTADOS_CUENTA.ACTIVA) throw new ErrorHttp(403, MSG.USUARIO_SUSPENDIDO);

  const zona = zonaValida(zona_horaria) ? zona_horaria : u.zona_horaria;

  const jti = generarToken(32);
  const expira = sumarDias(ahora, C.SESION_DIAS);
  await pool.query(
    'UPDATE control_acceso SET intentos_fallidos = 0, inicio_intentos = NULL, ultimo_intento = NULL WHERE id_usuario = ?',
    [u.id_usuario]
  );
  await pool.query('UPDATE usuario SET zona_horaria = ? WHERE id_usuario = ?', [zona, u.id_usuario]);
  const [ins] = await pool.query(
    'INSERT INTO sesion (id_usuario, token, fecha_login, fecha_expiracion) VALUES (?, ?, ?, ?)',
    [u.id_usuario, hashToken(jti), ahora, expira]
  );

  try {
    await pool.query('INSERT INTO registro_login (id_usuario, fecha) VALUES (?, ?)', [u.id_usuario, ahora]);
  } catch (err) {
    console.error('[registro_login] no se pudo guardar el inicio de sesión:', err.message);
  }

  return {
    idUsuario: u.id_usuario,
    idSesion: ins.insertId,
    jti,
    expiraMs: expira.getTime(),
    token: firmarJwt({ idUsuario: u.id_usuario, jti }, expira),
    expira: expira.toISOString(),
    usuario: {
      id_usuario: u.id_usuario,
      nombre: u.nombre,
      apellidos: u.apellidos,
      correo: u.correo,
      telefono: u.telefono,
      foto_perfil: u.foto_perfil,
      rol: u.rol,
      es_premium: Boolean(u.es_premium),
      ...(u.id_tipo_vendedor && {
        id_tipo_vendedor: u.id_tipo_vendedor,
        tipo_vendedor: u.tipo_vendedor,
        icono_vendedor: u.icono_vendedor,
      }),
      ...(u.premium_desde && { premium_desde: u.premium_desde }),
      estado_cuenta: estado,
      zona_horaria: zona,
    },
  };
}

/**
 * Valida un JWT contra la BD (RF2.1 / RF22.4). Lanza ErrorHttp 401 (sesion invalida o vencida)
 * o 403 (cuenta suspendida). Devuelve los datos con el rol ACTUAL de la BD.
 */
async function validarToken(token) {
  let claims;
  try {
    claims = verificarJwt(token);
  } catch (err) {
    if (err instanceof ErrorJwt) throw sesionInvalida();
    throw err;
  }

  const [filas] = await pool.query(
    `SELECT s.id_sesion, s.fecha_expiracion,
            u.id_usuario, u.nombre, u.apellidos, u.correo, u.telefono, u.foto_perfil, u.rol,
            u.es_premium, u.estado_cuenta, u.fin_suspension, u.zona_horaria, u.id_tipo_vendedor,
            u.tipo_vendedor, u.icono_vendedor, u.premium_desde
       FROM sesion s JOIN vw_usuario u ON u.id_usuario = s.id_usuario
      WHERE s.token = ? AND s.id_usuario = ? LIMIT 1`,
    [hashToken(claims.jti), claims.idUsuario]
  );
  const f = filas[0];
  if (!f) throw sesionInvalida();

  // RF2.1: vigente hasta el instante exacto de expiracion; a +1 s se rechaza
  const expiraMs = new Date(f.fecha_expiracion).getTime();
  if (Date.now() > expiraMs) {
    await pool.query('DELETE FROM sesion WHERE id_sesion = ?', [f.id_sesion]);
    throw sesionInvalida();
  }

  let estado = f.estado_cuenta;
  if (estado === C.ESTADOS_CUENTA.SUSPENDIDA_TEMP && (await reactivarSiVencio(f.id_usuario))) {
    estado = C.ESTADOS_CUENTA.ACTIVA;
  }

  // RF22.4: 403 y cierre de sesion, aunque el token se haya emitido antes de la suspension
  if (estado !== C.ESTADOS_CUENTA.ACTIVA) {
    await revocarUsuario(f.id_usuario, 'suspendido');
    throw new ErrorHttp(403, MSG.USUARIO_SUSPENDIDO);
  }

  return {
    idSesion: f.id_sesion,
    idUsuario: f.id_usuario,
    jti: claims.jti,
    expiraMs,
    usuario: {
      id: f.id_usuario,
      correo: f.correo,
      tipo: f.rol,
      rol: f.rol,
      esPremium: Boolean(f.es_premium),
      estado,
      zonaHoraria: f.zona_horaria,
    },
    // misma forma que el "usuario" que devuelve el login
    perfil: {
      id_usuario: f.id_usuario,
      nombre: f.nombre,
      apellidos: f.apellidos,
      correo: f.correo,
      telefono: f.telefono,
      foto_perfil: f.foto_perfil,
      rol: f.rol,
      es_premium: Boolean(f.es_premium),
      ...(f.id_tipo_vendedor && {
        id_tipo_vendedor: f.id_tipo_vendedor,
        tipo_vendedor: f.tipo_vendedor,
        icono_vendedor: f.icono_vendedor,
      }),
      ...(f.premium_desde && { premium_desde: f.premium_desde }),
      estado_cuenta: estado,
      zona_horaria: f.zona_horaria,
    },
  };
}

/** Cierra UNA sesion (logout). Avisa a las pestanas/dispositivos que compartan ese mismo token. */
async function cerrarSesion({ idUsuario, idSesion, jti }) {
  await pool.query('DELETE FROM sesion WHERE id_sesion = ?', [idSesion]);
  hub.expulsarSesion(idUsuario, jti, 'logout');
}

/** Revoca TODAS las sesiones de un usuario y expulsa sus conexiones en tiempo real. */
async function revocarUsuario(idUsuario, motivo) {
  await pool.query('DELETE FROM sesion WHERE id_usuario = ?', [idUsuario]);
  hub.expulsarUsuario(idUsuario, motivo);
}

/** Solo expulsa conexiones (para cuando la BD ya borro las sesiones dentro de una transaccion). */
const expulsarConexiones = (idUsuario, motivo) => hub.expulsarUsuario(idUsuario, motivo);

/**
 * Red de seguridad: una consulta por lote revisa que todas las conexiones abiertas sigan teniendo
 * sesion viva y cuenta activa. Cubre cambios hechos fuera de este proceso (otra instancia, SQL directo).
 */
async function revalidarConexiones() {
  const conns = hub.todas().filter((c) => c.hashJti);
  if (conns.length === 0) return;

  const [filas] = await pool.query(
    `SELECT s.token, s.fecha_expiracion, u.estado_cuenta
       FROM sesion s JOIN vw_usuario u ON u.id_usuario = s.id_usuario
      WHERE s.token IN (?)`,
    [[...new Set(conns.map((c) => c.hashJti))]]
  );
  const vivas = new Map(filas.map((f) => [f.token, f]));

  for (const conn of conns) {
    const f = vivas.get(conn.hashJti);
    if (!f) hub.expulsarConexion(conn, 'sesion_invalida');
    else if (f.estado_cuenta !== C.ESTADOS_CUENTA.ACTIVA) hub.expulsarConexion(conn, 'suspendido');
    else if (Date.now() > new Date(f.fecha_expiracion).getTime()) hub.expulsarConexion(conn, 'expirada');
  }
}

module.exports = {
  iniciarSesion, validarToken, cerrarSesion, revocarUsuario, expulsarConexiones,
  revalidarConexiones, reactivarSiVencio,
};
