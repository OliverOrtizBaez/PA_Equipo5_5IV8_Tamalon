// Pruebas unitarias del JWT propio (utils/jwt.js). Ejecutar: npm test
process.env.JWT_SECRET = 'prueba-secreto-de-al-menos-32-caracteres!!';
const test = require('node:test');
const assert = require('node:assert/strict');
const crypto = require('crypto');
const { firmarJwt, verificarJwt, ErrorJwt } = require('../js/utils/jwt');

const enUnMinuto = () => new Date(Date.now() + 60_000);
const b64 = (o) => Buffer.from(JSON.stringify(o)).toString('base64url');

test('firma y verifica un JWT valido', () => {
  const t = firmarJwt({ idUsuario: 7, jti: 'abc' }, enUnMinuto());
  assert.equal(t.split('.').length, 3);
  const r = verificarJwt(t);
  assert.equal(r.idUsuario, 7);
  assert.equal(r.jti, 'abc');
  assert.ok(r.expiraMs > Date.now());
});

test('rechaza un token manipulado (payload cambiado, firma original)', () => {
  const [h, , f] = firmarJwt({ idUsuario: 7, jti: 'abc' }, enUnMinuto()).split('.');
  const falso = b64({ iss: 'tamalon', sub: '1', jti: 'abc', iat: 1, exp: Math.floor(Date.now() / 1000) + 600 });
  assert.throws(() => verificarJwt(`${h}.${falso}.${f}`), ErrorJwt);
});

test('rechaza alg "none" y otros algoritmos', () => {
  const cuerpo = b64({ iss: 'tamalon', sub: '1', jti: 'x', iat: 1, exp: Math.floor(Date.now() / 1000) + 600 });
  assert.throws(() => verificarJwt(`${b64({ alg: 'none', typ: 'JWT' })}.${cuerpo}.`), ErrorJwt);
  assert.throws(() => verificarJwt(`${b64({ alg: 'HS512', typ: 'JWT' })}.${cuerpo}.firma`), ErrorJwt);
});

test('rechaza un token firmado con otro secreto', () => {
  const datos = `${b64({ alg: 'HS256', typ: 'JWT' })}.${b64({ iss: 'tamalon', sub: '1', jti: 'x', iat: 1, exp: Math.floor(Date.now() / 1000) + 600 })}`;
  const firma = crypto.createHmac('sha256', 'otro-secreto-distinto-de-32-caracteres!!').update(datos).digest('base64url');
  assert.throws(() => verificarJwt(`${datos}.${firma}`), ErrorJwt);
});

test('rechaza un token vencido', () => {
  const t = firmarJwt({ idUsuario: 1, jti: 'x' }, new Date(Date.now() - 5_000));
  assert.throws(() => verificarJwt(t), /expirado/);
});

test('rechaza basura, vacios, no-strings y tokens enormes', () => {
  for (const malo of ['', 'abc', 'a.b', 'a.b.c', '....', null, undefined, 123, {}, 'x'.repeat(5000)]) {
    assert.throws(() => verificarJwt(malo), ErrorJwt);
  }
});

test('rechaza claims incompletos aunque la firma sea valida', () => {
  const datos = `${b64({ alg: 'HS256', typ: 'JWT' })}.${b64({ iss: 'tamalon', sub: 'no-numero', jti: 'x', exp: Math.floor(Date.now() / 1000) + 600 })}`;
  const firma = crypto.createHmac('sha256', process.env.JWT_SECRET).update(datos).digest('base64url');
  assert.throws(() => verificarJwt(`${datos}.${firma}`), ErrorJwt);
});

test('sin JWT_SECRET (o muy corto) falla con un mensaje claro', () => {
  const original = process.env.JWT_SECRET;
  process.env.JWT_SECRET = 'corto';
  try {
    assert.throws(() => firmarJwt({ idUsuario: 1, jti: 'x' }, enUnMinuto()), /JWT_SECRET/);
  } finally {
    process.env.JWT_SECRET = original;
  }
});
