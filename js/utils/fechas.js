const { ZONA_NEGOCIO, OFFSET_ZHN } = require('../config/constantes');

const MIN = 60 * 1000;
const DIA = 24 * 60 * MIN;

const sumarMinutos = (d, n) => new Date(d.getTime() + n * MIN);
const sumarDias = (d, n) => new Date(d.getTime() + n * DIA);

/** Identificador IANA valido (RF2.4). Rechaza offsets tipo "+05:00" y cualquier texto raro. */
function zonaValida(tz) {
  if (typeof tz !== 'string' || tz.length === 0 || tz.length > 50) return false;
  if (!/^(UTC|[A-Za-z]+(?:\/[A-Za-z0-9_+-]+)+)$/.test(tz)) return false;
  try {
    new Intl.DateTimeFormat('en-US', { timeZone: tz });
    return true;
  } catch (_) {
    return false;
  }
}

function partes(date, tz, opciones) {
  const fmt = new Intl.DateTimeFormat('en-GB', { timeZone: tz, hourCycle: 'h23', ...opciones });
  const obj = {};
  for (const p of fmt.formatToParts(date)) obj[p.type] = p.value;
  return obj;
}

/** 'YYYY-MM-DD' del instante en la zona dada (por defecto ZH-N). */
function fechaEnZona(date, tz = ZONA_NEGOCIO) {
  const p = partes(date, tz, { year: 'numeric', month: '2-digit', day: '2-digit' });
  return `${p.year}-${p.month}-${p.day}`;
}

/** Hora (0-23) del instante en la zona dada. */
function horaEnZona(date, tz) {
  return Number(partes(date, tz, { hour: '2-digit' }).hour);
}

/** 'YYYY-MM-DD' + n dias, en calendario (sin zona). */
function sumarDiasFecha(fechaStr, n) {
  const d = new Date(`${fechaStr}T00:00:00Z`);
  d.setUTCDate(d.getUTCDate() + n);
  return d.toISOString().slice(0, 10);
}

/** DD/MM/AAAA HH:MM:SS (o HH:MM) en la zona dada. */
function formatoFechaHora(date, tz = ZONA_NEGOCIO, { segundos = true } = {}) {
  const p = partes(date, tz, {
    year: 'numeric', month: '2-digit', day: '2-digit',
    hour: '2-digit', minute: '2-digit', second: '2-digit',
  });
  const hora = `${p.hour}:${p.minute}${segundos ? `:${p.second}` : ''}`;
  return `${p.day}/${p.month}/${p.year} ${hora}`;
}

/** Limites inclusivos de un rango de fechas evaluado en ZH-N (UTC-6, sin DST). */
const inicioDiaZHN = (fechaStr) => new Date(`${fechaStr}T00:00:00.000${OFFSET_ZHN}`);
const finDiaZHN = (fechaStr) => new Date(`${fechaStr}T23:59:59.999${OFFSET_ZHN}`);

module.exports = {
  MIN, DIA, sumarMinutos, sumarDias, zonaValida, fechaEnZona, horaEnZona,
  sumarDiasFecha, formatoFechaHora, inicioDiaZHN, finDiaZHN,
};
