const { pool } = require('../config/db');
const { ErrorHttp, asyncHandler } = require('../utils/errores');
const { entero } = require('../utils/validaciones');

const listarTipos = asyncHandler(async (_req, res) => {
  const [tipos] = await pool.query(
    `SELECT id_tipo_vendedor, clave, nombre, icono
       FROM tipo_vendedor WHERE activo = 1 ORDER BY id_tipo_vendedor`
  );
  res.json({ tipos });
});

const listarCategorias = asyncHandler(async (req, res) => {
  const id = entero(req.params.id);
  if (id === null) throw new ErrorHttp(400, 'Identificador de tipo de vendedor no válido');
  const [categorias] = await pool.query(
    `SELECT cp.id_categoria, cp.nombre
       FROM categoria_producto cp
       JOIN tipo_vendedor tv ON tv.id_tipo_vendedor = cp.id_tipo_vendedor
      WHERE tv.id_tipo_vendedor = ? AND tv.activo = 1
      ORDER BY cp.id_categoria`,
    [id]
  );
  res.json({ categorias });
});

module.exports = { listarTipos, listarCategorias };
