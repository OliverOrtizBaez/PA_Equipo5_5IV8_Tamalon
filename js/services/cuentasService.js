const { pool } = require('../config/db');
const { ESTADOS_CUENTA, ZONA_NEGOCIO } = require('../config/constantes');
const MSG = require('../utils/mensajes');
const { ErrorHttp, ErrorValidacion } = require('../utils/errores');
const { validar, validarContrasenaConConfirmacion, normalizarCorreo } = require('../utils/validaciones');
const { hashear } = require('../utils/contrasenas');

/**
 * Crea una cuenta (RF1.1, RF1.2, RF1.3). Valida todos los campos a la vez (RF25.1).
 * datos: { nombre_completo, correo, contrasena, confirmar_contrasena }
 */
async function crearCuenta(datos, rol) {
  const { nombre_completo, correo, contrasena, confirmar_contrasena } = datos || {};

  const errores = [
    ...validar([
      { campo: 'correo', valor: correo },
      { campo: 'nombre', valor: nombre_completo },
    ]),
    ...validarContrasenaConConfirmacion(contrasena, confirmar_contrasena),
  ];
  if (errores.length) throw new ErrorValidacion(errores);

  const hash = await hashear(contrasena);
  try {
    const [r] = await pool.query(
      `INSERT INTO usuarios (nombre_completo, contrasena, correo, rol, estado_cuenta, zona_horaria)
       VALUES (?, ?, ?, ?, ?, ?)`,
      [nombre_completo.trim(), hash, normalizarCorreo(correo), rol, ESTADOS_CUENTA.ACTIVA, ZONA_NEGOCIO]
    );
    return { id_usuario: r.insertId, rol, correo: normalizarCorreo(correo), nombre_completo: nombre_completo.trim() };
  } catch (err) {
    // El indice UNIQUE es la verdad (sin condicion de carrera entre dos registros simultaneos)
    if (err.code === 'ER_DUP_ENTRY') throw new ErrorHttp(409, MSG.CORREO_REPETIDO);
    throw err;
  }
}

module.exports = { crearCuenta };
