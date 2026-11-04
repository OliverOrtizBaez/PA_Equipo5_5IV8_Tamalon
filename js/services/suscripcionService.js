// Logica de cambio de plan. Recibe una conexion de transaccion para que el rol, los catalogos,
// los productos y las rutas cambien juntos o no cambien. La reutilizaran los pagos (RF5.x) y los jobs.
const {
  ROLES, PREMIUM_DIAS, LIMITE_CATALOGOS_PREMIUM, LIMITE_PRODUCTOS_GRATIS, LIMITE_PRODUCTOS_PREMIUM,
} = require('../config/constantes');
const { fechaEnZona, sumarDiasFecha } = require('../utils/fechas');

const esVendedor = (rol) => rol === ROLES.VENDEDOR_GRATUITO || rol === ROLES.VENDEDOR_PREMIUM;
const esGratuito = (rol) => rol === ROLES.VENDEDOR_GRATUITO || rol === ROLES.CLIENTE_GRATUITO;
const esPremium = (rol) => rol === ROLES.VENDEDOR_PREMIUM || rol === ROLES.CLIENTE_PREMIUM;

/** Fecha de corte = dia de hoy en ZH-N + 30 dias (RF5.7 / RF17b). Devuelve 'YYYY-MM-DD'. */
const calcularFechaCorte = (desde = new Date()) => sumarDiasFecha(fechaEnZona(desde), PREMIUM_DIAS);

/**
 * Gratuito -> Premium del mismo tipo.
 * Vendedor: restaura catalogos/productos (RF5.8a) y reactiva rutas (RF5.8b).
 * Cliente: su lista de favoritos queda habilitada solo por tener el rol (RF9.4b).
 */
async function activarPremium(conn, usuario, { fechaCorte, renovacion }) {
  const rolNuevo = usuario.rol === ROLES.VENDEDOR_GRATUITO ? ROLES.VENDEDOR_PREMIUM : ROLES.CLIENTE_PREMIUM;
  await conn.query('UPDATE usuarios SET rol = ?, fecha_corte = ?, renovacion = ? WHERE id_usuario = ?',
    [rolNuevo, fechaCorte, renovacion ? 1 : 0, usuario.id_usuario]);
  if (esVendedor(usuario.rol)) await restaurarContenidoVendedor(conn, usuario.id_usuario);
  return rolNuevo;
}

/**
 * Premium -> Gratuito del mismo tipo (borra fecha de corte y desactiva renovacion).
 * Vendedor: oculta catalogos (RF5.5a) y productos excedentes (RF5.5b), inactiva rutas (RF5.6).
 * Cliente: los favoritos NO se borran; quedan inaccesibles por rol (RF9.4a).
 */
async function degradarAGratuito(conn, usuario) {
  const rolNuevo = usuario.rol === ROLES.VENDEDOR_PREMIUM ? ROLES.VENDEDOR_GRATUITO : ROLES.CLIENTE_GRATUITO;
  await conn.query('UPDATE usuarios SET rol = ?, fecha_corte = NULL, renovacion = 0 WHERE id_usuario = ?',
    [rolNuevo, usuario.id_usuario]);
  if (esVendedor(usuario.rol)) await limitarContenidoVendedor(conn, usuario.id_usuario);
  return rolNuevo;
}

// RF5.5a + RF5.5b + RF5.6
async function limitarContenidoVendedor(conn, idVendedor) {
  const [cats] = await conn.query(
    'SELECT id_catalogo FROM catalogo WHERE id_vendedor = ? ORDER BY fech_creacion ASC, id_catalogo ASC FOR UPDATE',
    [idVendedor]
  );
  if (cats.length > 0) {
    const conservado = cats[0].id_catalogo; // el mas antiguo (empate: menor ID), aunque este vacio
    await conn.query(
      "UPDATE catalogo SET estado = IF(id_catalogo = ?, 'Visible', 'Oculto') WHERE id_vendedor = ?",
      [conservado, idVendedor]
    );
    await conn.query(
      `UPDATE producto p
         JOIN (SELECT id_producto,
                      ROW_NUMBER() OVER (ORDER BY fech_creacion ASC, id_producto ASC) AS rn
                 FROM producto WHERE id_catalogo = ?) x ON x.id_producto = p.id_producto
          SET p.estado = 'Oculto'
        WHERE x.rn > ?`,
      [conservado, LIMITE_PRODUCTOS_GRATIS]
    );
  }
  await conn.query("UPDATE ruta SET estado = 'Inactiva' WHERE id_vendedor = ?", [idVendedor]);
}

// RF5.8a + RF5.8b: se restauran los mas antiguos respetando RF10.2 (3 catalogos) y RF11.2 (50 productos)
async function restaurarContenidoVendedor(conn, idVendedor) {
  const [cats] = await conn.query(
    'SELECT id_catalogo, estado FROM catalogo WHERE id_vendedor = ? ORDER BY fech_creacion ASC, id_catalogo ASC FOR UPDATE',
    [idVendedor]
  );
  const visibles = cats.filter((c) => c.estado === 'Visible').length;
  const cupo = Math.max(0, LIMITE_CATALOGOS_PREMIUM - visibles);
  const aRestaurar = cats.filter((c) => c.estado === 'Oculto').slice(0, cupo).map((c) => c.id_catalogo);
  if (aRestaurar.length > 0) {
    await conn.query("UPDATE catalogo SET estado = 'Visible' WHERE id_catalogo IN (?)", [aRestaurar]);
  }
  await conn.query(
    `UPDATE producto p
       JOIN (SELECT id_producto,
                    ROW_NUMBER() OVER (PARTITION BY id_catalogo ORDER BY fech_creacion ASC, id_producto ASC) AS rn
               FROM producto
              WHERE id_catalogo IN (SELECT id_catalogo FROM catalogo WHERE id_vendedor = ? AND estado = 'Visible')) x
         ON x.id_producto = p.id_producto
        SET p.estado = 'Visible'
      WHERE x.rn <= ? AND p.estado = 'Oculto'`,
    [idVendedor, LIMITE_PRODUCTOS_PREMIUM]
  );
  await conn.query("UPDATE ruta SET estado = 'Activa' WHERE id_vendedor = ?", [idVendedor]);
}

module.exports = {
  esVendedor, esGratuito, esPremium, calcularFechaCorte, activarPremium, degradarAGratuito,
};
