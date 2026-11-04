// Unico lugar que toca el algoritmo de hash. Si bcrypt no compila en tu equipo,
// cambia la siguiente linea por require('bcryptjs') (misma API, mas lento).
const bcrypt = require('bcrypt');

const COSTO = 10;
// Hash falso para igualar el tiempo de respuesta cuando el correo no existe (evita enumerar cuentas)
const HASH_FALSO = bcrypt.hashSync('tamalon-hash-falso', COSTO);

const hashear = (plano) => bcrypt.hash(plano, COSTO);
const comparar = (plano, hash) => bcrypt.compare(plano, hash);
const compararFalso = (plano) => bcrypt.compare(plano, HASH_FALSO).then(() => false);

module.exports = { hashear, comparar, compararFalso };
