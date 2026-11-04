const { pool, transaccion } = require('../config/db');
const C = require('../config/constantes');
const MSG = require('../utils/mensajes');
const { ErrorHttp, ErrorValidacion, asyncHandler } = require('../utils/errores');
const { validar, validarContrasenaConConfirmacion, mensajeInvalido, normalizarCorreo } = require('../utils/validaciones');
const { sumarMinutos, sumarDias, zonaValida } = require('../utils/fechas');
const { generarToken, hashToken } = require('../utils/tokens');
const { comparar, compararFalso, hashear } = require('../utils/contrasenas');
const { enviarRecuperacion } = require('../utils/correo');
const { crearCuenta } = require('../services/cuentasService');
const { reactivarSiVencio } = require('../middlewares/autenticacion');

const registrarVendedor = asyncHandler(async (req, res) => {
  const cuenta = await crearCuenta(req.body, C.ROLES.VENDEDOR_GRATUITO);
  res.status(201).json({ mensaje: 'Cuenta creada', usuario: cuenta });
});

const registrarCliente = asyncHandler(async (req, res) => {
  const cuenta = await crearCuenta(req.body, C.ROLES.CLIENTE_GRATUITO);
  res.status(201).json({ mensaje: 'Cuenta creada', usuario: cuenta });
});

const login = asyncHandler(async (req, res) => {
  const { correo, contrasena, zona_horaria } = req.body || {};
  const rechazar = () => res.status(401).json({ mensaje: MSG.CREDENCIALES_INCORRECTAS });

  if (typeof correo !== 'string' || typeof contrasena !== 'string' || correo.length > 100 || contrasena.length > 64) {
    return rechazar();
  }

  const [filas] = await pool.query(
    `SELECT id_usuario, nombre_completo, correo, telefono, foto_perfil, contrasena, rol, estado_cuenta,
            zona_horaria, intentos_fallidos, ultimo_intento
       FROM usuarios WHERE correo = ? LIMIT 1`,
    [normalizarCorreo(correo)]
  );
  const u = filas[0];
  if (!u) {
    await compararFalso(contrasena);
    return rechazar();
  }

  const ahora = new Date();

  if (u.intentos_fallidos >= C.MAX_INTENTOS_LOGIN && u.ultimo_intento) {
    const finBloqueo = sumarMinutos(new Date(u.ultimo_intento), C.BLOQUEO_MIN);
    if (ahora < finBloqueo) return res.status(429).json({ mensaje: MSG.BLOQUEO_TEMPORAL });
  }

  if (!(await comparar(contrasena, u.contrasena))) {
    const limiteVentana = sumarMinutos(ahora, -C.VENTANA_INTENTOS_MIN);
    await pool.query(
      `UPDATE usuarios SET
         intentos_fallidos = IF(inicio_intentos IS NULL OR inicio_intentos < ?, 1, intentos_fallidos + 1),
         inicio_intentos   = IF(inicio_intentos IS NULL OR inicio_intentos < ?, ?, inicio_intentos),
         ultimo_intento    = ?
       WHERE id_usuario = ?`,
      [limiteVentana, limiteVentana, ahora, ahora, u.id_usuario]
    );
    return rechazar();
  }

  let estado = u.estado_cuenta;
  if (estado === C.ESTADOS_CUENTA.SUSPENDIDA_TEMP && (await reactivarSiVencio(u.id_usuario))) {
    estado = C.ESTADOS_CUENTA.ACTIVA;
  }
  if (estado !== C.ESTADOS_CUENTA.ACTIVA) {
    return res.status(403).json({ mensaje: MSG.USUARIO_SUSPENDIDO }); 
  }

  const zona = zonaValida(zona_horaria) ? zona_horaria : u.zona_horaria;

  const token = generarToken();
  const expira = sumarDias(ahora, C.SESION_DIAS);
  await pool.query(
    'UPDATE usuarios SET intentos_fallidos = 0, inicio_intentos = NULL, ultimo_intento = NULL, zona_horaria = ? WHERE id_usuario = ?',
    [zona, u.id_usuario]
  );
  await pool.query(
    'INSERT INTO sesion (id_usuario, token, fech_hrs_login, fecha_expiracion) VALUES (?, ?, ?, ?)',
    [u.id_usuario, hashToken(token), ahora, expira]
  );

  try {
    await pool.query('INSERT INTO registro_login (id_usuario, rol, fech_hrs) VALUES (?, ?, ?)', [u.id_usuario, u.rol, ahora]);
  } catch (err) {
    console.error('[registro_login] no se pudo guardar el inicio de sesión:', err.message);
  }

  res.json({
    token,
    expira: expira.toISOString(),
    usuario: {
      id_usuario: u.id_usuario,
      nombre_completo: u.nombre_completo,
      correo: u.correo,
      telefono: u.telefono,
      foto_perfil: u.foto_perfil,
      rol: u.rol,
      estado_cuenta: estado,
      zona_horaria: zona,
    },
  });
});

const logout = asyncHandler(async (req, res) => {
  await pool.query('DELETE FROM sesion WHERE id_sesion = ?', [req.idSesion]);
  res.json({ mensaje: 'Sesión cerrada' });
});

async function procesarRecuperacion(correo) {
  const [filas] = await pool.query('SELECT id_usuario FROM usuarios WHERE correo = ? LIMIT 1', [correo]);
  if (!filas[0]) return;
  const idUsuario = filas[0].id_usuario;
  const ahora = new Date();

  const [[{ total }]] = await pool.query(
    'SELECT COUNT(*) AS total FROM recuperacion_pass WHERE id_usuario = ? AND fech_emision > ?',
    [idUsuario, sumarMinutos(ahora, -C.RECUPERACION_VENTANA_MIN)]
  );
  if (total >= C.RECUPERACION_MAX_SOLICITUDES) return;

  const token = generarToken(32); 
  const [ins] = await pool.query(
    'INSERT INTO recuperacion_pass (id_usuario, token, fech_emision, fecha_expiracion, valido) VALUES (?, ?, ?, ?, 1)',
    [idUsuario, hashToken(token), ahora, sumarMinutos(ahora, C.RECUPERACION_VIGENCIA_MIN)]
  );
  const base = (process.env.FRONTEND_URL || 'http://localhost:3000').replace(/\/$/, '');
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
       FROM recuperacion_pass r JOIN usuarios u ON u.id_usuario = r.id_usuario
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

    await conn.query('UPDATE usuarios SET contrasena = ? WHERE id_usuario = ?', [nuevoHash, r.id_usuario]);
    await conn.query('UPDATE recuperacion_pass SET valido = 0 WHERE id_usuario = ?', [r.id_usuario]);
    await conn.query('DELETE FROM sesion WHERE id_usuario = ?', [r.id_usuario]);                      
  });

  res.json({ mensaje: 'Contraseña actualizada. Inicia sesión con tu nueva contraseña' });
});

module.exports = {
  registrarVendedor, registrarCliente, login, logout, solicitarRecuperacion, restablecerContrasena,
};
