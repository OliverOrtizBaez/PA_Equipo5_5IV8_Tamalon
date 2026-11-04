// Crea el primer Administrador (RF1.3: "se crea por carga inicial de datos, fuera de la app").
// Uso:  node scripts/crearAdminInicial.js     (lee ADMIN_NOMBRE, ADMIN_CORREO y ADMIN_PASS del .env)
require('dotenv').config();
const { pool } = require('../js/config/db');
const { crearCuenta } = require('../js/services/cuentasService');
const { ROLES } = require('../js/config/constantes');

(async () => {
  try {
    const cuenta = await crearCuenta({
      nombre_completo: process.env.ADMIN_NOMBRE,
      correo: process.env.ADMIN_CORREO,
      contrasena: process.env.ADMIN_PASS,
      confirmar_contrasena: process.env.ADMIN_PASS,
    }, ROLES.ADMIN);
    console.log(`Administrador creado: id ${cuenta.id_usuario} (${cuenta.correo}). Borra ADMIN_PASS del .env.`);
  } catch (err) {
    console.error('No se pudo crear el administrador:', err.errores || err.message);
    process.exitCode = 1;
  } finally {
    await pool.end();
  }
})();
