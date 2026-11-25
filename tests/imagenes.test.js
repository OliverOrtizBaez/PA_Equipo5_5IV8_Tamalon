const test = require('node:test');
const assert = require('node:assert/strict');
const http = require('node:http');
const path = require('node:path');
const express = require('express');

let queryHandler = async () => { throw new Error('No se esperaba una consulta SQL'); };
const fakePool = { query: (...args) => queryHandler(...args) };
const dbPath = path.resolve(__dirname, '../js/config/db.js');
require.cache[dbPath] = {
  id: dbPath,
  filename: dbPath,
  loaded: true,
  exports: { pool: fakePool, transaccion: (fn) => fn(fakePool) },
};

const imagenes = require('../js/routes/imagenesRoutes');
const usuario = require('../js/Controladores/usuarioController');
const producto = require('../js/Controladores/productoController');
const validaciones = require('../js/utils/validaciones');
const { manejadorErrores } = require('../js/middlewares/errores');

const PNG = Buffer.from([137, 80, 78, 71, 13, 10, 26, 10, 0]);
let servidor;
let base;
let subidas;
let errorCloudinary;
let cloudinaryUrlAnterior;

function cuerpoMultipart({ destino = 'productos', archivo = PNG, mimetype = 'image/png', campo = 'imagen', sinArchivo = false } = {}) {
  const boundary = '----TamalonUploadBoundary';
  const partes = [
    `--${boundary}\r\nContent-Disposition: form-data; name="destino"\r\n\r\n${destino}\r\n`,
  ];
  if (!sinArchivo) {
    partes.push(
      `--${boundary}\r\nContent-Disposition: form-data; name="${campo}"; filename="imagen.bin"\r\nContent-Type: ${mimetype}\r\n\r\n`,
      archivo,
      '\r\n'
    );
  }
  partes.push(`--${boundary}--\r\n`);
  return {
    boundary,
    body: Buffer.concat(partes.map((parte) => Buffer.isBuffer(parte) ? parte : Buffer.from(parte))),
  };
}

async function iniciarServidor({ autenticar } = {}) {
  const cloudinaryClient = {
    uploader: {
      upload_stream(opciones, callback) {
        subidas.push({ opciones, buffer: null });
        const subida = subidas[subidas.length - 1];
        return {
          end(buffer) {
            subida.buffer = Buffer.from(buffer);
            callback(errorCloudinary, errorCloudinary ? undefined : {
              secure_url: 'https://res.cloudinary.com/test-cloud/image/upload/v1/test.png',
            });
          },
        };
      },
    },
  };
  const app = express();
  app.use('/api/imagenes', imagenes.crearRouter({
    cloudinaryClient,
    ...(autenticar ? { autenticar } : {}),
  }));
  app.use(manejadorErrores);
  servidor = http.createServer(app);
  await new Promise((resolve) => servidor.listen(0, '127.0.0.1', resolve));
  base = `http://127.0.0.1:${servidor.address().port}`;
}

async function postImagen(opciones = {}, headers = {}) {
  const { boundary, body } = cuerpoMultipart(opciones);
  const respuesta = await fetch(`${base}/api/imagenes`, {
    method: 'POST',
    headers: {
      'Content-Type': `multipart/form-data; boundary=${boundary}`,
      ...headers,
    },
    body,
  });
  return { status: respuesta.status, cuerpo: await respuesta.json(), headers: respuesta.headers };
}

async function cerrarServidor() {
  if (servidor) await new Promise((resolve) => servidor.close(resolve));
  servidor = null;
}

test.beforeEach(async () => {
  cloudinaryUrlAnterior = process.env.CLOUDINARY_URL;
  delete process.env.CLOUDINARY_URL;
  subidas = [];
  errorCloudinary = null;
  await iniciarServidor({
    autenticar: (req, _res, next) => {
      req.usuario = { id: Number(req.headers['x-test-user'] || 1), tipo: 'Cliente' };
      next();
    },
  });
});

test.afterEach(async () => {
  await cerrarServidor();
  if (cloudinaryUrlAnterior === undefined) delete process.env.CLOUDINARY_URL;
  else process.env.CLOUDINARY_URL = cloudinaryUrlAnterior;
});

test('sube un PNG válido al destino solicitado y devuelve secure_url', async () => {
  process.env.CLOUDINARY_URL = 'cloudinary://test:test@test-cloud';
  const resultado = await postImagen({ destino: 'productos' });

  assert.equal(resultado.status, 201);
  assert.deepEqual(resultado.cuerpo, {
    url: 'https://res.cloudinary.com/test-cloud/image/upload/v1/test.png',
  });
  assert.deepEqual(subidas[0].opciones, {
    folder: 'tamalon/productos',
    transformation: [{ width: 1280, crop: 'limit', quality: 'auto', fetch_format: 'auto' }],
  });
  assert.deepEqual(subidas[0].buffer, PNG);
});

