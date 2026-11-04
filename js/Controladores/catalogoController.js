const { pool, transaccion } = require('../config/db');
const C = require('../config/constantes');
const { ErrorHttp, ErrorValidacion, asyncHandler } = require('../utils/errores');
const { entero } = require('../utils/validaciones');

const ROLES_VENDEDOR = [C.ROLES.VENDEDOR_GRATUITO, C.ROLES.VENDEDOR_PREMIUM];
const ESTADOS = ['Visible', 'Oculto'];

function idValido(valor, etiqueta = 'Identificador') {
  const id = entero(valor);
  if (id === null) throw new ErrorHttp(400, `${etiqueta} no válido`);
  return id;
}

function exigirVendedor(req) {
  if (!ROLES_VENDEDOR.includes(req.usuario.rol)) {
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
    `SELECT c.id_catalogo, c.id_vendedor, c.nombre, c.estado
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
    `SELECT c.id_catalogo, c.id_vendedor, c.nombre, c.estado, c.fech_creacion
       FROM catalogo c
       JOIN usuarios u ON u.id_usuario = c.id_vendedor
      WHERE c.id_vendedor = ? AND c.estado = 'Visible'
        AND u.rol IN (?, ?) AND u.estado_cuenta = ?
      ORDER BY c.fech_creacion ASC, c.id_catalogo ASC`,
    [idVendedor, ...ROLES_VENDEDOR, C.ESTADOS_CUENTA.ACTIVA]
  );
  res.json({ catalogos: filas });
});

const crearCatalogo = asyncHandler(async (req, res) => {
  exigirVendedor(req);
  const nombre = validarNombre(req.body?.nombre);
  const resultado = await transaccion(async (conn) => {
    const [[vendedor]] = await conn.query(
      'SELECT rol FROM usuarios WHERE id_usuario = ? FOR UPDATE',
      [req.usuario.id]
    );
    if (!vendedor || !ROLES_VENDEDOR.includes(vendedor.rol)) {
      throw new ErrorHttp(403, 'Solo las cuentas de vendedor pueden administrar catálogos');
    }
    const [[{ total }]] = await conn.query(
      "SELECT COUNT(*) AS total FROM catalogo WHERE id_vendedor = ? AND estado = 'Visible'",
      [req.usuario.id]
    );
    const limite = vendedor.rol === C.ROLES.VENDEDOR_PREMIUM
      ? C.LIMITE_CATALOGOS_PREMIUM
      : 1;
    if (total >= limite) throw new ErrorHttp(409, `Tu plan permite hasta ${limite} catálogo(s) visible(s)`);

    const [insertado] = await conn.query(
      "INSERT INTO catalogo (id_vendedor, nombre, estado) VALUES (?, ?, 'Visible')",
      [req.usuario.id, nombre]
    );
    return insertado.insertId;
  });
  res.status(201).json({ mensaje: 'Catálogo creado', id_catalogo: resultado });
});

const actualizarCatalogo = asyncHandler(async (req, res) => {
  exigirVendedor(req);
  const id = idValido(req.params.id, 'Identificador de catálogo');
  const tieneNombre = Object.hasOwn(req.body || {}, 'nombre');
  const tieneEstado = Object.hasOwn(req.body || {}, 'estado');
  if (!tieneNombre && !tieneEstado) throw new ErrorHttp(400, 'Indica el nombre o el estado que deseas actualizar');

  const nombre = tieneNombre ? validarNombre(req.body.nombre) : null;
  const estado = tieneEstado ? req.body.estado : null;
  if (tieneEstado && !ESTADOS.includes(estado)) {
    throw new ErrorValidacion(['Dato inválido en Estado: Visible u Oculto']);
  }

  await transaccion(async (conn) => {
    const catalogo = await buscarCatalogoPropio(conn, id, req.usuario.id, true);
    if (estado === 'Visible' && catalogo.estado !== 'Visible') {
      const [[vendedor]] = await conn.query(
        'SELECT rol FROM usuarios WHERE id_usuario = ? FOR UPDATE',
        [req.usuario.id]
      );
      const [[{ total }]] = await conn.query(
        "SELECT COUNT(*) AS total FROM catalogo WHERE id_vendedor = ? AND estado = 'Visible'",
        [req.usuario.id]
      );
      const limite = vendedor.rol === C.ROLES.VENDEDOR_PREMIUM ? C.LIMITE_CATALOGOS_PREMIUM : 1;
      if (total >= limite) throw new ErrorHttp(409, `Tu plan permite hasta ${limite} catálogo(s) visible(s)`);
    }

    await conn.query(
      'UPDATE catalogo SET nombre = COALESCE(?, nombre), estado = COALESCE(?, estado) WHERE id_catalogo = ?',
      [nombre, estado, id]
    );
  });
  res.json({ mensaje: 'Catálogo actualizado' });
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

module.exports = { obtenerPorVendedor, crearCatalogo, actualizarCatalogo, eliminarCatalogo };
