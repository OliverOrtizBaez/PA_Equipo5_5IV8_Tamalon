// Pool de conexiones MySQL. Todo el sistema trabaja en UTC (ISO 8601 UTC, ver glosario).
const mysql = require('mysql2/promise');

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
  ssl: process.env.DB_SSL === 'true' ? { rejectUnauthorized: true } : undefined,
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
