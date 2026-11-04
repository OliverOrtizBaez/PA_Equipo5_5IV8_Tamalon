const { pool, transaccion } = require('../config/db');
const C = require('../config/constantes');
const { ErrorHttp, asyncHandler } = require('../utils/errores');
const { entero } = require('../utils/validaciones');

function validarClientePremium(req) {
  if (req.usuario.rol !== C.ROLES.CLIENTE_PREMIUM) {
    throw new ErrorHttp(403, 'Los favoritos están disponibles para cuentas de cliente Premium');
  }
}

function leerIdVendedor(valor) {
  const id = entero(valor);
  if (id === null) throw new ErrorHttp(400, 'Identificador de vendedor no válido');
  return id;
}

const obtenerFavoritosCliente = asyncHandler(async (req, res) => {
  validarClientePremium(req);
  const [favoritos] = await pool.query(
    `SELECT f.id_favorito, u.id_usuario AS id_vendedor, u.nombre_completo, u.foto_perfil
       FROM favorito f
       JOIN usuarios u ON u.id_usuario = f.id_vendedor
      WHERE f.id_cliente = ?
      ORDER BY f.id_favorito DESC`,
    [req.usuario.id]
  );
  res.json({ favoritos });
});

const verificarEsFavorito = asyncHandler(async (req, res) => {
  validarClientePremium(req);
  const idVendedor = leerIdVendedor(req.params.vendedorId);
  const [[fila]] = await pool.query(
    'SELECT 1 AS es_favorito FROM favorito WHERE id_cliente = ? AND id_vendedor = ? LIMIT 1',
    [req.usuario.id, idVendedor]
  );
  res.json({ es_favorito: Boolean(fila) });
});

const agregarFavorito = asyncHandler(async (req, res) => {
  validarClientePremium(req);
  const idVendedor = leerIdVendedor(req.params.vendedorId);
  if (idVendedor === req.usuario.id) throw new ErrorHttp(400, 'No puedes agregarte como favorito');

  const resultado = await transaccion(async (conn) => {
    const [[cliente]] = await conn.query(
      'SELECT rol FROM usuarios WHERE id_usuario = ? FOR UPDATE',
      [req.usuario.id]
    );
    if (!cliente || cliente.rol !== C.ROLES.CLIENTE_PREMIUM) {
      throw new ErrorHttp(403, 'Los favoritos están disponibles para cuentas de cliente Premium');
    }

    const [[vendedor]] = await conn.query(
      'SELECT rol FROM usuarios WHERE id_usuario = ? LIMIT 1',
      [idVendedor]
    );
    if (!vendedor || ![C.ROLES.VENDEDOR_GRATUITO, C.ROLES.VENDEDOR_PREMIUM].includes(vendedor.rol)) {
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
    if (total >= C.LIMITE_FAVORITOS_PREMIUM) {
      throw new ErrorHttp(409, `El límite de favoritos es ${C.LIMITE_FAVORITOS_PREMIUM}`);
    }

    const [insertado] = await conn.query(
      'INSERT INTO favorito (id_cliente, id_vendedor) VALUES (?, ?)',
      [req.usuario.id, idVendedor]
    );
    return { idFavorito: insertado.insertId };
  });

  res.status(resultado.existente ? 200 : 201).json({
    mensaje: resultado.existente ? 'El vendedor ya estaba en tus favoritos' : 'Vendedor agregado a favoritos',
    ...(resultado.idFavorito && { id_favorito: resultado.idFavorito }),
  });
});

const eliminarFavorito = asyncHandler(async (req, res) => {
  validarClientePremium(req);
  const idVendedor = leerIdVendedor(req.params.vendedorId);
  const [resultado] = await pool.query(
    'DELETE FROM favorito WHERE id_cliente = ? AND id_vendedor = ?',
    [req.usuario.id, idVendedor]
  );
  if (!resultado.affectedRows) throw new ErrorHttp(404, 'El vendedor no está en tus favoritos');
  res.json({ mensaje: 'Vendedor eliminado de favoritos' });
});

module.exports = { obtenerFavoritosCliente, verificarEsFavorito, agregarFavorito, eliminarFavorito };
