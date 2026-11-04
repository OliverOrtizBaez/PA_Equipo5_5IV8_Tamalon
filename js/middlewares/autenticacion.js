const { pool } = require('../config/db');
const { ESTADOS_CUENTA } = require('../config/constantes');
const { hashToken } = require('../utils/tokens');
const MSG = require('../utils/mensajes');
const { asyncHandler } = require('../utils/errores');

/**
 * Reactiva en el momento una suspension temporal ya vencida (RF22.2b; el job periodico es el respaldo).
 * Devuelve true si la cuenta quedo Activa.
 */
async function reactivarSiVencio(idUsuario) {
  const [r] = await pool.query(
    `UPDATE usuarios SET estado_cuenta = ?, fin_suspension = NULL
      WHERE id_usuario = ? AND estado_cuenta = ? AND fin_suspension IS NOT NULL AND fin_suspension <= ?`,
    [ESTADOS_CUENTA.ACTIVA, idUsuario, ESTADOS_CUENTA.SUSPENDIDA_TEMP, new Date()]
  );
  return r.affectedRows > 0;
}

/**
 * Valida el token Bearer contra la tabla sesion (RF2.1) y rechaza cuentas suspendidas (RF22.4).
 * Deja en req.usuario los datos de la cuenta con el rol ACTUAL de la BD.
 */
const verificarSesion = asyncHandler(async (req, res, next) => {
  const [esquema, token] = (req.headers.authorization || '').split(' ');
  if (esquema !== 'Bearer' || !token) {
    return res.status(401).json({ mensaje: MSG.SESION_INVALIDA });
  }

  const [filas] = await pool.query(
    `SELECT s.id_sesion, s.fecha_expiracion,
            u.id_usuario, u.nombre_completo, u.correo, u.rol, u.estado_cuenta, u.fin_suspension, u.zona_horaria
       FROM sesion s JOIN usuarios u ON u.id_usuario = s.id_usuario
      WHERE s.token = ? LIMIT 1`,
    [hashToken(token)]
  );
  const f = filas[0];
  if (!f) return res.status(401).json({ mensaje: MSG.SESION_INVALIDA });

  // RF2.1: vigente hasta el instante exacto de expiracion; a +1 s se rechaza
  if (Date.now() > new Date(f.fecha_expiracion).getTime()) {
    await pool.query('DELETE FROM sesion WHERE id_sesion = ?', [f.id_sesion]);
    return res.status(401).json({ mensaje: MSG.SESION_INVALIDA });
  }

  let estado = f.estado_cuenta;
  if (estado === ESTADOS_CUENTA.SUSPENDIDA_TEMP && (await reactivarSiVencio(f.id_usuario))) {
    estado = ESTADOS_CUENTA.ACTIVA;
  }

  // RF22.4: 403 y cierre de sesion, aunque el token se haya emitido antes de la suspension
  if (estado !== ESTADOS_CUENTA.ACTIVA) {
    await pool.query('DELETE FROM sesion WHERE id_usuario = ?', [f.id_usuario]);
    return res.status(403).json({ mensaje: MSG.USUARIO_SUSPENDIDO });
  }

  req.idSesion = f.id_sesion;
  req.usuario = {
    id: f.id_usuario,
    nombre: f.nombre_completo,
    correo: f.correo,
    rol: f.rol,
    estado,
    zonaHoraria: f.zona_horaria,
  };
  next();
});

/** Uso: verificarRol('Administrador')  /  verificarRol('Vendedor Gratuito', 'Vendedor Premium') */
const verificarRol = (...rolesPermitidos) => (req, res, next) => {
  if (!req.usuario || !rolesPermitidos.includes(req.usuario.rol)) {
    return res.status(403).json({ mensaje: MSG.SIN_PERMISO });
  }
  next();
};

module.exports = { verificarSesion, verificarRol, reactivarSiVencio };
