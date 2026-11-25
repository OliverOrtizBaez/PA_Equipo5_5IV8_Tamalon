const { ESTADOS_CUENTA, ZONA_NEGOCIO, TIPOS } = require('../config/constantes');
const { transaccion } = require('../config/db');
const MSG = require('../utils/mensajes');
const { ErrorHttp, ErrorValidacion } = require('../utils/errores');
const { validar, validarContrasenaConConfirmacion, normalizarCorreo } = require('../utils/validaciones');
const { hashear } = require('../utils/contrasenas');

/**
 * Crea una cuenta (RF1.1, RF1.2, RF1.3). Valida todos los campos a la vez (RF25.1).
 * datos: { nombre, apellidos, correo, contrasena, confirmar_contrasena, id_tipo_vendedor }
 */
async function crearCuenta(datos, tipo) {
  const { nombre, apellidos, correo, contrasena, confirmar_contrasena, id_tipo_vendedor } = datos || {};

  const errores = [
    ...validar([
      { campo: 'correo', valor: correo },
      { campo: 'nombre', valor: nombre },
      { campo: 'apellidos', valor: apellidos },
    ]),
    ...validarContrasenaConConfirmacion(contrasena, confirmar_contrasena),
  ];
  if (tipo === TIPOS.VENDEDOR && (!Number.isSafeInteger(Number(id_tipo_vendedor)) || Number(id_tipo_vendedor) < 1)) {
    errores.push('Dato inválido en Tipo de vendedor: selecciona un tipo válido');
  }
  if (errores.length) throw new ErrorValidacion(errores);

  const hash = await hashear(contrasena);
  try {
    return await transaccion(async (conn) => {
      let tipoVendedor = null;
      if (tipo === TIPOS.VENDEDOR) {
        const [tipos] = await conn.query(
          'SELECT id_tipo_vendedor, nombre, icono FROM tipo_vendedor WHERE id_tipo_vendedor = ? AND activo = 1',
          [Number(id_tipo_vendedor)]
        );
        tipoVendedor = tipos[0];
        if (!tipoVendedor) throw new ErrorValidacion(['Dato inválido en Tipo de vendedor: selecciona un tipo válido']);
      }

      const [r] = await conn.query(
        `INSERT INTO usuario (id_rol, nombre, apellidos, contrasena, correo, zona_horaria)
         VALUES ((SELECT id_rol FROM rol WHERE nombre = ?), ?, ?, ?, ?, ?)`,
        [tipo, nombre.trim(), apellidos.trim(), hash, normalizarCorreo(correo), ZONA_NEGOCIO]
      );
      if (tipo === TIPOS.VENDEDOR) {
        await conn.query(
          'INSERT INTO vendedor (id_vendedor, id_tipo_vendedor) VALUES (?, ?)',
          [r.insertId, Number(id_tipo_vendedor)]
        );
      } else if (tipo === TIPOS.CLIENTE) {
        await conn.query('INSERT INTO cliente (id_cliente) VALUES (?)', [r.insertId]);
      }
      await conn.query('INSERT INTO control_acceso (id_usuario) VALUES (?)', [r.insertId]);
      return {
        id_usuario: r.insertId,
        nombre: nombre.trim(),
        apellidos: apellidos.trim(),
        rol: tipo,
        es_premium: false,
        foto_perfil: null,
        ...(tipoVendedor && {
          id_tipo_vendedor: Number(id_tipo_vendedor),
          tipo_vendedor: tipoVendedor.nombre,
          icono_vendedor: tipoVendedor.icono,
        }),
        correo: normalizarCorreo(correo),
      };
    });
  } catch (err) {
    // El indice UNIQUE es la verdad (sin condicion de carrera entre dos registros simultaneos)
    if (err.code === 'ER_DUP_ENTRY') throw new ErrorHttp(409, MSG.CORREO_REPETIDO);
    throw err;
  }
}

module.exports = { crearCuenta };
