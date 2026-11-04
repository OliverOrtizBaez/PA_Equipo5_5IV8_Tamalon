const crypto = require('crypto');

// Token aleatorio criptografico (hex). 48 bytes = 96 caracteres (RF3.1 pide >= 32).
const generarToken = (bytes = 48) => crypto.randomBytes(bytes).toString('hex');

// En la BD solo se guarda el hash: si se filtra la tabla, los tokens no sirven.
const hashToken = (token) => crypto.createHash('sha256').update(String(token)).digest('hex');

module.exports = { generarToken, hashToken };
