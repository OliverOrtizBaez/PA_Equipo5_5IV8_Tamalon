# Tamalon v2 — Plan maestro (modelo central)

Rol: yo coordino. **IA-VSCode** hace backend + web + base de datos. **IA-Android** hace la app móvil.
Ambas trabajan contra el **contrato de la sección 4**. Ninguna cambia el contrato sin avisarme: me pasas su
reporte y yo actualizo este documento. Así no vuelve a pasar que Android llame a rutas que no existen.

---

## 1. Hallazgos del código actual (verificados en el zip)

**Catálogos (por qué no funcionan).** Hay 5 causas independientes:
1. Android llama `GET api/catalogos` (mis catálogos) → **no existe** en el backend (solo `/vendedor/:id`).
2. Android llama `api/productos/...` → **no existe**; el backend expone `/api/catalogos/:id/productos` y `/api/catalogos/productos/:id`.
3. Android envía `estado = "Activo"`; la BD solo acepta `'Visible'/'Oculto'` → el INSERT falla por el CHECK.
4. `POST /api/catalogos` responde `{mensaje, id_catalogo}` pero Retrofit espera un `CatalogoDto` completo.
5. `CatalogoRepository` guarda en **Room local** (no en el servidor): lo que crea el vendedor no lo ve ningún cliente.
   Hay que elegir una sola fuente de verdad: **el servidor** (Room solo como caché si se quiere).

**Ubicación.** Android ya usa `LocationManager`, pero solo dentro de `MapaActivity` (si cierras la pantalla se
deja de reportar), cada 15 s, y el servidor solo guarda el último punto con vigencia de 60 s. No hay historial,
por eso hoy es imposible dar estadísticas.

**Premium.** Hoy: Cliente Premium (favoritos) y Vendedor Premium (más catálogos/productos/rutas), 30 días con
renovación y un job que degrada al vencer. Todo eso se reemplaza por Premium vitalicio solo de vendedor.

**Admin → Usuarios.** `GET /api/admin/usuarios` exige un texto de búsqueda; sin él devuelve error de validación.
Debe listar todos en orden alfabético y la búsqueda pasa a ser opcional.

**Orientación a tamales.** Textos en `index.html`, `terminos_condiciones.html`, layouts `activity_map/list/catalog_products`,
listas de categorías en `MapaActivity`, `ListaVendedoresActivity`, `ProductoFormActivity`, y un solo ícono de vendedor
(`ic_vendedor_mapa.xml` con una olla de tamales).

**Cosas que conviene arreglar aparte (te las marco, no las hice):**
- `terminos_condiciones.html` línea 159 contiene una cláusula de broma ("matar a su hijo primogénito…"). Quítala antes de entregar.
- El zip incluye `.env` con credenciales (aunque está en `.gitignore`). Si el zip circula, rota `DB_PASSWORD`, `JWT_SECRET` y llaves de EmailJS.
- Hay carpetas `* - copia` en `js/`; el código vivo no las usa. Bórralas para no confundir a las IAs.
- AGP ya está en 9.0.0 en `libs.versions.toml` (compatible con Otter 3). **Android: no subas AGP/Gradle/JDK.**

## 2. Decisiones que tomé (dime si alguna no te gusta)

| Tema | Decisión |
|---|---|
| Normalización | `schema_v2.sql`: 3FN, nombre dividido en `nombre`+`apellidos`, listas en tablas catálogo, Premium = existencia de fila en `premium_vendedor` (sin fecha_corte/renovación). |
| Cliente | Sin plan. **Favoritos libres para todo cliente**, tope de 5 (`LIMITE_FAVORITOS`) se mantiene; si lo quieres ilimitado, es una constante. |
| Vendedor Premium | $50 MXN, pago único, vitalicio. Conserva sus beneficios actuales (3 catálogos, 50 productos, rutas) y **añade estadísticas**. Gratis: 1 catálogo, 10 productos. |
| Tipos de vendedor | Un tipo por vendedor, se elige al registrarse y se puede cambiar en el perfil. Define ícono y categorías de producto. 10 tipos iniciales (ropavejero, limpieza, basura, comida, fruta/verdura, pan/dulces, agua/gas, afilador, plantas, otro). |
| "En ruta" | Deja de ser un texto guardado: es una **jornada abierta** (el vendedor toca "Iniciar recorrido"). Si pasan 60 s sin puntos, el servidor lo trata como fuera de ruta. |
| GPS | `LocationManager` nativo (GPS, con respaldo a red) dentro de un **Foreground Service** con notificación, para que siga reportando con la pantalla apagada. |

