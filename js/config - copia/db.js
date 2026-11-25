// Pool de conexiones MySQL. Todo el sistema trabaja en UTC (ISO 8601 UTC, ver glosario).
const fs = require('fs');
const path = require('path');
const mysql = require('mysql2/promise');

// SSL: verifica el certificado del servidor usando el CA de Aiven.
// Orden: variable DB_SSL_CA (contenido del certificado) o archivo certs/aiven-ca.pem
function configSSL() {
  if (process.env.DB_SSL !== 'true') return undefined;
  let ca;
  if (process.env.DB_SSL_CA) {
    ca = process.env.DB_SSL_CA.replace(/\\n/g, '\n');
  } else {
    const ruta = path.join(__dirname, '..', '..', 'certs', 'aiven-ca.pem');
    if (fs.existsSync(ruta)) ca = fs.readFileSync(ruta);
  }
  return { rejectUnauthorized: true, ca };
}

const pool = mysql.createPool({
  host: process.env.DB_HOST || 'localhost',
  port: Number(process.env.DB_PORT || 3306),
  user: process.env.DB_USER || 'root',
  password: process.env.DB_PASSWORD || '',
  database: process.env.DB_NAME || 'tamalon_db',
  waitForConnections: true,
  connectionLimit: 10,
  charset: 'utf8mb4',
  timezone: 'Z', // los Date de JS se envian y reciben como UTC
  ssl: configSSL(),
});

// Cada conexion nueva opera en UTC (NOW(), CURRENT_TIMESTAMP, columnas TIMESTAMP)
pool.on('connection', (conn) => {
  conn.query("SET time_zone = '+00:00'");
});

/**
 * Ejecuta fn(conn) dentro de una transaccion. Si fn lanza, hace rollback y relanza.
 */
async function transaccion(fn) {
  const conn = await pool.getConnection();
  try {
    await conn.beginTransaction();
    const resultado = await fn(conn);
    await conn.commit();
    return resultado;
  } catch (err) {
    try { await conn.rollback(); } catch (_) { /* ignorar */ }
    throw err;
  } finally {
    conn.release();
  }
}

module.exports = { pool, transaccion };