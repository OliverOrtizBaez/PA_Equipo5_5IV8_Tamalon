// Tareas periodicas en el mismo proceso (sin dependencias).
const { pool, transaccion } = require('../config/db');
const { ESTADOS_CUENTA, RETENCION_LOGIN_MESES } = require('../config/constantes');

async function reactivarSuspensionesVencidas() {
  await transaccion(async (conn) => {
    const [vencidas] = await conn.query(
      'SELECT id_usuario FROM suspension WHERE fin <= ? FOR UPDATE',
      [new Date()]
    );
    if (!vencidas.length) return;
    const ids = vencidas.map((fila) => fila.id_usuario);
    await conn.query(
      `UPDATE usuario
          SET id_estado_cuenta = (SELECT id_estado_cuenta FROM estado_cuenta WHERE nombre = ?)
        WHERE id_usuario IN (?)`,
      [ESTADOS_CUENTA.ACTIVA, ids]
    );
    await conn.query('DELETE FROM suspension WHERE id_usuario IN (?)', [ids]);
  });
}

async function limpiezaPeriodica() {
  const ahora = new Date();
  await pool.query('DELETE FROM sesion WHERE fecha_expiracion < ?', [ahora]);
  await pool.query(
    'DELETE FROM recuperacion_pass WHERE fecha_expiracion < ?',
    [new Date(ahora.getTime() - 24 * 3600 * 1000)]
  );
  await pool.query('DELETE FROM registro_login WHERE fecha < DATE_SUB(?, INTERVAL ? MONTH)', [
    ahora, RETENCION_LOGIN_MESES,
  ]);
}

async function cerrarJornadasSinPuntos() {
  const ahora = new Date();
  const limite = new Date(ahora.getTime() - 10 * 60 * 1000);
  const [resultado] = await pool.query(
    `UPDATE jornada j
        SET fin = ?
      WHERE j.fin IS NULL
        AND COALESCE(
          (SELECT MAX(uh.registrado) FROM ubicacion_historial uh WHERE uh.id_jornada = j.id_jornada),
          j.inicio
        ) < ?`,
    [ahora, limite]
  );
  if (resultado.affectedRows) {
    console.log(`[job:jornada] ${resultado.affectedRows} jornada(s) cerrada(s) por falta de puntos`);
  }
}

async function limpiarHistorialUbicaciones() {
  const [resultado] = await pool.query(
    'DELETE FROM ubicacion_historial WHERE registrado < DATE_SUB(?, INTERVAL 90 DAY)',
    [new Date()]
  );
  if (resultado.affectedRows) {
    console.log(`[job:ubicacion] ${resultado.affectedRows} punto(s) con más de 90 días eliminados`);
  }
}

function programar(nombre, ms, fn) {
  const ejecutar = () => fn().catch((err) => console.error(`[job:${nombre}]`, err.message));
  ejecutar();
  setInterval(ejecutar, ms).unref();
}

function iniciarJobs() {
  programar('suspensiones', 30 * 1000, reactivarSuspensionesVencidas);
  programar('limpieza', 60 * 60 * 1000, limpiezaPeriodica);
  programar('jornadas', 60 * 1000, cerrarJornadasSinPuntos);
  programar('historial-ubicaciones', 24 * 60 * 60 * 1000, limpiarHistorialUbicaciones);
}

module.exports = {
  iniciarJobs, reactivarSuspensionesVencidas, limpiezaPeriodica,
  cerrarJornadasSinPuntos, limpiarHistorialUbicaciones,
};