## 3. Estadísticas

### Vendedor Premium (pantalla "Mi negocio")
1. **Recorrido**: horas en ruta y km recorridos por semana (de `jornada` + `ubicacion_historial`).
2. **Mejores horas**: visitas a tu perfil/catálogo por hora del día y día de la semana (¿cuándo te buscan más?).
3. **Alcance**: visitas totales, comparativo contra la semana anterior (+/− %).
4. **Fidelidad**: cuántos clientes te tienen en favoritos y cómo cambia por semana.
5. **Productos más vistos** (top 5) y productos sin visitas (candidatos a ajustar precio/foto).
6. **Zonas calientes** (opcional, fase 2): dónde estaba el cliente al consultarte, redondeado a ~110 m, sin identidad.

### Panel de administrador (aprovechando lo ya existente: ingresos, tráfico, suscripciones)
1. **Embudo Premium**: vendedores totales → Premium → % de conversión; ingresos = pagos aprobados × $50, por mes y acumulado.
2. **Usuarios por rol y por tipo de vendedor** (barras) — dice qué giros crecen y cuáles íconos/categorías mejorar.
3. **Vendedores en ruta ahora** y horas pico de actividad (reutiliza `registro_login` + `jornada`).
4. **Altas por mes** y retención simple (vendedores con ≥1 jornada en los últimos 30 días).
5. **Calidad**: reportes por usuario, cuentas suspendidas, vendedores sin catálogo (para invitarlos a completarlo).
6. **Uso de la estadística**: cada tarjeta enlaza a la lista filtrada (p. ej. "vendedores sin catálogo" abre Usuarios filtrado).

## 4. CONTRATO API v2 (fuente de verdad para ambas IAs)

Todas las respuestas de error mantienen el formato actual de `js/utils/errores.js`.
Auth: `Authorization: Bearer <token>` (sin cambios).

**Auth / usuario** (`/api/auth`, `/api/usuarios`)
- `POST /api/auth/registro` body: `{nombre, apellidos, correo, contrasena, telefono?, rol: "Vendedor"|"Cliente", id_tipo_vendedor?}` (obligatorio si Vendedor).
- Respuestas de login / `GET /api/usuarios/me`: `{id_usuario, nombre, apellidos, correo, rol, es_premium, tipo_vendedor?, icono_vendedor?, id_tipo_vendedor?, foto_perfil}`.
  Se **elimina** el campo `rol` compuesto ("Vendedor Premium"); ahora `rol` ∈ Administrador|Vendedor|Cliente y `es_premium` es booleano.
- `GET /api/tipos-vendedor` (público) → `{tipos:[{id_tipo_vendedor, clave, nombre, icono}]}`
- `GET /api/tipos-vendedor/:id/categorias` (público) → `{categorias:[{id_categoria, nombre}]}`

**Premium** (`/api/suscripciones`)
- `GET /api/suscripciones/mia` → `{es_premium, premium_desde, precio: 50.00, moneda: "MXN", pago_demo_habilitado}`
- `POST /api/suscripciones/pagar` (solo Vendedor, sin premium) → crea `pago` de 50.00 y, si queda Aprobado, inserta `premium_vendedor` en una transacción. Responde `{es_premium:true}`. Si ya es premium → 409.
- Se eliminan: renovación, fecha_corte, job de degradación, cualquier ruta/UI de Cliente Premium.

