const { pool } = require('../config/db');
const { ErrorHttp, ErrorValidacion, asyncHandler } = require('../utils/errores');
const { entero } = require('../utils/validaciones');

const TIPOS_EVENTO = {
  vista_perfil: 1,
  vista_catalogo: 2,
  vista_producto: 3,
};

const registrarEvento = asyncHandler(async (req, res) => {
  const { id_vendedor: rawVendedor, tipo, id_producto: rawProducto } = req.body || {};
  const idVendedor = entero(rawVendedor);
  const idProducto = rawProducto === undefined ? null : entero(rawProducto);
  const idTipoEvento = TIPOS_EVENTO[tipo];
  if (idVendedor === null || !idTipoEvento ||
      (rawProducto !== undefined && idProducto === null) ||
      (tipo === 'vista_producto' && idProducto === null)) {
    throw new ErrorValidacion(['Datos de evento no válidos']);
  }
  if (req.usuario && req.usuario.tipo !== 'Cliente') {
    throw new ErrorHttp(403, 'Solo clientes o usuarios anónimos pueden registrar vistas');
  }

  const [[vendedor]] = await pool.query(
    'SELECT id_vendedor FROM vendedor WHERE id_vendedor = ?',
    [idVendedor]
  );
  if (!vendedor) throw new ErrorHttp(404, 'Vendedor no encontrado');
  if (idProducto !== null) {
    const [[producto]] = await pool.query(
      `SELECT p.id_producto FROM producto p JOIN catalogo c ON c.id_catalogo = p.id_catalogo
        WHERE p.id_producto = ? AND c.id_vendedor = ? AND p.visible = 1 AND c.visible = 1`,
      [idProducto, idVendedor]
    );
    if (!producto) throw new ErrorHttp(404, 'Producto no encontrado');
  }
  await pool.query(
    'INSERT INTO evento_vendedor (id_vendedor, id_tipo_evento, id_cliente, id_producto) VALUES (?, ?, ?, ?)',
    [idVendedor, idTipoEvento, req.usuario?.id ?? null, idProducto]
  );
  res.status(201).json({ registrado: true });
});

module.exports = { registrarEvento };
