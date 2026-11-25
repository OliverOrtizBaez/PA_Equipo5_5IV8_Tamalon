const { pool, transaccion } = require('../config/db');
const C = require('../config/constantes');
const { ErrorHttp, ErrorValidacion, asyncHandler } = require('../utils/errores');
const { entero, esUrlCloudinary } = require('../utils/validaciones');

function idValido(valor, etiqueta = 'Identificador') {
  const id = entero(valor);
  if (id === null) throw new ErrorHttp(400, `${etiqueta} no válido`);
  return id;
}

function exigirVendedor(req) {
  if (req.usuario.tipo !== C.TIPOS.VENDEDOR) {
    throw new ErrorHttp(403, 'Solo las cuentas de vendedor pueden administrar productos');
  }
}

function validarTexto(valor, campo, minimo, maximo) {
  if (typeof valor !== 'string' || valor.trim().length < minimo || valor.trim().length > maximo) {
    throw new ErrorValidacion([`Dato inválido en ${campo}: debe contener de ${minimo} a ${maximo} caracteres`]);
  }
  return valor.trim();
}

function validarPrecio(valor) {
  const texto = typeof valor === 'number' ? String(valor) : valor;
  if (typeof texto !== 'string' || !/^\d+(?:\.\d{1,2})?$/.test(texto)) {
    throw new ErrorValidacion(['Dato inválido en Precio: usa un valor de 0.01 a 99999.99 con máximo 2 decimales']);
  }
  const precio = Number(texto);
  if (!Number.isFinite(precio) || precio < 0.01 || precio > 99999.99) {
    throw new ErrorValidacion(['Dato inválido en Precio: usa un valor de 0.01 a 99999.99 con máximo 2 decimales']);
  }
  return precio.toFixed(2);
}

function validarImagen(valor) {
  if (valor === null || valor === '') return null;
  if (!esUrlCloudinary(valor)) {
    throw new ErrorValidacion(['Dato inválido en Imagen: debe ser una URL HTTPS de Cloudinary de hasta 2048 caracteres']);
  }
  return valor;
}

function validarCategoria(valor) {
  const id = entero(valor);
  if (id === null) throw new ErrorValidacion(['Dato inválido en Categoría: id_categoria no válido']);
  return id;
}

async function buscarProductoPropio(conn, id, idVendedor, lock = false) {
  const [filas] = await conn.query(
    `SELECT p.id_producto, p.id_catalogo, p.id_categoria, cp.nombre AS categoria,
            p.nombre, p.descripcion, p.precio, p.imagen, p.visible, p.fecha_creacion,
            v.id_tipo_vendedor
       FROM producto p
       JOIN catalogo c ON c.id_catalogo = p.id_catalogo
       JOIN vendedor v ON v.id_vendedor = c.id_vendedor
       JOIN categoria_producto cp ON cp.id_categoria = p.id_categoria
      WHERE p.id_producto = ? AND c.id_vendedor = ?${lock ? ' FOR UPDATE' : ''}`,
    [id, idVendedor]
  );
  if (!filas[0]) throw new ErrorHttp(404, 'No se encontró el producto de tu cuenta');
  return filas[0];
}

async function productoCompleto(conn, id) {
  const [[producto]] = await conn.query(
    `SELECT p.id_producto, p.id_catalogo, p.id_categoria, cp.nombre AS categoria,
            p.nombre, p.descripcion, p.precio, p.imagen, p.visible
       FROM producto p JOIN categoria_producto cp ON cp.id_categoria = p.id_categoria
      WHERE p.id_producto = ?`,
    [id]
  );
  return producto;
}

const obtenerPorCatalogo = asyncHandler(async (req, res) => {
  const idCatalogo = idValido(req.params.catalogoId, 'Identificador de catálogo');
  const [[catalogo]] = await pool.query(
    `SELECT c.id_catalogo
       FROM catalogo c JOIN usuario u ON u.id_usuario = c.id_vendedor
      WHERE c.id_catalogo = ? AND c.visible = 1 AND u.id_estado_cuenta = 1`,
    [idCatalogo]
  );
  if (!catalogo) throw new ErrorHttp(404, 'No se encontró el catálogo');

  const [productos] = await pool.query(
    `SELECT p.id_producto, p.id_catalogo, p.id_categoria, cp.nombre AS categoria,
            p.nombre, p.descripcion, p.precio, p.imagen, p.visible
       FROM producto p JOIN categoria_producto cp ON cp.id_categoria = p.id_categoria
      WHERE p.id_catalogo = ? AND p.visible = 1
      ORDER BY p.fecha_creacion, p.id_producto`,
    [idCatalogo]
  );
  res.json({ productos: productos.map((producto) => ({ ...producto, visible: Boolean(producto.visible) })) });
});

