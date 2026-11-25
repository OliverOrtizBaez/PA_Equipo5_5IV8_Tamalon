// Errores controlados: el manejador global los convierte en { mensaje } con el status indicado.
class ErrorHttp extends Error {
  constructor(status, mensaje, extra = {}) {
    super(mensaje);
    this.name = 'ErrorHttp';
    this.status = status;
    this.extra = extra;
  }
}

// RF25.1: «Dato inválido en [campo]: [regla]», todos los campos a la vez.
class ErrorValidacion extends ErrorHttp {
  constructor(errores) {
    super(400, errores.join('\n'), { errores });
    this.name = 'ErrorValidacion';
  }
}

const asyncHandler = (fn) => (req, res, next) => Promise.resolve(fn(req, res, next)).catch(next);

module.exports = { ErrorHttp, ErrorValidacion, asyncHandler };
