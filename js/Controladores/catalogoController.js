const { pool, transaccion } = require('../config/db');
const C = require('../config/constantes');
const { ErrorHttp, ErrorValidacion, asyncHandler } = require('../utils/errores');
const { entero } = require('../utils/validaciones');

function idValido(valor, etiqueta = 'Identificador') {
  const id = entero(valor);
  if (id === null) throw new ErrorHttp(400, `${etiqueta} no válido`);
  return id;
}

function exigirVendedor(req) {
  if (req.usuario.tipo !== C.TIPOS.VENDEDOR) {
    throw new ErrorHttp(403, 'Solo las cuentas de vendedor pueden administrar catálogos');
  }
}

function validarNombre(nombre) {
  if (typeof nombre !== 'string' || nombre.trim().length < 1 || nombre.trim().length > 50) {
    throw new ErrorValidacion(['Dato inválido en Nombre: debe contener de 1 a 50 caracteres']);
  }
  return nombre.trim();
}

async function buscarCatalogoPropio(conn, id, idVendedor, lock = false) {
  const [filas] = await conn.query(
    `SELECT c.id_catalogo, c.id_vendedor, c.nombre, c.visible
       FROM catalogo c
      WHERE c.id_catalogo = ? AND c.id_vendedor = ?${lock ? ' FOR UPDATE' : ''}`,
    [id, idVendedor]
  );
  if (!filas[0]) throw new ErrorHttp(404, 'No se encontró el catálogo de tu cuenta');
  return filas[0];
}

const obtenerPorVendedor = asyncHandler(async (req, res) => {
  const idVendedor = idValido(req.params.vendedorId, 'Identificador de vendedor');
  const [filas] = await pool.query(
    `SELECT c.id_catalogo, c.id_vendedor, c.nombre, c.visible, c.fecha_creacion,
            (SELECT COUNT(*) FROM producto p WHERE p.id_catalogo = c.id_catalogo AND p.visible = 1) AS total_productos
       FROM catalogo c
       JOIN vendedor v ON v.id_vendedor = c.id_vendedor
       JOIN usuario u ON u.id_usuario = v.id_vendedor
      WHERE c.id_vendedor = ? AND c.visible = 1 AND u.id_estado_cuenta = 1
      ORDER BY c.fecha_creacion ASC, c.id_catalogo ASC`,
    [idVendedor]
  );
  res.json({ catalogos: filas.map((fila) => ({ ...fila, visible: Boolean(fila.visible) })) });
});

const obtenerMios = asyncHandler(async (req, res) => {
  exigirVendedor(req);
  const [catalogos] = await pool.query(
    `SELECT c.id_catalogo, c.nombre, c.visible, c.fecha_creacion,
            (SELECT COUNT(*) FROM producto p WHERE p.id_catalogo = c.id_catalogo AND p.visible = 1) AS total_productos
       FROM catalogo c
      WHERE c.id_vendedor = ?
      ORDER BY c.fecha_creacion, c.id_catalogo`,
    [req.usuario.id]
  );
  res.json({ catalogos: catalogos.map((fila) => ({ ...fila, visible: Boolean(fila.visible) })) });
});

const crearCatalogo = asyncHandler(async (req, res) => {
  exigirVendedor(req);
  const nombre = validarNombre(req.body?.nombre);
  const resultado = await transaccion(async (conn) => {
    const [[vendedor]] = await conn.query(
      `SELECT EXISTS (SELECT 1 FROM premium_vendedor WHERE id_vendedor = ?) AS es_premium
         FROM vendedor WHERE id_vendedor = ? FOR UPDATE`,
      [req.usuario.id, req.usuario.id]
    );
    if (!vendedor) {
      throw new ErrorHttp(403, 'Solo las cuentas de vendedor pueden administrar catálogos');
    }
    const [[{ total }]] = await conn.query(
      "SELECT COUNT(*) AS total FROM catalogo WHERE id_vendedor = ? AND visible = 1",
      [req.usuario.id]
    );
    const limite = vendedor.es_premium
      ? C.LIMITE_CATALOGOS_PREMIUM
      : 1;
    if (total >= limite) throw new ErrorHttp(409, `Tu plan permite hasta ${limite} catálogo(s) visible(s)`);

    const [insertado] = await conn.query(
      "INSERT INTO catalogo (id_vendedor, nombre, visible) VALUES (?, ?, 1)",
      [req.usuario.id, nombre]
    );
    const [[catalogo]] = await conn.query(
      'SELECT id_catalogo, nombre, visible, fecha_creacion FROM catalogo WHERE id_catalogo = ?',
      [insertado.insertId]
    );
    return { ...catalogo, visible: Boolean(catalogo.visible) };
  });
  res.status(201).json(resultado);
});

const actualizarCatalogo = asyncHandler(async (req, res) => {
  exigirVendedor(req);
  const id = idValido(req.params.id, 'Identificador de catálogo');
  const tieneNombre = Object.hasOwn(req.body || {}, 'nombre');
  const tieneVisible = Object.hasOwn(req.body || {}, 'visible');
  if (!tieneNombre && !tieneVisible) throw new ErrorHttp(400, 'Indica el nombre o la visibilidad que deseas actualizar');

  const nombre = tieneNombre ? validarNombre(req.body.nombre) : null;
  const visible = tieneVisible ? req.body.visible : null;
  if (tieneVisible && typeof visible !== 'boolean') {
    throw new ErrorValidacion(['Dato inválido en Visible: debe ser booleano']);
  }

  const actualizado = await transaccion(async (conn) => {
    const catalogo = await buscarCatalogoPropio(conn, id, req.usuario.id, true);
    if (visible && !catalogo.visible) {
      const [[vendedor]] = await conn.query(
        `SELECT EXISTS (SELECT 1 FROM premium_vendedor WHERE id_vendedor = ?) AS es_premium
           FROM vendedor WHERE id_vendedor = ? FOR UPDATE`,
        [req.usuario.id, req.usuario.id]
      );
      const [[{ total }]] = await conn.query(
        "SELECT COUNT(*) AS total FROM catalogo WHERE id_vendedor = ? AND visible = 1",
        [req.usuario.id]
      );
      const limite = vendedor.es_premium ? C.LIMITE_CATALOGOS_PREMIUM : 1;
      if (total >= limite) throw new ErrorHttp(409, `Tu plan permite hasta ${limite} catálogo(s) visible(s)`);
    }

    await conn.query(
      'UPDATE catalogo SET nombre = COALESCE(?, nombre), visible = COALESCE(?, visible) WHERE id_catalogo = ?',
      [nombre, visible, id]
    );
    const [[fila]] = await conn.query(
      'SELECT id_catalogo, nombre, visible FROM catalogo WHERE id_catalogo = ?',
      [id]
    );
    return { ...fila, visible: Boolean(fila.visible) };
  });
  res.json(actualizado);
});

const eliminarCatalogo = asyncHandler(async (req, res) => {
  exigirVendedor(req);
  const id = idValido(req.params.id, 'Identificador de catálogo');
  await transaccion(async (conn) => {
    await buscarCatalogoPropio(conn, id, req.usuario.id, true);
    await conn.query('DELETE FROM catalogo WHERE id_catalogo = ?', [id]);
  });
  res.json({ mensaje: 'Catálogo eliminado' });
});

module.exports = { obtenerPorVendedor, obtenerMios, crearCatalogo, actualizarCatalogo, eliminarCatalogo };
