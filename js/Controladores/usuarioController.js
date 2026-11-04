const { pool } = require('../config/db');
const { ErrorHttp, ErrorValidacion, asyncHandler } = require('../utils/errores');
const { validar } = require('../utils/validaciones');

const obtenerPerfil = asyncHandler(async (req, res) => {
  const [[usuario]] = await pool.query(
    `SELECT id_usuario, nombre_completo, correo, telefono, foto_perfil, rol, estado_cuenta,
            zona_horaria, fecha_creacion, fecha_corte, renovacion
       FROM usuarios
      WHERE id_usuario = ?
      LIMIT 1`,
    [req.usuario.id]
  );
  if (!usuario) throw new ErrorHttp(404, 'La cuenta no existe');
  res.json({ usuario });
});

const actualizarPerfil = asyncHandler(async (req, res) => {
  const { nombre_completo, telefono } = req.body || {};
  const errores = validar([{ campo: 'nombre', valor: nombre_completo }]);
  const telefonoNormalizado = typeof telefono === 'string' ? telefono.trim() : telefono;
  if (telefonoNormalizado !== null && telefonoNormalizado !== '' &&
      (typeof telefonoNormalizado !== 'string' || !/^\d{10}$/.test(telefonoNormalizado))) {
    errores.push('Dato inválido en Teléfono: debe contener 10 dígitos o estar vacío');
  }
  if (errores.length) throw new ErrorValidacion(errores);

  const [resultado] = await pool.query(
    'UPDATE usuarios SET nombre_completo = ?, telefono = ? WHERE id_usuario = ?',
    [nombre_completo.trim(), telefonoNormalizado || null, req.usuario.id]
  );
  if (!resultado.affectedRows) throw new ErrorHttp(404, 'La cuenta no existe');
  res.json({ mensaje: 'Perfil actualizado' });
});

const actualizarFotoPerfil = asyncHandler(async (req, res) => {
  const foto = req.body?.foto_perfil;
  if (foto !== null && foto !== '') {
    if (typeof foto !== 'string' || foto.length > 2048) {
      throw new ErrorValidacion(['Dato inválido en Foto de perfil: debe ser una URL HTTPS de hasta 2048 caracteres']);
    }
    let url;
    try { url = new URL(foto); } catch (_) {
      throw new ErrorValidacion(['Dato inválido en Foto de perfil: debe ser una URL HTTPS válida']);
    }
    if (url.protocol !== 'https:') {
      throw new ErrorValidacion(['Dato inválido en Foto de perfil: solo se permiten URL HTTPS']);
    }
  }

  const [resultado] = await pool.query(
    'UPDATE usuarios SET foto_perfil = ? WHERE id_usuario = ?',
    [foto || null, req.usuario.id]
  );
  if (!resultado.affectedRows) throw new ErrorHttp(404, 'La cuenta no existe');
  res.json({ mensaje: 'Foto de perfil actualizada' });
});

module.exports = { obtenerPerfil, actualizarPerfil, actualizarFotoPerfil };