**Catálogos y productos** (`/api/catalogos`) — estado ahora es `visible: boolean` en todo el contrato
- `GET /api/catalogos/mios` (Vendedor) → `{catalogos:[{id_catalogo, nombre, visible, fecha_creacion, total_productos}]}`  ← **nueva**
- `GET /api/catalogos/vendedor/:id` (público, solo visibles) → igual forma.
- `POST /api/catalogos` `{nombre}` → **201** `{id_catalogo, nombre, visible:true, fecha_creacion}`  ← ahora devuelve el objeto
- `PUT /api/catalogos/:id` `{nombre?, visible?}` → `{id_catalogo, nombre, visible}`
- `DELETE /api/catalogos/:id` → 204 (o `{mensaje}`; Android debe aceptar ambos)
- `GET /api/catalogos/:id/productos` → `{productos:[{id_producto, id_catalogo, id_categoria, categoria, nombre, descripcion, precio, imagen, visible}]}`
- `POST /api/catalogos/:id/productos` `{nombre, descripcion, precio, id_categoria, imagen?}` → 201 producto completo
- `PUT /api/catalogos/productos/:id` / `DELETE /api/catalogos/productos/:id`
- Android debe usar **solo estas rutas** (se elimina `api/productos/...`).

**Ubicación y jornada** (`/api/ubicacion`, solo Vendedor salvo lectura)
- `POST /api/ubicacion/jornada/iniciar` → 201 `{id_jornada, inicio}`; si ya hay una abierta, devuelve la misma (200).
- `POST /api/ubicacion/jornada/finalizar` → `{id_jornada, fin, minutos, km}`
- `PUT /api/ubicacion/mia` body `{puntos:[{latitud, longitud, precision_m?, t}]}` (1..20 puntos, `t` = epoch ms del GPS; permite enviar en lote si hubo mala señal).
  Servidor: valida rangos, exige jornada abierta (409 si no), actualiza `ubicacion_actual` con el punto más reciente y guarda en `ubicacion_historial` solo si ≥25 m o ≥60 s desde el último guardado. Responde `{en_ruta:true, recibido:n}`.
- `GET /api/ubicacion/cercanos?lat&lng&radio_km&id_tipo_vendedor?&buscar?` → `{vendedores:[{id_usuario, nombre_completo, foto_perfil, es_premium, id_tipo_vendedor, tipo_vendedor, icono_vendedor, latitud, longitud, distancia_m, ultima_actualizacion}]}`
- `GET /api/ubicacion/vendedores/:id` → `{en_ruta, latitud?, longitud?, ultima_actualizacion?}` (sin cambio de forma)
- Se elimina `PUT /mia/fuera-de-ruta` (reemplazado por finalizar jornada). Un job cierra jornadas sin puntos hace >10 min.

**Estadísticas**
- `GET /api/estadisticas/vendedor?desde&hasta` (Vendedor **Premium**; si no, 403 `{codigo:"PREMIUM_REQUERIDO"}`) →
  `{recorrido:{horas, km, por_dia:[{fecha,horas,km}]}, visitas:{total, previo, por_hora:[24], por_dia_semana:[7]}, favoritos:{total, nuevos}, top_productos:[{id_producto,nombre,vistas}], sin_vistas:[…]}`
