require('dotenv').config();
const { pool, transaccion } = require('../js/config/db');
const { crearCuenta } = require('../js/services/cuentasService');
const { TIPOS, PRECIO_PREMIUM } = require('../js/config/constantes');

const password = process.env.SEED_DEV_PASSWORD;
if (!password) {
  throw new Error('Define SEED_DEV_PASSWORD para crear las cuentas de prueba.');
}

async function obtenerPorCorreo(correo) {
  const [[usuario]] = await pool.query('SELECT id_usuario FROM usuario WHERE correo = ?', [correo]);
  return usuario?.id_usuario ?? null;
}

async function crearSiNoExiste(datos, tipo) {
  const idExistente = await obtenerPorCorreo(datos.correo);
  if (idExistente) return idExistente;
  const usuario = await crearCuenta({
    ...datos,
    contrasena: datos.contrasena || password,
    confirmar_contrasena: datos.contrasena || password,
  }, tipo);
  return usuario.id_usuario;
}

async function crearAdmin() {
  const { ADMIN_NOMBRE: nombre, ADMIN_APELLIDOS: apellidos, ADMIN_CORREO: correo, ADMIN_PASS: contrasena } = process.env;
  if (![nombre, apellidos, correo, contrasena].every(Boolean)) {
    throw new Error('Configura ADMIN_NOMBRE, ADMIN_APELLIDOS, ADMIN_CORREO y ADMIN_PASS en el entorno.');
  }
  return crearSiNoExiste({ nombre, apellidos, correo, contrasena }, TIPOS.ADMIN);
}

async function crearVendedor({ clave, nombre, apellidos, correo, premium }) {
  const [[tipo]] = await pool.query(
    'SELECT id_tipo_vendedor FROM tipo_vendedor WHERE clave = ? AND activo = 1',
    [clave]
  );
  if (!tipo) throw new Error(`No existe el tipo de vendedor ${clave}.`);
  const idVendedor = await crearSiNoExiste({
    nombre, apellidos, correo, id_tipo_vendedor: tipo.id_tipo_vendedor,
  }, TIPOS.VENDEDOR);
  const [[vendedor]] = await pool.query('SELECT id_tipo_vendedor FROM vendedor WHERE id_vendedor = ?', [idVendedor]);
  if (!vendedor || vendedor.id_tipo_vendedor !== tipo.id_tipo_vendedor) {
    throw new Error(`La cuenta ${correo} existe con un tipo distinto al solicitado.`);
  }
  if (premium) {
    await transaccion(async (conn) => {
      const [[actual]] = await conn.query(
        'SELECT id_vendedor FROM premium_vendedor WHERE id_vendedor = ? FOR UPDATE',
        [idVendedor]
      );
      if (actual) return;
      const [pago] = await conn.query(
        `INSERT INTO pago (id_vendedor, id_estado_pago, monto, moneda, codigo_transaccion)
         VALUES (?, 1, ?, 'MXN', ?)`,
        [idVendedor, PRECIO_PREMIUM, `DEV-SEED-${idVendedor}-${Date.now()}`]
      );
      await conn.query(
        'INSERT INTO premium_vendedor (id_vendedor, id_pago) VALUES (?, ?)',
        [idVendedor, pago.insertId]
      );
    });
  }
  await crearCatalogoEjemplo(idVendedor, tipo.id_tipo_vendedor, clave);
  return idVendedor;
}

async function crearCatalogoEjemplo(idVendedor, idTipo, clave) {
  await transaccion(async (conn) => {
    let [[catalogo]] = await conn.query(
      'SELECT id_catalogo FROM catalogo WHERE id_vendedor = ? AND nombre = ? FOR UPDATE',
      [idVendedor, `Ejemplo ${clave}`]
    );
    if (!catalogo) {
      const [insertado] = await conn.query(
        'INSERT INTO catalogo (id_vendedor, nombre, visible) VALUES (?, ?, 1)',
        [idVendedor, `Ejemplo ${clave}`]
      );
      catalogo = { id_catalogo: insertado.insertId };
    }
    const [[categoria]] = await conn.query(
      'SELECT id_categoria FROM categoria_producto WHERE id_tipo_vendedor = ? ORDER BY id_categoria LIMIT 1',
      [idTipo]
    );
    if (!categoria) throw new Error(`El tipo ${clave} no tiene categorías de producto configuradas.`);
    const [[producto]] = await conn.query(
      'SELECT id_producto FROM producto WHERE id_catalogo = ? AND nombre = ?',
      [catalogo.id_catalogo, `Producto de ${clave}`]
    );
    if (!producto) {
      await conn.query(
        `INSERT INTO producto (id_catalogo, id_categoria, nombre, descripcion, precio, visible)
         VALUES (?, ?, ?, ?, ?, 1)`,
        [catalogo.id_catalogo, categoria.id_categoria, `Producto de ${clave}`, 'Artículo de ejemplo para desarrollo', 25.00]
      );
    }
  });
}

(async () => {
  try {
    const adminId = await crearAdmin();
    const vendedores = [
      { clave: 'ropavejero', nombre: 'Rosa', apellidos: 'Vende Todo', correo: 'seed.ropavejero@example.test', premium: true },
      { clave: 'limpieza', nombre: 'Luis', apellidos: 'Limpieza', correo: 'seed.limpieza@example.test', premium: false },
      { clave: 'fruta_verdura', nombre: 'María', apellidos: 'Frutería', correo: 'seed.frutas@example.test', premium: false },
    ];
    const idsVendedores = [];
    for (const vendedor of vendedores) idsVendedores.push(await crearVendedor(vendedor));
    const clientes = [
      { nombre: 'Carlos', apellidos: 'Cliente Uno', correo: 'seed.cliente1@example.test' },
      { nombre: 'Elena', apellidos: 'Cliente Dos', correo: 'seed.cliente2@example.test' },
    ];
    const idsClientes = [];
    for (const cliente of clientes) {
      idsClientes.push(await crearSiNoExiste(cliente, TIPOS.CLIENTE));
    }
    console.log(JSON.stringify({ adminId, idsVendedores, idsClientes }));
  } catch (error) {
    console.error('No se pudo completar seed_dev:', error.message);
    process.exitCode = 1;
  } finally {
    await pool.end();
  }
})();