const crearProducto = asyncHandler(async (req, res) => {
  exigirVendedor(req);
  const idCatalogo = idValido(req.params.catalogoId, 'Identificador de catálogo');
  const nombre = validarTexto(req.body?.nombre, 'Nombre', 2, 60);
  const descripcion = validarTexto(req.body?.descripcion, 'Descripción', 1, 300);
  const precio = validarPrecio(req.body?.precio);
  const idCategoria = validarCategoria(req.body?.id_categoria);
  const imagen = validarImagen(req.body?.imagen ?? null);

  const producto = await transaccion(async (conn) => {
    const [catalogos] = await conn.query(
      `SELECT c.id_catalogo, c.visible, v.id_tipo_vendedor,
              EXISTS (SELECT 1 FROM premium_vendedor pv WHERE pv.id_vendedor = v.id_vendedor) AS es_premium
         FROM catalogo c JOIN vendedor v ON v.id_vendedor = c.id_vendedor
        WHERE c.id_catalogo = ? AND c.id_vendedor = ?
        FOR UPDATE`,
      [idCatalogo, req.usuario.id]
    );
    const catalogo = catalogos[0];
    if (!catalogo) throw new ErrorHttp(404, 'No se encontró el catálogo de tu cuenta');
    if (!catalogo.visible) throw new ErrorHttp(409, 'No puedes agregar productos a un catálogo oculto');

    const [categorias] = await conn.query(
      'SELECT id_categoria FROM categoria_producto WHERE id_categoria = ? AND id_tipo_vendedor = ?',
      [idCategoria, catalogo.id_tipo_vendedor]
    );
    if (!categorias[0]) throw new ErrorValidacion(['La categoría no pertenece al tipo de vendedor de tu cuenta']);

    const [[{ total }]] = await conn.query(
      'SELECT COUNT(*) AS total FROM producto WHERE id_catalogo = ? AND visible = 1',
      [idCatalogo]
    );
    const limite = catalogo.es_premium ? C.LIMITE_PRODUCTOS_PREMIUM : C.LIMITE_PRODUCTOS_GRATIS;
    if (total >= limite) throw new ErrorHttp(409, `Tu plan permite hasta ${limite} productos visibles por catálogo`);

    const [insertado] = await conn.query(
      `INSERT INTO producto (id_catalogo, id_categoria, nombre, descripcion, precio, imagen, visible)
       VALUES (?, ?, ?, ?, ?, ?, 1)`,
      [idCatalogo, idCategoria, nombre, descripcion, precio, imagen]
    );
    const creado = await productoCompleto(conn, insertado.insertId);
    return { ...creado, visible: Boolean(creado.visible) };
  });
  res.status(201).json(producto);
});

const actualizarProducto = asyncHandler(async (req, res) => {
  exigirVendedor(req);
  const id = idValido(req.params.id, 'Identificador de producto');
  const datos = req.body || {};
  const campos = ['nombre', 'descripcion', 'precio', 'imagen', 'id_categoria', 'visible'];
  const proporcionados = campos.filter((campo) => Object.hasOwn(datos, campo));
  if (!proporcionados.length) throw new ErrorHttp(400, 'Indica los datos que deseas actualizar');

  const actualizados = {};
  if (Object.hasOwn(datos, 'nombre')) actualizados.nombre = validarTexto(datos.nombre, 'Nombre', 2, 60);
  if (Object.hasOwn(datos, 'descripcion')) actualizados.descripcion = validarTexto(datos.descripcion, 'Descripción', 1, 300);
  if (Object.hasOwn(datos, 'precio')) actualizados.precio = validarPrecio(datos.precio);
  if (Object.hasOwn(datos, 'imagen')) actualizados.imagen = validarImagen(datos.imagen);
  if (Object.hasOwn(datos, 'id_categoria')) actualizados.id_categoria = validarCategoria(datos.id_categoria);
  if (Object.hasOwn(datos, 'visible')) {
    if (typeof datos.visible !== 'boolean') throw new ErrorValidacion(['Dato inválido en Visible: debe ser booleano']);
    actualizados.visible = datos.visible ? 1 : 0;
  }

  const producto = await transaccion(async (conn) => {
    const actual = await buscarProductoPropio(conn, id, req.usuario.id, true);
    if (actualizados.id_categoria !== undefined) {
      const [categorias] = await conn.query(
        'SELECT id_categoria FROM categoria_producto WHERE id_categoria = ? AND id_tipo_vendedor = ?',
        [actualizados.id_categoria, actual.id_tipo_vendedor]
      );
      if (!categorias[0]) throw new ErrorValidacion(['La categoría no pertenece al tipo de vendedor de tu cuenta']);
    }

    if (actualizados.visible === 1 && !actual.visible) {
      const [[vendedor]] = await conn.query(
        `SELECT EXISTS (SELECT 1 FROM premium_vendedor WHERE id_vendedor = ?) AS es_premium
           FROM vendedor WHERE id_vendedor = ? FOR UPDATE`,
        [req.usuario.id, req.usuario.id]
      );
      const [[{ total }]] = await conn.query(
        'SELECT COUNT(*) AS total FROM producto WHERE id_catalogo = ? AND visible = 1',
        [actual.id_catalogo]
      );
      const limite = vendedor.es_premium ? C.LIMITE_PRODUCTOS_PREMIUM : C.LIMITE_PRODUCTOS_GRATIS;
      if (total >= limite) throw new ErrorHttp(409, `Tu plan permite hasta ${limite} productos visibles por catálogo`);
    }

    const assignments = proporcionados.map((campo) => `${campo} = ?`).join(', ');
    await conn.query(
      `UPDATE producto SET ${assignments} WHERE id_producto = ?`,
      [...proporcionados.map((campo) => actualizados[campo]), id]
    );
    const actualizado = await productoCompleto(conn, id);
    return { ...actualizado, visible: Boolean(actualizado.visible) };
  });
  res.json(producto);
});

const eliminarProducto = asyncHandler(async (req, res) => {
  exigirVendedor(req);
  const id = idValido(req.params.id, 'Identificador de producto');
  await transaccion(async (conn) => {
    await buscarProductoPropio(conn, id, req.usuario.id, true);
    await conn.query('DELETE FROM producto WHERE id_producto = ?', [id]);
  });
  res.status(204).end();
});

module.exports = { obtenerPorCatalogo, crearProducto, actualizarProducto, eliminarProducto, validarImagen };
