const { pool, transaccion } = require('../config/db');
const C = require('../config/constantes');
const MSG = require('../utils/mensajes');
const { ErrorHttp, ErrorValidacion, asyncHandler } = require('../utils/errores');
const { validar, validarContrasenaConConfirmacion, mensajeInvalido, normalizarCorreo } = require('../utils/validaciones');
const { sumarMinutos } = require('../utils/fechas');
const { generarToken, hashToken } = require('../utils/tokens');
const { comparar, hashear } = require('../utils/contrasenas');
const { enviarRecuperacion } = require('../utils/correo');
const { crearCuenta } = require('../services/cuentasService');
const sesiones = require('../services/sesionesService');

const registrar = asyncHandler(async (req, res) => {
  const { rol } = req.body || {};
  if (![C.TIPOS.VENDEDOR, C.TIPOS.CLIENTE].includes(rol)) {
    throw new ErrorValidacion(['Dato inválido en Rol: Vendedor o Cliente']);
  }
  const cuenta = await crearCuenta(req.body, rol);
  res.status(201).json({ mensaje: 'Cuenta creada', usuario: cuenta });
});

// El login real vive en sesionesService.iniciarSesion (lo comparte con el canal WebSocket /ws/auth).
// El campo "token" ahora es un JWT, pero se sigue enviando como Bearer: los clientes no cambian.
const login = asyncHandler(async (req, res) => {
  const { token, expira, usuario } = await sesiones.iniciarSesion(req.body || {});
  res.json({ token, expira, usuario });
});

const logout = asyncHandler(async (req, res) => {
  await sesiones.cerrarSesion({ idUsuario: req.usuario.id, idSesion: req.idSesion, jti: req.jti });
  res.json({ mensaje: 'Sesión cerrada' });
});

async function procesarRecuperacion(correo) {
  const [filas] = await pool.query('SELECT id_usuario FROM usuario WHERE correo = ? LIMIT 1', [correo]);
  if (!filas[0]) return;
  const idUsuario = filas[0].id_usuario;
  const ahora = new Date();

  const [[{ total }]] = await pool.query(
    'SELECT COUNT(*) AS total FROM recuperacion_pass WHERE id_usuario = ? AND fecha_emision > ?',
    [idUsuario, sumarMinutos(ahora, -C.RECUPERACION_VENTANA_MIN)]
  );
  if (total >= C.RECUPERACION_MAX_SOLICITUDES) return;

  const token = generarToken(32); 
  const [ins] = await pool.query(
    'INSERT INTO recuperacion_pass (id_usuario, token, fecha_emision, fecha_expiracion, valido) VALUES (?, ?, ?, ?, 1)',
    [idUsuario, hashToken(token), ahora, sumarMinutos(ahora, C.RECUPERACION_VIGENCIA_MIN)]
  );
  const base = (process.env.FRONTEND_URL || process.env.RENDER_EXTERNAL_URL || 'http://localhost:3000').replace(/\/$/, '');
  try {
    await enviarRecuperacion(correo, `${base}/html/restablecer.html?token=${token}`);
  } catch (err) {
    await pool.query('DELETE FROM recuperacion_pass WHERE id_recuperacion = ?', [ins.insertId]);
    throw err;
  }
}

const solicitarRecuperacion = asyncHandler(async (req, res) => {
  const { correo } = req.body || {};
  const errores = validar([{ campo: 'correo', valor: correo }]);
  if (errores.length) throw new ErrorValidacion(errores);

  res.json({ mensaje: MSG.RECUPERACION_ENVIADA });
  setImmediate(() => {
    procesarRecuperacion(normalizarCorreo(correo)).catch((err) =>
      console.error('[recuperación] error procesando solicitud:', err.message)
    );
  });
});

const restablecerContrasena = asyncHandler(async (req, res) => {
  const { token, contrasena, confirmar_contrasena } = req.body || {};
  const enlaceInvalido = () => new ErrorHttp(400, MSG.ENLACE_VENCIDO);

  if (typeof token !== 'string' || token.length < 32 || token.length > 200) throw enlaceInvalido();
  const hash = hashToken(token);

  const [filas] = await pool.query(
    `SELECT r.id_recuperacion, r.id_usuario, r.fecha_expiracion, r.valido, u.contrasena AS hash_actual
       FROM recuperacion_pass r JOIN usuario u ON u.id_usuario = r.id_usuario
      WHERE r.token = ? LIMIT 1`,
    [hash]
  );
  const r = filas[0];
  if (!r || !r.valido || Date.now() >= new Date(r.fecha_expiracion).getTime()) throw enlaceInvalido();

  const errores = validarContrasenaConConfirmacion(contrasena, confirmar_contrasena);
  if (errores.length === 0 && (await comparar(contrasena, r.hash_actual))) {
    errores.push(mensajeInvalido('Contraseña', 'debe ser distinta a la actual'));
  }
  if (errores.length) throw new ErrorValidacion(errores); 

  const nuevoHash = await hashear(contrasena);
  await transaccion(async (conn) => {
    const [[vigente]] = await conn.query(
      'SELECT valido FROM recuperacion_pass WHERE id_recuperacion = ? FOR UPDATE',
      [r.id_recuperacion]
    );
    if (!vigente || !vigente.valido) throw enlaceInvalido();

    await conn.query('UPDATE usuario SET contrasena = ? WHERE id_usuario = ?', [nuevoHash, r.id_usuario]);
    await conn.query('UPDATE recuperacion_pass SET valido = 0 WHERE id_usuario = ?', [r.id_usuario]);
    await conn.query('DELETE FROM sesion WHERE id_usuario = ?', [r.id_usuario]);
  });
  // La transaccion ya borro las sesiones; aqui se expulsa en vivo a quien las tenga abiertas
  sesiones.expulsarConexiones(r.id_usuario, 'contrasena_cambiada');

  res.json({ mensaje: 'Contraseña actualizada. Inicia sesión con tu nueva contraseña' });
});

module.exports = {
  registrar, login, logout, solicitarRecuperacion, restablecerContrasena,
};