test('comprueba las firmas binarias de JPEG, WebP y HEIC', () => {
  const jpeg = Buffer.from([0xff, 0xd8, 0xff, 0]);
  const webp = Buffer.from('RIFF0000WEBP', 'ascii');
  const heic = Buffer.alloc(16);
  heic.writeUInt32BE(16, 0);
  heic.write('ftypheic', 4, 'ascii');

  assert.equal(imagenes.firmaValida(jpeg, 'image/jpeg'), true);
  assert.equal(imagenes.firmaValida(webp, 'image/webp'), true);
  assert.equal(imagenes.firmaValida(heic, 'image/heic'), true);
  assert.equal(imagenes.firmaValida(jpeg, 'image/png'), false);
});

test('acepta destino perfiles y un cliente autenticado sin exigir rol específico', async () => {
  process.env.CLOUDINARY_URL = 'cloudinary://test:test@test-cloud';
  const resultado = await postImagen({ destino: 'perfiles' }, { 'x-test-user': '42' });
  assert.equal(resultado.status, 201);
  assert.equal(subidas[0].opciones.folder, 'tamalon/perfiles');
});

test('rechaza archivo ausente, MIME no permitido y bytes que no coinciden', async () => {
  process.env.CLOUDINARY_URL = 'cloudinary://test:test@test-cloud';
  assert.equal((await postImagen({ sinArchivo: true })).status, 400);
  assert.equal((await postImagen({ mimetype: 'text/plain' })).status, 400);
  assert.equal((await postImagen({ archivo: Buffer.from('not an image') })).status, 400);
  assert.equal(subidas.length, 0);
});

test('rechaza un destino desconocido', async () => {
  process.env.CLOUDINARY_URL = 'cloudinary://test:test@test-cloud';
  const resultado = await postImagen({ destino: 'usuarios' });
  assert.equal(resultado.status, 400);
  assert.equal(subidas.length, 0);
});

test('responde 413 cuando el archivo supera 8 MB', async () => {
  const resultado = await postImagen({ archivo: Buffer.alloc(8 * 1024 * 1024 + 1) });
  assert.equal(resultado.status, 413);
  assert.match(resultado.cuerpo.mensaje, /8 MB/);
});

test('sin CLOUDINARY_URL responde 503 sin impedir que la ruta cargue', async () => {
  const resultado = await postImagen();
  assert.equal(resultado.status, 503);
  assert.match(resultado.cuerpo.mensaje, /no está configurada/);
  assert.equal(subidas.length, 0);
});

test('Cloudinary falla con 502 sin filtrar el detalle del proveedor', async () => {
  process.env.CLOUDINARY_URL = 'cloudinary://test:test@test-cloud';
  errorCloudinary = new Error('credential-secret-from-cloudinary');
  const resultado = await postImagen();
  assert.equal(resultado.status, 502);
  assert.doesNotMatch(resultado.cuerpo.mensaje, /credential-secret/);
});

test('aplica 20 solicitudes por usuario en una ventana de 10 minutos', async () => {
  for (let i = 0; i < 20; i += 1) {
    assert.equal((await postImagen({ sinArchivo: true })).status, 400);
  }
  const limite = await postImagen({ sinArchivo: true });
  assert.equal(limite.status, 429);
  assert.ok(Number(limite.headers.get('retry-after')) > 0);
});

test('la ruta exige sesión cuando usa el middleware de producción', async () => {
  await cerrarServidor();
  await iniciarServidor();
  const respuesta = await fetch(`${base}/api/imagenes`, { method: 'POST' });
  assert.equal(respuesta.status, 401);
});

test('imagen y foto_perfil solo aceptan URLs Cloudinary HTTPS de hasta 2048 caracteres', async () => {
  const url = 'https://res.cloudinary.com/demo-cloud/image/upload/v1/foto.webp';
  const prefijo = 'https://res.cloudinary.com/demo-cloud/image/upload/';
  const urlMaxima = `${prefijo}${'a'.repeat(2048 - prefijo.length)}`;
  assert.equal(validaciones.esUrlCloudinary(url), true);
  assert.equal(urlMaxima.length, 2048);
  assert.equal(validaciones.esUrlCloudinary(urlMaxima), true);
  assert.equal(validaciones.esUrlCloudinary('https://example.com/foto.webp'), false);
  assert.equal(validaciones.esUrlCloudinary(`https://res.cloudinary.com/demo/${'a'.repeat(2050)}`), false);
  assert.deepEqual(validaciones.validar([{ campo: 'imagen', valor: url }, { campo: 'foto_perfil', valor: url }]), []);
  assert.equal(producto.validarImagen(url), url);
  assert.throws(() => producto.validarImagen('https://example.com/foto.webp'), { status: 400 });

  queryHandler = async () => [{ affectedRows: 1 }];
  const res = {
    statusCode: 200,
    json(body) { this.body = body; return this; },
  };
  let error;
  await usuario.actualizarFotoPerfil(
    { body: { foto_perfil: url }, usuario: { id: 8 } },
    res,
    (err) => { error = err; }
  );
  assert.equal(error, undefined);
  assert.deepEqual(res.body, { mensaje: 'Foto de perfil actualizada' });

  await usuario.actualizarFotoPerfil(
    { body: { foto_perfil: 'https://example.com/foto.webp' }, usuario: { id: 8 } },
    res,
    (err) => { error = err; }
  );
  assert.equal(error.status, 400);
});
