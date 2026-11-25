const express = require('express');
const multer = require('multer');
const cloudinary = require('cloudinary').v2;
const { verificarSesion } = require('../middlewares/autenticacion');
const { ErrorHttp, asyncHandler } = require('../utils/errores');

const TAMANO_MAXIMO = 8 * 1024 * 1024;
const VENTANA_LIMITE_MS = 10 * 60 * 1000;
const MAXIMO_POR_VENTANA = 20;
const TIPOS_PERMITIDOS = new Set([
  'image/jpeg',
  'image/png',
  'image/webp',
  'image/heic',
]);

function firmaValida(buffer, mimetype) {
  if (!Buffer.isBuffer(buffer)) return false;
  if (mimetype === 'image/jpeg') {
    return buffer.length >= 3 && buffer[0] === 0xff && buffer[1] === 0xd8 && buffer[2] === 0xff;
  }
  if (mimetype === 'image/png') {
    return buffer.length >= 8 && buffer.subarray(0, 8).equals(Buffer.from([137, 80, 78, 71, 13, 10, 26, 10]));
  }
  if (mimetype === 'image/webp') {
    return buffer.length >= 12 &&
      buffer.toString('ascii', 0, 4) === 'RIFF' &&
      buffer.toString('ascii', 8, 12) === 'WEBP';
  }
  if (mimetype === 'image/heic') {
    if (buffer.length < 16 || buffer.toString('ascii', 4, 8) !== 'ftyp') return false;
    const tamanoCaja = buffer.readUInt32BE(0);
    if (tamanoCaja < 16 || tamanoCaja > buffer.length) return false;
    const marcas = new Set(['heic', 'heix', 'hevc', 'hevx', 'mif1', 'msf1']);
    if (marcas.has(buffer.toString('ascii', 8, 12))) return true;
    for (let offset = 16; offset + 4 <= Math.min(tamanoCaja, 128); offset += 4) {
      if (marcas.has(buffer.toString('ascii', offset, offset + 4))) return true;
    }
    return false;
  }
  return false;
}

function crearLimitador() {
  const ventanas = new Map();
  return (req, res, next) => {
    const idUsuario = req.usuario?.id;
    if (idUsuario === undefined || idUsuario === null) {
      return next(new ErrorHttp(401, 'Se requiere una sesión válida para subir imágenes'));
    }

    const ahora = Date.now();
    for (const [id, ventana] of ventanas) {
      if (ahora >= ventana.expira) ventanas.delete(id);
    }

    let ventana = ventanas.get(idUsuario);
    if (!ventana || ahora >= ventana.expira) {
      ventana = { cantidad: 0, expira: ahora + VENTANA_LIMITE_MS };
      ventanas.set(idUsuario, ventana);
    }
    if (ventana.cantidad >= MAXIMO_POR_VENTANA) {
      res.set('Retry-After', String(Math.ceil((ventana.expira - ahora) / 1000)));
      return next(new ErrorHttp(429, 'Límite de 20 subidas por usuario cada 10 minutos alcanzado'));
    }
    ventana.cantidad += 1;
    next();
  };
}

function crearRouter({ cloudinaryClient = cloudinary, autenticar = verificarSesion } = {}) {
  const router = express.Router();
  const limitarSubidas = crearLimitador();
  const upload = multer({
    storage: multer.memoryStorage(),
    limits: { fileSize: TAMANO_MAXIMO, files: 1, fields: 1, fieldSize: 32 },
    fileFilter: (_req, file, callback) => {
      if (!TIPOS_PERMITIDOS.has(file.mimetype)) {
        return callback(new ErrorHttp(400, 'Formato inválido: usa JPEG, PNG, WebP o HEIC'));
      }
      callback(null, true);
    },
  }).single('imagen');

  const procesarArchivo = (req, res, next) => {
    upload(req, res, (error) => {
      if (!error) return next();
      if (error instanceof multer.MulterError) {
        if (error.code === 'LIMIT_FILE_SIZE') {
          return next(new ErrorHttp(413, 'La imagen excede el límite de 8 MB'));
        }
        return next(new ErrorHttp(400, 'La solicitud debe incluir un único archivo en el campo imagen'));
      }
      next(error);
    });
  };

  router.post('/', autenticar, limitarSubidas, procesarArchivo, asyncHandler(async (req, res) => {
    if (!req.file) throw new ErrorHttp(400, 'Falta el archivo requerido en el campo imagen');
    if (!firmaValida(req.file.buffer, req.file.mimetype)) {
      throw new ErrorHttp(400, 'El contenido del archivo no coincide con un formato de imagen permitido');
    }

    const carpetas = { productos: 'tamalon/productos', perfiles: 'tamalon/perfiles' };
    const folder = carpetas[req.body?.destino];
    if (!folder) throw new ErrorHttp(400, 'El campo destino debe ser productos o perfiles');
    if (!process.env.CLOUDINARY_URL?.trim()) {
      throw new ErrorHttp(503, 'La subida de imágenes no está configurada en el servidor');
    }

    const resultado = await new Promise((resolve, reject) => {
      const stream = cloudinaryClient.uploader.upload_stream({
        folder,
        transformation: [{ width: 1280, crop: 'limit', quality: 'auto', fetch_format: 'auto' }],
      }, (error, respuesta) => {
        if (error) return reject(error);
        resolve(respuesta);
      });
      stream.end(req.file.buffer);
    }).catch(() => {
      throw new ErrorHttp(502, 'Cloudinary no pudo completar la subida de la imagen');
    });

    if (!resultado?.secure_url) {
      throw new ErrorHttp(502, 'Cloudinary no devolvió una URL segura para la imagen');
    }
    res.status(201).json({ url: resultado.secure_url });
  }));

  return router;
}

const router = crearRouter();
router.crearRouter = crearRouter;
router.firmaValida = firmaValida;
module.exports = router;
