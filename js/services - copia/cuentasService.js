const { pool } = require('../config/db');
const { ESTADOS_CUENTA, ZONA_NEGOCIO, TIPOS, PLANES, componerRol } = require('../config/constantes');
const { transaccion } = require('../config/db');
const MSG = require('../utils/mensajes');
const { ErrorHttp, ErrorValidacion } = require('../utils/errores');
const { validar, validarContrasenaConConfirmacion, normalizarCorreo } = require('../utils/validaciones');
const { hashear } = require('../utils/contrasenas');

/**
 * Crea una cuenta (RF1.1, RF1.2, RF1.3). Valida todos los campos a la vez (RF25.1).
 * datos: { nombre_completo, correo, contrasena, confirmar_contrasena }
 */
async function crearCuenta(datos, tipo) {
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
    return await transaccion(async (conn) => {
      const [r] = await conn.query(
        `INSERT INTO usuarios (nombre_completo, contrasena, correo, tipo, estado_cuenta, zona_horaria)
         VALUES (?, ?, ?, ?, ?, ?)`,
        [nombre_completo.trim(), hash, normalizarCorreo(correo), tipo, ESTADOS_CUENTA.ACTIVA, ZONA_NEGOCIO]
      );
      if (tipo === TIPOS.VENDEDOR) {
        await conn.query('INSERT INTO vendedor (id_vendedor) VALUES (?)', [r.insertId]);
      } else if (tipo === TIPOS.CLIENTE) {
        await conn.query('INSERT INTO cliente (id_cliente) VALUES (?)', [r.insertId]);
      }
      await conn.query('INSERT INTO control_acceso (id_usuario) VALUES (?)', [r.insertId]);
      const plan = tipo === TIPOS.ADMIN ? null : PLANES.GRATUITO;
      return {
        id_usuario: r.insertId,
        tipo,
        plan,
        rol: componerRol(tipo, plan),
        correo: normalizarCorreo(correo),
        nombre_completo: nombre_completo.trim(),
      };
    });
  } catch (err) {
    // El indice UNIQUE es la verdad (sin condicion de carrera entre dos registros simultaneos)
    if (err.code === 'ER_DUP_ENTRY') throw new ErrorHttp(409, MSG.CORREO_REPETIDO);
    throw err;
  }
}

module.exports = { crearCuenta };
