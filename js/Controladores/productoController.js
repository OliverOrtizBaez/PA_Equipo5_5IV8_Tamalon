const { pool, transaccion } = require('../config/db');
const C = require('../config/constantes');
const { ErrorHttp, ErrorValidacion, asyncHandler } = require('../utils/errores');
const { entero } = require('../utils/validaciones');

const ROLES_VENDEDOR = [C.ROLES.VENDEDOR_GRATUITO, C.ROLES.VENDEDOR_PREMIUM];
const CATEGORIAS = ['Comida preparada', 'Bebidas', 'Postres y dulces', 'Frutas y verduras', 'Botanas y snacks', 'Otros'];
const ESTADOS = ['Visible', 'Oculto'];

function idValido(valor, etiqueta = 'Identificador') {
  const id = entero(valor);
  if (id === null) throw new ErrorHttp(400, `${etiqueta} no válido`);
  return id;
}

function exigirVendedor(req) {
  if (!ROLES_VENDEDOR.includes(req.usuario.rol)) {
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
  if (typeof valor !== 'string' || valor.length > 2048) {
    throw new ErrorValidacion(['Dato inválido en Imagen: debe ser una URL HTTPS de hasta 2048 caracteres']);
  }
  let url;
  try { url = new URL(valor); } catch (_) {
    throw new ErrorValidacion(['Dato inválido en Imagen: debe ser una URL HTTPS válida']);
  }
  if (url.protocol !== 'https:') {
    throw new ErrorValidacion(['Dato inválido en Imagen: solo se permiten URL HTTPS']);
  }
  return url.toString();
}

function validarCategoria(valor) {
  if (!CATEGORIAS.includes(valor)) {
    throw new ErrorValidacion([`Dato inválido en Categoría: selecciona una de estas opciones: ${CATEGORIAS.join(', ')}`]);
  }
  return valor;
}

async function buscarProductoPropio(conn, id, idVendedor, lock = false) {
  const [filas] = await conn.query(
    `SELECT p.id_producto, p.id_catalogo, p.nombre, p.descripcion, p.precio, p.imagen, p.estado, p.categoria
       FROM producto p
       JOIN catalogo c ON c.id_catalogo = p.id_catalogo
      WHERE p.id_producto = ? AND c.id_vendedor = ?${lock ? ' FOR UPDATE' : ''}`,
    [id, idVendedor]
  );
  if (!filas[0]) throw new ErrorHttp(404, 'No se encontró el producto de tu cuenta');
  return filas[0];
}

const obtenerPorCatalogo = asyncHandler(async (req, res) => {
  const idCatalogo = idValido(req.params.catalogoId, 'Identificador de catálogo');
  const [[catalogo]] = await pool.query(
    `SELECT c.id_catalogo, c.id_vendedor
       FROM catalogo c
       JOIN usuarios u ON u.id_usuario = c.id_vendedor
      WHERE c.id_catalogo = ? AND c.estado = 'Visible'
        AND u.rol IN (?, ?) AND u.estado_cuenta = ?`,
    [idCatalogo, ...ROLES_VENDEDOR, C.ESTADOS_CUENTA.ACTIVA]
  );
  if (!catalogo) throw new ErrorHttp(404, 'No se encontró el catálogo');

  const [productos] = await pool.query(
    `SELECT id_producto, id_catalogo, nombre, descripcion, precio, imagen, estado, categoria, fech_creacion
       FROM producto
      WHERE id_catalogo = ? AND estado = 'Visible'
      ORDER BY fech_creacion ASC, id_producto ASC`,
    [idCatalogo]
  );
  res.json({ productos });
});

const crearProducto = asyncHandler(async (req, res) => {
  exigirVendedor(req);
  const idCatalogo = idValido(req.params.catalogoId, 'Identificador de catálogo');
  const nombre = validarTexto(req.body?.nombre, 'Nombre', 2, 60);
  const descripcion = validarTexto(req.body?.descripcion, 'Descripción', 1, 300);
  const precio = validarPrecio(req.body?.precio);
  const categoria = validarCategoria(req.body?.categoria);
  const imagen = validarImagen(req.body?.imagen ?? null);

  const idProducto = await transaccion(async (conn) => {
    const [catalogos] = await conn.query(
      `SELECT c.id_catalogo, c.estado
         FROM catalogo c
        WHERE c.id_catalogo = ? AND c.id_vendedor = ?
        FOR UPDATE`,
      [idCatalogo, req.usuario.id]
    );
    if (!catalogos[0]) throw new ErrorHttp(404, 'No se encontró el catálogo de tu cuenta');
    if (catalogos[0].estado !== 'Visible') throw new ErrorHttp(409, 'No puedes agregar productos a un catálogo oculto');

    const [[vendedor]] = await conn.query(
      'SELECT rol FROM usuarios WHERE id_usuario = ? FOR UPDATE',
      [req.usuario.id]
    );
    if (!vendedor || !ROLES_VENDEDOR.includes(vendedor.rol)) {
      throw new ErrorHttp(403, 'Solo las cuentas de vendedor pueden administrar productos');
    }
    const [[{ total }]] = await conn.query(
      "SELECT COUNT(*) AS total FROM producto WHERE id_catalogo = ? AND estado = 'Visible'",
      [idCatalogo]
    );
    const limite = vendedor.rol === C.ROLES.VENDEDOR_PREMIUM
      ? C.LIMITE_PRODUCTOS_PREMIUM
      : C.LIMITE_PRODUCTOS_GRATIS;
    if (total >= limite) throw new ErrorHttp(409, `Tu plan permite hasta ${limite} productos visibles por catálogo`);

    const [insertado] = await conn.query(
      `INSERT INTO producto (id_catalogo, nombre, descripcion, precio, imagen, estado, categoria)
       VALUES (?, ?, ?, ?, ?, 'Visible', ?)`,
      [idCatalogo, nombre, descripcion, precio, imagen, categoria]
    );
    return insertado.insertId;
  });

  res.status(201).json({ mensaje: 'Producto creado', id_producto: idProducto });
});

const actualizarProducto = asyncHandler(async (req, res) => {
  exigirVendedor(req);
  const id = idValido(req.params.id, 'Identificador de producto');
  const datos = req.body || {};
  const campos = ['nombre', 'descripcion', 'precio', 'imagen', 'categoria', 'estado'];
  const proporcionados = campos.filter((campo) => Object.hasOwn(datos, campo));
  if (!proporcionados.length) throw new ErrorHttp(400, 'Indica los datos que deseas actualizar');

  const actualizados = {};
  if (Object.hasOwn(datos, 'nombre')) actualizados.nombre = validarTexto(datos.nombre, 'Nombre', 2, 60);
  if (Object.hasOwn(datos, 'descripcion')) actualizados.descripcion = validarTexto(datos.descripcion, 'Descripción', 1, 300);
  if (Object.hasOwn(datos, 'precio')) actualizados.precio = validarPrecio(datos.precio);
  if (Object.hasOwn(datos, 'imagen')) actualizados.imagen = validarImagen(datos.imagen);
  if (Object.hasOwn(datos, 'categoria')) actualizados.categoria = validarCategoria(datos.categoria);
  if (Object.hasOwn(datos, 'estado')) {
    if (!ESTADOS.includes(datos.estado)) throw new ErrorValidacion(['Dato inválido en Estado: Visible u Oculto']);
    actualizados.estado = datos.estado;
  }

  await transaccion(async (conn) => {
    const producto = await buscarProductoPropio(conn, id, req.usuario.id, true);
    if (actualizados.estado === 'Visible' && producto.estado !== 'Visible') {
      const [[vendedor]] = await conn.query(
        'SELECT rol FROM usuarios WHERE id_usuario = ? FOR UPDATE',
        [req.usuario.id]
      );
      const [[{ total }]] = await conn.query(
        "SELECT COUNT(*) AS total FROM producto WHERE id_catalogo = ? AND estado = 'Visible'",
        [producto.id_catalogo]
      );
      const limite = vendedor.rol === C.ROLES.VENDEDOR_PREMIUM
        ? C.LIMITE_PRODUCTOS_PREMIUM
        : C.LIMITE_PRODUCTOS_GRATIS;
      if (total >= limite) throw new ErrorHttp(409, `Tu plan permite hasta ${limite} productos visibles por catálogo`);
    }

    const asignaciones = proporcionados.map((campo) => `${campo} = ?`).join(', ');
    await conn.query(
      `UPDATE producto SET ${asignaciones} WHERE id_producto = ?`,
      [...proporcionados.map((campo) => actualizados[campo]), id]
    );
  });
  res.json({ mensaje: 'Producto actualizado' });
});

const eliminarProducto = asyncHandler(async (req, res) => {
  exigirVendedor(req);
  const id = idValido(req.params.id, 'Identificador de producto');
  await transaccion(async (conn) => {
    await buscarProductoPropio(conn, id, req.usuario.id, true);
    await conn.query('DELETE FROM producto WHERE id_producto = ?', [id]);
  });
  res.json({ mensaje: 'Producto eliminado' });
});

module.exports = { obtenerPorCatalogo, crearProducto, actualizarProducto, eliminarProducto };
