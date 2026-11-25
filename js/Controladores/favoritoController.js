const { pool, transaccion } = require('../config/db');
const C = require('../config/constantes');
const { ErrorHttp, asyncHandler } = require('../utils/errores');
const { entero } = require('../utils/validaciones');

function validarCliente(req) {
  if (req.usuario.tipo !== C.TIPOS.CLIENTE) {
    throw new ErrorHttp(403, 'Los favoritos están disponibles para cuentas de cliente');
  }
}

function leerIdVendedor(valor) {
  const id = entero(valor);
  if (id === null) throw new ErrorHttp(400, 'Identificador de vendedor no válido');
  return id;
}

const obtenerFavoritosCliente = asyncHandler(async (req, res) => {
  validarCliente(req);
  const [favoritos] = await pool.query(
    `SELECT f.id_favorito, u.id_usuario AS id_vendedor, u.nombre_completo, u.foto_perfil
       FROM favorito f
       JOIN vw_usuario u ON u.id_usuario = f.id_vendedor
      WHERE f.id_cliente = ?
      ORDER BY f.id_favorito DESC`,
    [req.usuario.id]
  );
  res.json({ favoritos });
});

const verificarEsFavorito = asyncHandler(async (req, res) => {
  validarCliente(req);
  const idVendedor = leerIdVendedor(req.params.vendedorId);
  const [[fila]] = await pool.query(
    'SELECT 1 AS es_favorito FROM favorito WHERE id_cliente = ? AND id_vendedor = ? LIMIT 1',
    [req.usuario.id, idVendedor]
  );
  res.json({ es_favorito: Boolean(fila) });
});

const agregarFavorito = asyncHandler(async (req, res) => {
  validarCliente(req);
  const idVendedor = leerIdVendedor(req.params.vendedorId);
  if (idVendedor === req.usuario.id) throw new ErrorHttp(400, 'No puedes agregarte como favorito');

  const resultado = await transaccion(async (conn) => {
    const [[cliente]] = await conn.query(
      'SELECT id_cliente FROM cliente WHERE id_cliente = ? FOR UPDATE',
      [req.usuario.id]
    );
    if (!cliente) throw new ErrorHttp(403, 'Los favoritos están disponibles para cuentas de cliente');

    const [[vendedor]] = await conn.query(
      'SELECT 1 AS existe FROM vendedor WHERE id_vendedor = ? LIMIT 1',
      [idVendedor]
    );
    if (!vendedor) {
      throw new ErrorHttp(404, 'No se encontró una cuenta de vendedor con ese identificador');
    }

    const [[{ total }]] = await conn.query(
      'SELECT COUNT(*) AS total FROM favorito WHERE id_cliente = ?',
      [req.usuario.id]
    );
    const [[existente]] = await conn.query(
      'SELECT id_favorito FROM favorito WHERE id_cliente = ? AND id_vendedor = ? LIMIT 1',
      [req.usuario.id, idVendedor]
    );
    if (existente) return { existente: true };
    if (total >= C.LIMITE_FAVORITOS) {
      throw new ErrorHttp(409, `El límite de favoritos es ${C.LIMITE_FAVORITOS}`);
    }

    const [insertado] = await conn.query(
      'INSERT INTO favorito (id_cliente, id_vendedor) VALUES (?, ?)',
      [req.usuario.id, idVendedor]
    );
    await conn.query(
      'INSERT INTO evento_vendedor (id_vendedor, id_tipo_evento, id_cliente) VALUES (?, 4, ?)',
      [idVendedor, req.usuario.id]
    );
    return { idFavorito: insertado.insertId };
  });

  res.status(resultado.existente ? 200 : 201).json({
    mensaje: resultado.existente ? 'El vendedor ya estaba en tus favoritos' : 'Vendedor agregado a favoritos',
    ...(resultado.idFavorito && { id_favorito: resultado.idFavorito }),
  });
});

const eliminarFavorito = asyncHandler(async (req, res) => {
  validarCliente(req);
  const idVendedor = leerIdVendedor(req.params.vendedorId);
  await transaccion(async (conn) => {
    const [resultado] = await conn.query(
      'DELETE FROM favorito WHERE id_cliente = ? AND id_vendedor = ?',
      [req.usuario.id, idVendedor]
    );
    if (!resultado.affectedRows) throw new ErrorHttp(404, 'El vendedor no está en tus favoritos');
    await conn.query(
      'INSERT INTO evento_vendedor (id_vendedor, id_tipo_evento, id_cliente) VALUES (?, 5, ?)',
      [idVendedor, req.usuario.id]
    );
  });
  res.json({ mensaje: 'Vendedor eliminado de favoritos' });
});

module.exports = { obtenerFavoritosCliente, verificarEsFavorito, agregarFavorito, eliminarFavorito };
