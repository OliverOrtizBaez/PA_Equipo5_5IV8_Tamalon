const { LIMITE_CATALOGOS_PREMIUM, LIMITE_PRODUCTOS_GRATIS, LIMITE_PRODUCTOS_PREMIUM } = require('../config/constantes');

async function activarPremium(conn, idVendedor, idPago = null) {
  await conn.query(
    'INSERT INTO premium_vendedor (id_vendedor, id_pago) VALUES (?, ?)',
    [idVendedor, idPago]
  );
  await restaurarContenidoVendedor(conn, idVendedor);
}

async function revocarPremium(conn, idVendedor) {
  await conn.query('DELETE FROM premium_vendedor WHERE id_vendedor = ?', [idVendedor]);
  await limitarContenidoVendedor(conn, idVendedor);
}

async function limitarContenidoVendedor(conn, idVendedor) {
  const [catalogos] = await conn.query(
    `SELECT id_catalogo FROM catalogo
      WHERE id_vendedor = ? ORDER BY fecha_creacion, id_catalogo FOR UPDATE`,
    [idVendedor]
  );
  const conservado = catalogos[0]?.id_catalogo;
  if (conservado !== undefined) {
    await conn.query(
      'UPDATE catalogo SET visible = (id_catalogo = ?) WHERE id_vendedor = ?',
      [conservado, idVendedor]
    );
    await conn.query(
      `UPDATE producto p
         JOIN (SELECT id_producto,
                      ROW_NUMBER() OVER (ORDER BY fecha_creacion, id_producto) AS rn
                 FROM producto WHERE id_catalogo = ?) x ON x.id_producto = p.id_producto
          SET p.visible = 0 WHERE x.rn > ?`,
      [conservado, LIMITE_PRODUCTOS_GRATIS]
    );
  }
  await conn.query('UPDATE ruta SET activa = 0 WHERE id_vendedor = ?', [idVendedor]);
}

async function restaurarContenidoVendedor(conn, idVendedor) {
  const [catalogos] = await conn.query(
    `SELECT id_catalogo, visible FROM catalogo
      WHERE id_vendedor = ? ORDER BY fecha_creacion, id_catalogo FOR UPDATE`,
    [idVendedor]
  );
  const visibles = catalogos.filter((catalogo) => Boolean(catalogo.visible)).length;
  const cupo = Math.max(0, LIMITE_CATALOGOS_PREMIUM - visibles);
  const restaurar = catalogos.filter((catalogo) => !catalogo.visible)
    .slice(0, cupo).map((catalogo) => catalogo.id_catalogo);
  if (restaurar.length) {
    await conn.query('UPDATE catalogo SET visible = 1 WHERE id_catalogo IN (?)', [restaurar]);
  }
  await conn.query(
    `UPDATE producto p
       JOIN (SELECT id_producto,
                    ROW_NUMBER() OVER (PARTITION BY id_catalogo ORDER BY fecha_creacion, id_producto) AS rn
               FROM producto
              WHERE id_catalogo IN (SELECT id_catalogo FROM catalogo WHERE id_vendedor = ? AND visible = 1)) x
         ON x.id_producto = p.id_producto
        SET p.visible = 1
      WHERE x.rn <= ? AND p.visible = 0`,
    [idVendedor, LIMITE_PRODUCTOS_PREMIUM]
  );
  await conn.query('UPDATE ruta SET activa = 1 WHERE id_vendedor = ?', [idVendedor]);
}

module.exports = { activarPremium, revocarPremium, limitarContenidoVendedor, restaurarContenidoVendedor };