- `POST /api/eventos` `{id_vendedor, tipo: "vista_perfil"|"vista_catalogo"|"vista_producto", id_producto?}` (Cliente o anónimo; los favoritos generan su evento en el servidor).
- `GET /api/admin/estadisticas` (Admin) → `{embudo:{vendedores, premium, conversion}, ingresos:{total, por_mes:[{mes,monto}]}, por_rol:[…], por_tipo_vendedor:[…], en_ruta_ahora, altas_por_mes:[…], calidad:{reportes_30d, suspendidas, vendedores_sin_catalogo}}`
- `GET /api/admin/usuarios?pagina=&buscar?&rol?&es_premium?&id_tipo_vendedor?` → **sin `buscar` lista todos**, `ORDER BY nombre, apellidos, id_usuario`, `{usuarios:[{id_usuario, nombre_completo, correo, rol, tipo_vendedor?, es_premium, estado_cuenta}], pagina, total, totalPaginas}`
- `PUT /api/admin/usuarios/:id/premium` `{otorgar: true|false}` reemplaza a `/rol`.

## 5. Orden de ejecución y puntos de control

| Fase | Quién | Entregable | Yo valido |
|---|---|---|---|
| 0 | Tú | Respaldo de BD actual + borrar carpetas `* - copia` | — |
| 1 | IA-VSCode | Ejecutar `schema_v2.sql` en una BD de pruebas; script `scripts/seed_dev.js` con 1 admin, 3 vendedores de tipos distintos, 2 clientes | Reporte con salida del script |
| 2 | IA-VSCode | Backend adaptado al contrato (rutas, servicios, jobs, constantes) + tests `npm test` | Lista de endpoints probados con `curl` |
| 2 | IA-Android | Catálogos corregidos contra el contrato (puede avanzar contra los ejemplos JSON del contrato mientras sale la fase 2) | Reporte de causas corregidas 1–5 |
| 3 | IA-Android | Servicio GPS + jornada + íconos por tipo + Premium vitalicio + pantalla Mi negocio | Checklist de pruebas manuales |
| 3 | IA-VSCode | Panel admin (usuarios alfabético, estadísticas), web sin tamales, íconos SVG por tipo | Capturas / descripción |
| 4 | Ambas | Prueba extremo a extremo (sección 6) | Yo cierro |

**Regla de moderación:** cada IA termina su respuesta con el bloque *REPORTE* de su orden de trabajo. Pégamelo y yo
te resumo en 5 líneas qué hizo, qué falta y si choca con la otra.

## 6. Prueba extremo a extremo (criterio de "terminado")
1. Registrar vendedor "ropavejero" → su ícono propio aparece en el mapa del cliente.
2. Vendedor crea catálogo + producto (categoría del tipo ropavejero) → un cliente lo ve en la app.
3. Vendedor toca "Iniciar recorrido", bloquea el teléfono 2 min, camina → el cliente ve el punto moverse.
4. Vendedor paga $50 → `es_premium=true`, aparece "Mi negocio" con datos; nunca vuelve a pagar ni a degradarse.
5. Cliente no ve nada de Premium; puede guardar favoritos.
6. Admin abre Usuarios → ve **todos** de la A a la Z; Estadísticas muestra conversión e ingresos = pagos × 50.
7. Ninguna pantalla, texto ni categoría menciona tamales.

## 7. Subida de imágenes
- `POST /api/imagenes` requiere `Authorization: Bearer <token>` y acepta cualquier rol autenticado.
- Enviar `multipart/form-data` con un archivo `imagen` y `destino` igual a `productos` o `perfiles`.
- Formatos: JPEG, PNG, WebP o HEIC; se validan MIME y firma binaria. Límite de 8 MiB por archivo y 20 solicitudes por usuario cada 10 minutos.
- La imagen se guarda en `tamalon/productos` o `tamalon/perfiles`, con reducción limitada a 1280 px y calidad/formato automáticos.
- Respuesta exitosa: `201 {"url":"https://res.cloudinary.com/<cloud_name>/..."}`.
- Errores: `400` archivo/campos/formato inválidos; `413` archivo mayor a 8 MiB; `429` límite excedido; `502` fallo de Cloudinary; `503` si `CLOUDINARY_URL` no está configurada.
- Los campos `imagen` y `foto_perfil` solo aceptan URL HTTPS bajo `res.cloudinary.com`, con nombre de cloud válido y hasta 2048 caracteres.
