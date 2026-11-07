// Tareas periodicas en el mismo proceso (sin dependencias).
const { pool } = require('../config/db');
const { ESTADOS_CUENTA, RETENCION_LOGIN_MESES, UBICACION_VIGENCIA_SEG } = require('../config/constantes');

// RF22.2b: reactiva en <= 60 s las suspensiones temporales vencidas (corre cada 30 s)
async function reactivarSuspensionesVencidas() {
  const [r] = await pool.query(
    `UPDATE usuarios SET estado_cuenta = ?, fin_suspension = NULL
      WHERE estado_cuenta = ? AND fin_suspension IS NOT NULL AND fin_suspension <= ?`,
    [ESTADOS_CUENTA.ACTIVA, ESTADOS_CUENTA.SUSPENDIDA_TEMP, new Date()]
  );
  if (r.affectedRows > 0) console.log(`[job] ${r.affectedRows} cuenta(s) reactivada(s) por fin de suspensión`);
}

// Limpieza horaria: sesiones vencidas, enlaces viejos y registros de login > 12 meses (RF20a)
async function limpiezaPeriodica() {
  const ahora = new Date();
  await pool.query('DELETE FROM sesion WHERE fecha_expiracion < ?', [ahora]);
  // los enlaces se conservan 1 dia: RF3.1b cuenta solicitudes de los ultimos 60 min
  await pool.query('DELETE FROM recuperacion_pass WHERE fecha_expiracion < ?', [new Date(ahora.getTime() - 24 * 3600 * 1000)]);
  await pool.query('DELETE FROM registro_login WHERE fech_hrs < DATE_SUB(?, INTERVAL ? MONTH)', [ahora, RETENCION_LOGIN_MESES]);
}

// Marca fuera de ruta a quienes dejaron de enviar su latido de ubicación.
async function expirarUbicaciones() {
  const ahora = new Date();
  const limite = new Date(ahora.getTime() - UBICACION_VIGENCIA_SEG * 1000);
  const [r] = await pool.query(
    `UPDATE ubi_vendedor
        SET disponibilidad = ?
      WHERE disponibilidad = ? AND ultima_actualizacion < ?`,
    ['Fuera de ruta', 'En ruta', limite]
  );
  if (r.affectedRows > 0) console.log(`[job:ubicacion] ${r.affectedRows} vendedor(es) fuera de ruta por falta de señal`);
}

function programar(nombre, ms, fn) {
  const ejecutar = () => fn().catch((err) => console.error(`[job:${nombre}]`, err.message));
  ejecutar();
  setInterval(ejecutar, ms).unref(); // unref: los jobs no impiden que el proceso termine
}

function iniciarJobs() {
  programar('suspensiones', 30 * 1000, reactivarSuspensionesVencidas);
  programar('limpieza', 60 * 60 * 1000, limpiezaPeriodica);
  programar('ubicacion', 30 * 1000, expirarUbicaciones);
}

module.exports = { iniciarJobs, reactivarSuspensionesVencidas, limpiezaPeriodica, expirarUbicaciones };
