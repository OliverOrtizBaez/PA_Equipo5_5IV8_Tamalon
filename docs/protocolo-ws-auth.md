# Sesión con JWT y canal WebSocket `/ws/auth`

## Qué cambió
- La sesión ahora es un **JWT (HS256)**. Sigue viajando como `Authorization: Bearer <token>`, y el login HTTP
  (`POST /api/auth/login`) sigue respondiendo `{ token, expira, usuario }`, así que la app móvil actual funciona sin cambios.
- El JWT lleva `sub` (id_usuario), `jti` (id aleatorio de la sesión), `iat`, `exp`. En la tabla `sesion` se guarda el
  **SHA-256 del `jti`** (columna `token`, sin cambios de esquema). Validar = firma correcta **y** fila viva en `sesion`.
  Por eso una sesión se puede revocar al instante borrando la fila. El rol y el estado de la cuenta se leen de la BD
  en cada validación; nunca se confía en los claims.
- Nuevo canal WebSocket `ws(s)://<host>/ws/auth` (mismo servidor y puerto que Express). Sirve para:
  1. **Iniciar sesión** desde el panel web (`login.html`), con caída automática al login HTTP si el WebSocket no abre.
  2. **Expulsar en vivo**: el servidor empuja `session_revoked` en el momento en que se suspende la cuenta, se cambia la
     contraseña, se cierra sesión en otra pestaña o vence el token. Ya no hay que esperar a la siguiente petición.

## Variables de entorno
- `JWT_SECRET` (obligatoria, mínimo 32 caracteres). Si falta, el servidor no arranca.
  Generar: `node -e "console.log(require('crypto').randomBytes(48).toString('hex'))"`
  Ponerla en `.env` **y** en las variables de entorno de Render. Cambiarla invalida todas las sesiones.
- `WS_REVALIDAR_MS` (opcional, 60000): cada cuánto la red de seguridad revisa en lote las conexiones abiertas.
- `CORS_ORIGIN` (ya existía): también filtra el `Origin` del WebSocket. Las apps nativas no mandan `Origin` y pasan.

## Protocolo (un objeto JSON por frame de texto, máx. 4 KB)
Cliente → servidor
| Mensaje | Respuesta |
|---|---|
| `{"type":"login","correo","contrasena","zona_horaria"?}` | `{"type":"login_ok","token","expira","usuario"}` o `{"type":"login_error","status":401\|403\|429\|500,"mensaje"}` |
| `{"type":"auth","token"}` | `{"type":"auth_ok","expira","usuario"}` o `{"type":"auth_error","status","mensaje"}` (401/403 cierran con 4401) |
| `{"type":"logout"}` | `{"type":"logout_ok"}` y cierre normal (1000) |
| `{"type":"ping"}` | `{"type":"pong"}` |

Servidor → cliente (sin que lo pida): `{"type":"session_revoked","motivo","mensaje"}` y cierre 4001.
`motivo`: `suspendido` · `contrasena_cambiada` · `logout` · `expirada` · `sesion_invalida`.

Códigos de cierre: `4000` protocolo inválido · `4001` sesión revocada · `4401` no autorizado · `4408` no se autenticó en 15 s ·
`4429` demasiados intentos en la conexión (máx. 5) · `1009` mensaje > 4 KB · `1011` error interno (reintentable).
**No reintentar** tras 4000, 4001, 4401, 4408, 4429.

Las reglas de login son las mismas por HTTP y por WebSocket (misma función `sesionesService.iniciarSesion`):
mismo mensaje para correo inexistente y contraseña incorrecta, bloqueo tras 5 fallos en 15 min (RF2.3b), 403 si la
cuenta está suspendida.

## Dónde está cada cosa
- `js/utils/jwt.js` — firma/verifica HS256 con `crypto` (sin dependencias; rechaza `alg:none`).
- `js/services/sesionesService.js` — `iniciarSesion`, `validarToken`, `cerrarSesion`, `revocarUsuario`, `revalidarConexiones`.
- `js/ws/authSocket.js` — servidor del canal (protocolo, límites, latido cada 30 s, expiración, revalidación).
- `js/ws/hub.js` — registro en memoria de conexiones autenticadas y envío de `session_revoked`.
- `html/sesion-ws.js` — cliente del panel web (`TamalonSesion.login` / `TamalonSesion.escuchar`).
- Disparan la expulsión: `adminController` (suspender), `authController.restablecerContrasena`, `logout`.

## Límites conocidos
- El registro de conexiones vive en la memoria de **un** proceso. Con varias instancias del servidor haría falta Redis
  pub/sub; mientras tanto la revalidación periódica garantiza la expulsión en ≤ 60 s.
- El token dura 7 días (`SESION_DIAS`) y el panel lo guarda en `localStorage` (como antes). Una mejora futura es un
  access token corto + refresh por el mismo WebSocket.
- Al desplegar esta versión se cierran las sesiones existentes una vez (los tokens viejos no son JWT).

## Pruebas
`npm install` y luego `npm test` (47 pruebas: JWT, login HTTP/WS, expulsión en vivo, límites, cliente web). Usan una BD
simulada en memoria; no necesitan MySQL. Una prueba (timeout de 15 s sin autenticar) está omitida por lentitud.
