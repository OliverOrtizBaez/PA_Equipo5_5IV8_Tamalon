// Reglas RV. OJO: la tabla RV del documento no venia completa; estas reglas se infirieron de los
// ejemplos de los RF. Ajustalas aqui y se aplican en todo el sistema.
const EMAIL_RE = /^[^\s@]+@[^\s@]+\.[^\s@]+$/;
const NOMBRE_RE = /^\p{L}[\p{L} .'’-]*$/u;       // letras, espacios, punto, apostrofe y guion; sin digitos

const REGLAS = {
  correo: {                                       // RV-01
    etiqueta: 'Correo',
    regla: 'correo válido de hasta 100 caracteres',
    ok: (v) => typeof v === 'string' && v.trim().length > 0 && v.trim().length <= 100 && EMAIL_RE.test(v.trim()),
  },
  contrasena: {                                   // RV-02
    etiqueta: 'Contraseña',
    regla: 'de 8 a 64 caracteres, con al menos una letra y un número',
    ok: (v) =>
      typeof v === 'string' && v.length >= 8 && v.length <= 64 &&
      Buffer.byteLength(v, 'utf8') <= 72 &&       // limite real de bcrypt
      /\p{L}/u.test(v) && /\d/.test(v),
  },
  nombre: {                                       // RV-03
    etiqueta: 'Nombre completo',
    regla: 'de 2 a 100 letras (sin números)',
    ok: (v) => typeof v === 'string' && v.trim().length >= 2 && v.trim().length <= 100 && NOMBRE_RE.test(v.trim()),
  },
  telefono: {                                     // RV-04
    etiqueta: 'Teléfono',
    regla: '10 dígitos numéricos',
    ok: (v) => typeof v === 'string' && /^\d{10}$/.test(v),
  },
  busqueda: {                                     // RV-14
    etiqueta: 'Búsqueda',
    regla: 'de 3 a 100 caracteres',
    ok: (v) => typeof v === 'string' && v.trim().length >= 3 && v.trim().length <= 100,
  },
  dias: {                                         // RV-16
    etiqueta: 'Días de suspensión',
    regla: 'número entero de 1 a 365',
    ok: (v) => Number.isInteger(v) && v >= 1 && v <= 365,
  },
  fecha: {                                        // RV-15
    etiqueta: 'Fecha',
    regla: 'fecha válida con formato AAAA-MM-DD',
    ok: (v) => {
      if (typeof v !== 'string' || !/^\d{4}-\d{2}-\d{2}$/.test(v)) return false;
      const d = new Date(`${v}T00:00:00Z`);
      return !Number.isNaN(d.getTime()) && d.toISOString().slice(0, 10) === v;
    },
  },
};

const mensajeInvalido = (etiqueta, regla) => `Dato inválido en ${etiqueta}: ${regla}`;

/**
 * validar([{ campo: 'correo', valor }, { campo: 'contrasena', valor, etiqueta?: 'Nueva contraseña' }])
 * Devuelve el arreglo de mensajes RF25.1 (vacio si todo es valido).
 */
function validar(items) {
  const errores = [];
  for (const { campo, valor, etiqueta } of items) {
    const r = REGLAS[campo];
    if (!r.ok(valor)) errores.push(mensajeInvalido(etiqueta || r.etiqueta, r.regla));
  }
  return errores;
}

/** Contrasena + confirmacion (RV-02). Devuelve mensajes RF25.1. */
function validarContrasenaConConfirmacion(contrasena, confirmacion) {
  const errores = validar([{ campo: 'contrasena', valor: contrasena }]);
  if (errores.length === 0 && contrasena !== confirmacion) {
    errores.push(mensajeInvalido('Confirmar contraseña', 'debe coincidir con la contraseña'));
  }
  return errores;
}

const normalizarCorreo = (c) => String(c).trim().toLowerCase();

/** Entero positivo desde un parametro de ruta/consulta; null si no es valido. */
function entero(valor, { min = 1, max = 2147483647 } = {}) {
  if (typeof valor === 'number' && Number.isInteger(valor)) return valor >= min && valor <= max ? valor : null;
  if (typeof valor !== 'string' || !/^\d+$/.test(valor)) return null;
  const n = Number(valor);
  return n >= min && n <= max ? n : null;
}

module.exports = { REGLAS, validar, validarContrasenaConConfirmacion, mensajeInvalido, normalizarCorreo, entero };
