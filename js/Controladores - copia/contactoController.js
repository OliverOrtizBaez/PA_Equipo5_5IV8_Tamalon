const { pool } = require('../config/db');
const { ErrorValidacion, asyncHandler } = require('../utils/errores');
const { validar } = require('../utils/validaciones');

const crearMensaje = asyncHandler(async (req, res) => {
  const { nombre, correo, mensaje } = req.body || {};
  const errores = [
    ...validar([
      { campo: 'nombre', valor: nombre },
      { campo: 'correo', valor: correo },
    ]),
  ];

  if (typeof mensaje !== 'string' || mensaje.trim().length < 5 || mensaje.trim().length > 1000) {
    errores.push('Dato inválido en Mensaje: debe contener de 5 a 1000 caracteres');
  }
  if (errores.length) throw new ErrorValidacion(errores);

  const [resultado] = await pool.query(
    'INSERT INTO mensaje_contacto (nombre, correo, mensaje) VALUES (?, ?, ?)',
    [nombre.trim(), correo.trim().toLowerCase(), mensaje.trim()]
  );
  res.status(201).json({ mensaje: 'Mensaje enviado correctamente', id_mensaje: resultado.insertId });
});

module.exports = { crearMensaje };
