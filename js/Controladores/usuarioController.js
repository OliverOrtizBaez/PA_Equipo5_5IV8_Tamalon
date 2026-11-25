const { pool } = require('../config/db');
const { transaccion } = require('../config/db');
const C = require('../config/constantes');
const { ErrorHttp, ErrorValidacion, asyncHandler } = require('../utils/errores');
const { validar, esUrlCloudinary } = require('../utils/validaciones');

async function cargarPerfil(idUsuario) {
  const [[usuario]] = await pool.query(
    `SELECT id_usuario, nombre, apellidos, correo, telefono, foto_perfil, rol, es_premium,
            id_tipo_vendedor, tipo_vendedor, icono_vendedor, premium_desde, estado_cuenta,
            zona_horaria, fecha_creacion
       FROM vw_usuario
      WHERE id_usuario = ?
      LIMIT 1`,
    [idUsuario]
  );
  if (!usuario) throw new ErrorHttp(404, 'La cuenta no existe');
  usuario.es_premium = Boolean(usuario.es_premium);
  return usuario;
}

const obtenerPerfil = asyncHandler(async (req, res) => {
  const usuario = await cargarPerfil(req.usuario.id);
  res.json({ usuario });
});

const obtenerMe = asyncHandler(async (req, res) => {
  res.json(await cargarPerfil(req.usuario.id));
});

const actualizarPerfil = asyncHandler(async (req, res) => {
  const { nombre, apellidos, telefono, id_tipo_vendedor: idTipo } = req.body || {};
  const errores = validar([
    { campo: 'nombre', valor: nombre },
    { campo: 'apellidos', valor: apellidos },
  ]);
  if (idTipo !== undefined && (!Number.isSafeInteger(Number(idTipo)) || Number(idTipo) < 1)) {
    errores.push('Dato inválido en Tipo de vendedor: selecciona un tipo válido');
  }
  const telefonoNormalizado = typeof telefono === 'string' ? telefono.trim() : telefono;
  if (telefonoNormalizado !== null && telefonoNormalizado !== '' &&
      (typeof telefonoNormalizado !== 'string' || !/^\d{10}$/.test(telefonoNormalizado))) {
    errores.push('Dato inválido en Teléfono: debe contener 10 dígitos o estar vacío');
  }
  if (errores.length) throw new ErrorValidacion(errores);

  await transaccion(async (conn) => {
    if (idTipo !== undefined) {
      if (req.usuario.tipo !== C.TIPOS.VENDEDOR) {
        throw new ErrorHttp(400, 'Solo un vendedor puede cambiar su tipo de vendedor');
      }
      const [tipos] = await conn.query(
        'SELECT id_tipo_vendedor FROM tipo_vendedor WHERE id_tipo_vendedor = ? AND activo = 1',
        [Number(idTipo)]
      );
      if (!tipos[0]) throw new ErrorValidacion(['Dato inválido en Tipo de vendedor: selecciona un tipo válido']);
      await conn.query('UPDATE vendedor SET id_tipo_vendedor = ? WHERE id_vendedor = ?', [Number(idTipo), req.usuario.id]);
    }
    const [resultado] = await conn.query(
      'UPDATE usuario SET nombre = ?, apellidos = ?, telefono = ? WHERE id_usuario = ?',
      [nombre.trim(), apellidos.trim(), telefonoNormalizado || null, req.usuario.id]
    );
    if (!resultado.affectedRows) throw new ErrorHttp(404, 'La cuenta no existe');
  });
  res.json({ mensaje: 'Perfil actualizado' });
});

const actualizarFotoPerfil = asyncHandler(async (req, res) => {
  const foto = req.body?.foto_perfil;
  if (foto !== null && foto !== '') {
    if (!esUrlCloudinary(foto)) {
      throw new ErrorValidacion(['Dato inválido en Foto de perfil: debe ser una URL HTTPS de Cloudinary de hasta 2048 caracteres']);
    }
  }

  const [resultado] = await pool.query(
    'UPDATE usuario SET foto_perfil = ? WHERE id_usuario = ?',
    [foto || null, req.usuario.id]
  );
  if (!resultado.affectedRows) throw new ErrorHttp(404, 'La cuenta no existe');
  res.json({ mensaje: 'Foto de perfil actualizada' });
});

module.exports = { obtenerPerfil, obtenerMe, actualizarPerfil, actualizarFotoPerfil };
