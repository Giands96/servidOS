# FRONTEND_CONTEXT.md — ServidOS (contrato API para Angular)

> Fuente de verdad para el cliente web. Sincronizado **2026-10-11** contra el código real del backend (`main` en `4a5f941`, incluye PR #12, #13 y #14).
> Novedades respecto de la versión 2026-10-02: suscripción gestionada por la plataforma, bloqueo 402 por vencimiento, `/auth/me` con resumen de suscripción, `GET /planes`, `GET /pedidos` con filtros, `GET /cocina/listos`, WebSocket de cocina, cobro abierto a RECEPCION y CAJERO, reembolso que cancela el pedido.
> Guía de migración paso a paso (qué archivo del front tocar): `CAMBIOS_FRONTEND.md` en la raíz del repo. Si difiere de este documento, vale este (ver §10).
> Detalle campo por campo: Swagger en dev (`http://localhost:8080/swagger-ui/index.html`, perfil `dev`).
> Backend: este mismo repo, base `/api/v1`. Doc backend: `PROJECT_CONTEXT.md`.

---

## 1. Base y entorno

- **Base URL dev:** `http://localhost:8080/api/v1` (sin `context-path`, puerto 8080).
- **CORS:** orígenes de `CORS_ALLOWED_ORIGINS` (dev: `http://localhost:4200`), solo sobre `/api/**`, con credenciales. Métodos `GET,POST,PUT,PATCH,DELETE,OPTIONS`; headers `Authorization,Content-Type,X-Requested-With`. No se exponen headers de respuesta: JS cross-origin **no puede leer `Retry-After`**.
- **Cookies:** `secure=false` en dev, `true` en prod.
- **Swagger:** solo perfil `dev`; en prod está apagado. Botón `Authorize` con el JWT (`bearerAuth`). El WebSocket no aparece en Swagger.
- **JSON:** todos los campos en `camelCase` (no hay `snake_case` en ninguna respuesta).
- **Fechas:** el backend corre en `America/Lima`. `createdAt` llega sin zona (`"2026-10-09T12:30:00"`) y **ya está en hora de Lima**: mostrar tal cual, no agregar `Z`. `fechaInicio` / `fechaFin` son `yyyy-MM-dd`. "Hoy" (vencimiento, filtro `fecha`) es el día de Lima. Única excepción: `timestamp` de los errores es un instante UTC.

## 2. Autenticación

### `POST /auth/login` → 200
Body `{ "email", "password" }`. Respuesta `{ "accessToken": "<JWT, 10 min>" }` + cookie `refresh_token` (`HttpOnly`, `SameSite=Lax`, `Path=/api/v1/auth`, 7 días).
Errores: `401` genérico `"Credenciales inválidas"` (no distingue usuario, contraseña ni bloqueo), `422` validación, `429` rate limit.
Bloqueo por intentos: 5 fallos por (email, IP) en 10 min bloquean ese par 5 min (se duplica hasta 60 min); la respuesta sigue siendo el mismo `401`.

### `POST /auth/refresh` → 200
Sin body. Usa la cookie (`withCredentials: true`) y **exige** `X-Requested-With: XMLHttpRequest`; sin header o sin cookie → `401` `"Sesión inválida"`. Rota el refresh en cada llamada; reusar uno viejo revoca toda la familia.
La sesión es **absoluta, no deslizante**: 7 días desde el login aunque se renueve; después hay que volver a iniciar sesión.

### `POST /auth/logout` → 204
Mismo header `X-Requested-With`. Limpia la cookie y revoca **todos** los refresh tokens del usuario (cierra sesión en todos los dispositivos).

### `GET /auth/me` → 200
Requiere `Authorization: Bearer <accessToken>`.

```jsonc
{ "usuarioId": 1, "email": "...", "nombre": "...",
  "restauranteId": 7,              // null en sesiones de plataforma
  "rol": "ADMINISTRADOR",
  "restauranteEstado": "ACTIVO",   // ACTIVO | INACTIVO | null (plataforma)
  "suscripcion": {                 // null en plataforma o si el tenant no tiene suscripción vigente
    "planId": 1, "nombrePlan": "Estándar", "monto": 34.90, "moneda": "PEN",
    "estado": "ACTIVA",            // ACTIVA | CANCELADA
    "fechaFin": "2026-11-10",
    "diasRestantes": 30,           // negativo si ya venció
    "vencida": false } }
```

Llamarlo al arrancar y en cada sección del panel: con `suscripcion` se arma el banner de vencimiento sin otra petición. No trae `apellido`.

### Rate limit
5 peticiones por minuto por IP, contador **compartido** entre `/auth/login` y `/auth/refresh` (cuenta todas, exitosas o no). La 6ª responde `429` con cuerpo de **texto plano** (no `ErrorResponse`).

### Interceptor sugerido
1. Adjuntar `Bearer` a todo `/api/**`.
2. Ante `401` (no en `/auth/*`): un `refresh` y reintentar una vez; si falla → login. Ver la advertencia de §10 sobre token vencido.
3. `refresh` y `logout` con `withCredentials: true` + `X-Requested-With: XMLHttpRequest`.
4. **Excepción:** `POST /restaurantes/actual/suscripcion/cancelar` responde `401` cuando la contraseña es incorrecta. Ese `401` no debe disparar refresh ni logout: mostrar "contraseña incorrecta".

## 3. Reglas de oro del tenant

- **El `restauranteId` nunca va en el JSON.** Sale del JWT en el backend.
- **Roles (vocabulario cerrado):**
  - Plataforma: `SUPERADMIN > ADMIN > MODERADOR`.
  - Restaurante: `ADMINISTRADOR, RECEPCION, COCINERO, CAJERO, MESERO, REPARTIDOR`.
- **Flujo operativo:** RECEPCION crea el pedido → lo confirma (`PENDIENTE → EN_PREPARACION`) → COCINA lo ve en cola y lo marca listo → RECEPCION lo entrega. Detalle en `Flujo - Recepcion Cocina.md`.
- **`402` ≠ `403`:** `403` = el rol no alcanza; `402` = el restaurante está bloqueado por suscripción (§5). El `402` se evalúa **antes** que los roles.

### Matriz de permisos

| Recurso | Acción | Roles |
|---|---|---|
| Pedidos | `GET /pedidos` | ADMINISTRADOR, RECEPCION, CAJERO |
| Pedidos | crear, confirmar, cambiar estado | ADMINISTRADOR, RECEPCION |
| Pagos | `POST /pagos` (cobrar) | ADMINISTRADOR, RECEPCION, CAJERO |
| Pagos | reembolso | ADMINISTRADOR, RECEPCION |
| Cocina | cola, listos, marcar listo, WebSocket | ADMINISTRADOR, RECEPCION, COCINERO |
| Catálogo | crear/editar producto, crear categoría | ADMINISTRADOR |
| Catálogo | `PATCH /productos/{id}/estado` | ADMINISTRADOR, COCINERO |
| Catálogo, planes, `/restaurantes/actual*` | lecturas | cualquier sesión autenticada |
| Usuarios | crear, eliminar | ADMINISTRADOR |
| Usuarios | `PATCH /usuarios/{id}/rol` | ADMINISTRADOR, SUPERADMIN, ADMIN |
| Suscripción | cancelar (la propia) | ADMINISTRADOR |
| Plataforma | `POST /restaurantes` | SUPERADMIN, ADMIN |
| Plataforma | dashboard, detalle, estado, plan, renovar | SUPERADMIN |

`MESERO` y `REPARTIDOR` solo tienen lecturas. `MODERADOR` solo lecturas de plataforma.

## 4. Errores

```json
{ "timestamp": "2026-10-11T03:00:00Z", "status": "400 Bad Request", "message": "...", "path": "/api/v1/...", "traceID": "a1b2c3d4" }
```

| Código | Cuándo | Acción UI sugerida |
|---|---|---|
| 400 | Regla de negocio **y recurso inexistente** (`"Pedido no encontrado"`, `"Producto no encontrado"`, `"El restaurante no existe"`) | Mostrar `message` (viene en español) |
| 401 | Refresh inválido, falta `X-Requested-With`, contraseña incorrecta al cancelar suscripción | Refresh → login (salvo la excepción de §2) |
| 402 | Restaurante bloqueado por suscripción (§5) | Paywall / aviso, mostrar `message` |
| 403 | Rol insuficiente, jerarquía insuficiente, operación cross-tenant | Vista "sin permiso" |
| 409 | `"El email ya existe"`, eliminar al último ADMINISTRADOR | Mostrar `message` |
| 422 | Validación de campos; `message` = `"campo: mensaje; campo: mensaje"` | Marcar campos |
| 429 | Rate limit de login/refresh (texto plano) | Esperar ~60 s |
| 500 | Error no controlado, `message` fijo `"An unexpected error occurred."` | Mensaje genérico + `traceID` |

**El backend no devuelve `404`.** Todo "no encontrado" llega como `400` con mensaje.

## 5. Suscripciones y el 402

**Cuándo se bloquea.** Solo escrituras (las lecturas `GET` siempre pasan) y solo si se cumple alguna condición, en este orden:

| Condición | `message` del 402 |
|---|---|
| Restaurante `INACTIVO` | `"Restaurante suspendido: contactá a la plataforma para regularizar tu suscripción"` |
| Sin suscripción vigente | `"Restaurante sin suscripción: contactá a la plataforma…"` |
| `fechaFin < hoy`, estado `ACTIVA` | `"Tu plan venció el YYYY-MM-DD: contactá a la plataforma…"` |
| `fechaFin < hoy`, estado `CANCELADA` | `"Tu suscripción cancelada terminó el YYYY-MM-DD: contactá a la plataforma…"` |

- **El vencimiento por fecha sí bloquea.** Ya no es informativo.
- **`CANCELADA` no bloquea por sí sola:** el restaurante opera hasta `fechaFin` inclusive. Con `CANCELADA` vigente, mostrar un aviso ("Tu suscripción termina el {fechaFin}"), no el paywall.
- **No hay token limitado:** un restaurante bloqueado inicia sesión y refresca normal. El front lo detecta por `/auth/me` (`restauranteEstado`, `suscripcion.vencida`) y por el `402`.
- Guard recomendado: bloquear si `restauranteEstado === 'INACTIVO'`, `suscripcion === null` o `suscripcion.vencida`. El `402` sigue siendo la fuente de verdad.

**Escrituras que pasan aunque esté bloqueado** (cerrar pedidos en curso): `PATCH /pedidos/{id}/estado`, `POST /pedidos/{id}/confirmar`, `POST /cocina/pedidos/{id}/listo`, `POST /pagos`, `POST /pagos/{id}/reembolso`. Sin barra final en la URL. Todo lo demás (crear pedido, catálogo, usuarios, cancelar suscripción) da `402`. No ocultar esas acciones en pedidos existentes.

**Rutas del front sin `subscriptionGuard`:** `/cocina` completa (cola, listos y marcar listo funcionan bloqueado) y las vistas de pedidos y caja en lo que respecta a pedidos ya creados. El guard solo debe impedir crear pedidos y entrar a catálogo, usuarios y configuración.

**Quién gestiona la suscripción.** La plataforma, no el tenant:

- El tenant **no renueva ni cambia de plan**. El paywall muestra el plan actual y un texto de contacto, sin botón de renovar.
- El tenant solo puede **cancelar** (= no renovar) con su contraseña.
- Renovar, cambiar plan y activar/desactivar el restaurante son acciones del SUPERADMIN (§7 Plataforma).

## 6. Paginación (Spring `Page`)

Paginados: `GET /productos` (default `size=20`), `GET /categorias`, `GET /restaurantes` (default `size=10`), `GET /pedidos` (default `size=20`, máx. 100).

```json
{ "content": [ ... ], "totalElements": 95, "totalPages": 10, "size": 10, "number": 0, "first": true, "last": false }
```

Usar solo estos campos (la respuesta puede traer otros). `GET /pedidos` **no acepta `sort`**: orden fijo del más nuevo al más viejo. **No paginados** (array directo): `GET /planes`, `GET /cocina/cola`, `GET /cocina/listos`.

## 7. Endpoints por pantalla

### Plataforma (sesión sin tenant)
- `POST /restaurantes` 201 — `SUPERADMIN, ADMIN`. `{ slug, nombre, direccion?, planId, demo?, demoDias? }`. `slug`: 3–100, `^[a-z0-9-]+$`. `demo=true` exige `demoDias` 1–30 y crea suscripción con `monto=0`; `demo=false` crea una de 30 días al precio del plan. Responde `RestauranteResponse` (sin la suscripción). **No crea el usuario ADMINISTRADOR del restaurante.**
- `GET /restaurantes?page=&size=` 200 — `SUPERADMIN`. Filas `{ restauranteId, slug, nombre, direccion, estado, suscripcion: { suscripcionId, planId, nombrePlan, estado, fechaInicio, fechaFin } | null }`.
- `GET /restaurantes/{id}` y `GET /restaurantes/{id}/suscripcion` 200 — `SUPERADMIN`.
- `PATCH /restaurantes/{id}/estado` 200 — `SUPERADMIN`. Body `{ estado: ACTIVO|INACTIVO }`. `400` si ya está en ese estado. No toca la suscripción.
- `PATCH /restaurantes/{id}/plan` 200 — `SUPERADMIN`. Body `{ nuevoPlanId }` (sin `confirmado` ni `password`; la confirmación la pide el panel). Exige suscripción actual `ACTIVA` (`400` `"La suscripción no está activa"`). Cancela la actual y las programadas, crea una nueva de hoy a hoy + 1 mes al precio del plan. No reactiva un restaurante `INACTIVO`.
- `POST /restaurantes/{id}/suscripcion/renovar` 200 — `SUPERADMIN`. Body opcional `{ planId }`, obligatorio solo si el restaurante nunca tuvo suscripción (`400` `"El plan es obligatorio para la primera suscripción"`). Si la actual sigue vigente, la nueva arranca el día siguiente a su `fechaFin`; si no, hoy. Dura un mes. Deja `ACTIVO` a un restaurante `INACTIVO`.

Los tres últimos responden `SuscripcionResponse` (salvo `estado`, que responde `RestauranteResponse`).

### Restaurante actual
- `GET /restaurantes/actual` 200 — autenticado. `{ restauranteId, slug, nombre, direccion, estado }`. Sesión de plataforma → `400` `"El restaurante es obligatorio"`.
- `GET /restaurantes/actual/suscripcion` 200 — autenticado. Devuelve la suscripción **vigente hoy** (no una renovación programada). Sin ninguna → `400` `"No se encontró suscripción para el restaurante"`.
- `POST /restaurantes/actual/suscripcion/cancelar` 200 (vacío) — solo `ADMINISTRADOR`. Body `{ password }`. Contraseña vacía → `422`; incorrecta → `401` `"Credenciales inválidas"`; suscripción no `ACTIVA` → `400`. Queda `CANCELADA` y el restaurante opera hasta `fechaFin`.
- `GET /planes` 200 — autenticado. Array de planes activos ordenados por precio: `{ planId, nombre, precio, descripcion, estado, moneda }`. Hoy existe un único plan, "Estándar" (34.90 PEN).

### Catálogo
- `POST /productos` 201 — `{ nombre, descripcion?, categoriaId?, precio, tiempoPreparacion?, estado, imagenUrl? }`. `estado` es obligatorio (`400` si falta). Nombre duplicado → `400`.
- `PUT /productos/{id}` 200 — reemplazo completo. **Enviar siempre `estado`:** si se omite vuelve a `DISPONIBLE` y reactiva un plato agotado.
- `PATCH /productos/{id}/estado` 200 — `{ estado: DISPONIBLE|AGOTADO }`. Lo usa COCINA.
- `GET /productos?categoriaId=&page=&size=` 200, `GET /productos/{id}` 200.
- `POST /categorias` 201 — `{ nombre, estado: HABILITADO|DESHABILITADO }`. `GET /categorias` 200 (paginado).
- `ProductoResponse`: `{ productoId, categoriaId, nombre, descripcion, imagenUrl, precio, tiempoPreparacion, estado }`. `CategoriaResponse`: `{ categoriaId, nombre, estado }`.

### Pedidos
- `GET /pedidos?estado=&fecha=&porCobrar=&page=&size=` 200 — listado paginado.

  | Parámetro | Default | Efecto |
  |---|---|---|
  | `estado` | todos | Solo ese `EstadoPedido` |
  | `fecha` | todas | Creados ese día (`yyyy-MM-dd`, Lima) |
  | `porCobrar` | `false` | `true`: no cancelados y nunca cobrados (excluye pagados y reembolsados) |
  | `page`, `size` | `0`, `20` | `size` 1–100; fuera de rango → `400` |

  Ítem: `{ pedidoId, tipoPedido, mesaId, estado, total, observacion, createdAt, estadoPago }`. `estadoPago`: `"PAGADO"`, `"REEMBOLSADO"` o `null` (sin pago). Botones: "Cobrar" si `estadoPago === null` y no está `CANCELADO`; "Reembolsar" solo si `PAGADO`. Caja: `?porCobrar=true`. Historial del día: `?fecha=<hoy>`.
- `POST /pedidos` 201 — `{ tipoPedido, mesaId?, observacion?, repartidorNombre?, items: [{ productoId, cantidad, observacion? }] }`. Nace `PENDIENTE`. No enviar montos: precios y total los calcula el back. Errores `400`: `"La mesa es obligatoria para pedidos en mesa"`, `"La mesa no existe"` (se valida siempre que llegue `mesaId`; no enviarlo si no aplica), `"Producto no encontrado: {id}"`, `"Producto no disponible: {id}"` (agotado). `items` vacío o `cantidad < 1` → `422`.
- `POST /pedidos/{id}/confirmar` 200 — `PENDIENTE → EN_PREPARACION`.
- `PATCH /pedidos/{id}/estado` 200 — `{ estado }`.
- Los tres últimos responden `PedidoResponse`: `{ pedidoId, tipoPedido, mesaId, estado, total }`.

**Transiciones permitidas:**

| Desde | Hacia |
|---|---|
| `PENDIENTE` | `EN_PREPARACION`, `CANCELADO` |
| `EN_PREPARACION` | `LISTO`, `CANCELADO` |
| `LISTO` (DELIVERY) | `EN_ENTREGA`, `CANCELADO` |
| `LISTO` (MESA, RECOJO) | `ENTREGADO`, `CANCELADO` |
| `EN_ENTREGA` (solo DELIVERY) | `ENTREGADO`, `CANCELADO` |
| `ENTREGADO`, `CANCELADO` | ninguna (`400`) |

- **Cancelar** no pide contraseña ni aprobación; la única condición es que el pedido **no esté pagado** (`400` `"El pedido pagado no puede cancelarse"`). Para un pedido pagado, el camino es reembolsar.
- Un pedido `ENTREGADO` sin pago es válido (cobro posterior).
- No hay control por "dueño" del pedido: cualquier ADMINISTRADOR o RECEPCION del restaurante puede cancelar o reembolsar cualquier pedido. El usuario que lo tomó se guarda pero no se expone.

### Pagos (caja)
- `POST /pagos` 201 — `{ pedidoId, metodoPago, montoEntregado?, referenciaExterna? }`. `montoEntregado` solo aplica a `EFECTIVO` y es obligatorio ahí (`400` `"Monto insuficiente"` si falta o no alcanza); `vuelto` es `null` en los demás métodos. `referenciaExterna` (voucher, máx. 100) se guarda pero no vuelve en la respuesta. Errores `400`: `"Pedido ya pagado"`, `"El pedido está cancelado, no se puede registrar el pago"`, `"El pedido fue reembolsado, no se puede volver a cobrar"`. Cobrar no cambia el estado del pedido.
- `POST /pagos/{id}/reembolso` 200 — body `{ motivo }` obligatorio (`422` si vacío). Reembolso total; solo sobre un pago `PAGADO` (`400` `"Solo un pago PAGADO puede reembolsarse"`). **Efecto:** el pedido queda `CANCELADO` automáticamente salvo que ya esté `ENTREGADO`. Refrescar el pedido después de reembolsar y ocultar "Cobrar".
- `PagoResponse`: `{ pagoId, pedidoId, monto, vuelto, metodoPago, estado, reembolsoMotivo, reembolsoUsuarioId }` (los dos últimos `null` si no fue reembolsado).

### Usuarios
- `POST /usuarios` 201 — `{ nombre, apellido, email, password (8–72), rolRestauranteId }`. Responde `{ usuarioId, email, nombre, restauranteId, rolRestauranteId }`. Email repetido → `409`; rol `ADMINISTRADOR` → `403`; rol inexistente o inactivo → `400`; sesión de plataforma → `400`.
- `PATCH /usuarios/{id}/rol` 204 — `{ nuevoRolId }`. Con sesión de restaurante asigna rol de restaurante (nunca `ADMINISTRADOR`); con sesión de plataforma asigna rol de plataforma (estrictamente inferior al propio). Usuario de otro restaurante → `400` `"El usuario no pertenece a este restaurante"`.
- `DELETE /usuarios/{id}` 204 — baja lógica + revoca sus refresh tokens. Auto-eliminación → `403`; último ADMINISTRADOR → `409`; usuario de otro restaurante → `400`.

### Cocina
- `GET /cocina/cola` 200 — pedidos `EN_PREPARACION`.
- `GET /cocina/listos` 200 — pedidos `LISTO`, mismo formato.
- `POST /cocina/pedidos/{id}/listo` 204 — `EN_PREPARACION → LISTO`. `400` `"Pedido no está en preparación"`.

```json
{ "pedidoId": 20, "mesaId": 3, "tipoPedido": "MESA", "estado": "EN_PREPARACION",
  "observacion": null, "total": 50.00, "createdAt": "2026-10-08T12:30:00",
  "items": [{ "detalleId": 41, "productoId": 7, "nombreProducto": "Lomo saltado", "cantidad": 2, "observacion": null }] }
```

El orden de ambas listas no está garantizado: ordenar por `createdAt` en el cliente.

## 8. WebSocket del tablero de cocina

STOMP sobre WebSocket nativo, **sin SockJS**. Solo servidor → cliente.

| | |
|---|---|
| URL | `ws://localhost:8080/ws` (prod: `wss://`) |
| Auth | Header `Authorization: Bearer <accessToken>` en el frame **CONNECT** (no en la URL) |
| Roles | ADMINISTRADOR, RECEPCION, COCINERO |
| Tópico | `/topic/restaurantes/{restauranteId}/cocina` (el `restauranteId` propio, exacto, sin comodines) |
| Orígenes | Los mismos de `CORS_ALLOWED_ORIGINS` |

Mensaje:

```json
{ "pedidoId": 20, "estadoAnterior": "EN_PREPARACION", "estadoNuevo": "LISTO" }
```

| `estadoNuevo` | Acción en el tablero |
|---|---|
| `EN_PREPARACION` | Agregar a la cola (detalle: `GET /cocina/cola`) |
| `LISTO` | Mover de cola a listos (o `GET /cocina/listos`) |
| Otro (`EN_ENTREGA`, `ENTREGADO`, `CANCELADO`) | Quitar de la columna de `estadoAnterior` |

- Se emite después de confirmar la transacción y solo si el estado anterior o el nuevo es `EN_PREPARACION` o `LISTO`. Crear un pedido (`PENDIENTE`) no emite nada; la cancelación automática por reembolso sí.
- El mensaje es liviano a propósito: el detalle se pide por REST.
- **Resincronizar siempre** al conectar y reconectar con `/cocina/cola` + `/cocina/listos`: los mensajes emitidos sin conexión se pierden.
- El token se valida solo en CONNECT. Al renovar el access token, reconectar con el token nuevo.
- CONNECT sin token, con token inválido o con rol sin acceso se rechaza. Suscribirse a otro tópico o enviar mensajes (SEND) también.
- El bloqueo `402` no aplica al WebSocket: un restaurante bloqueado sigue viendo el tablero.
- Dev: `proxy.conf.json` solo proxia `/api`; agregar `"/ws": { "target": "http://localhost:8080", "ws": true, "secure": false }` o conectar directo a `ws://localhost:8080/ws`.
- El polling de `GET /cocina/cola` sigue siendo válido como respaldo.

## 9. Enums

Copiar tal cual; validar en el cliente antes de enviar (ver §10 sobre valores inválidos).

| Enum | Valores |
|---|---|
| `TipoPedido` | `DELIVERY`, `RECOJO`, `MESA` |
| `EstadoPedido` | `PENDIENTE`, `EN_PREPARACION`, `LISTO`, `EN_ENTREGA`, `ENTREGADO`, `CANCELADO` |
| `EstadoProducto` | `DISPONIBLE`, `AGOTADO` |
| `EstadoCategoria` | `HABILITADO`, `DESHABILITADO` |
| `MetodoPago` | `EFECTIVO`, `TARJETA_CREDITO`, `TARJETA_DEBITO`, `YAPE`, `PLIN`, `OTROS` |
| `EstadoPago` | `PENDIENTE`, `PAGADO`, `REEMBOLSADO`, `CANCELADO` (en la práctica solo `PAGADO` y `REEMBOLSADO`) |
| `EstadoSuscripcion` | `ACTIVA`, `CANCELADA` (no existe `VENCIDA`: se deriva de `fechaFin`) |
| `EstadoRestaurante` | `ACTIVO`, `INACTIVO` |
| `EstadoPlan` | `ACTIVO`, `INACTIVO` |
| Rol plataforma | `SUPERADMIN`, `ADMIN`, `MODERADOR` |
| Rol restaurante | `ADMINISTRADOR`, `RECEPCION`, `COCINERO`, `CAJERO`, `MESERO`, `REPARTIDOR` |

`SuscripcionResponse`: `{ suscripcionId, restauranteId, planId, nombrePlan, monto, moneda, estado, fechaInicio, fechaFin }`. `moneda` siempre `"PEN"`.

## 10. Advertencias y puntos sin verificar en ejecución

Deducidos del código, no probados contra el servidor levantado. Confirmar antes de depender de ellos.

- **Token de acceso vencido o ausente: probablemente `403`, no `401`.** El backend no configura un `AuthenticationEntryPoint`; un JWT vencido se ignora y la petición sigue como anónima, lo que por defecto en Spring Security responde `403` sin cuerpo. Si se confirma, el interceptor "ante `401` → refresh" **no se dispara** al vencer el token (10 min). Mitigación en el front: refrescar de forma proactiva antes del vencimiento, o tratar `403` sin cuerpo JSON como sesión vencida. Requiere definición con backend.
- **`createdAt`: corrige la nota del front del 2026-10-09 ("llega en UTC con `Z`").** Por código, `createdAt` es `LocalDateTime` desde `35736ba` (2026-10-06) en `GET /cocina/cola`, `GET /cocina/listos` y `GET /pedidos`, y no hay configuración de Jackson que le agregue zona: se serializa **sin `Z`**. Desde `4a5f941` (PR #14, 2026-10-08) la JVM fija `America/Lima`, por lo que el valor es hora de Lima. Antes de ese commit el valor dependía de la zona del servidor (UTC en un contenedor), lo que explicaría la observación anterior (hipótesis, no reproducida). Consecuencias: (1) si el front agrega `Z` o convierte desde UTC, las horas quedan corridas 5 horas; (2) los pedidos creados antes del PR #14 pueden estar guardados en hora UTC y no se migraron. Confirmar con un pedido nuevo contra el backend actualizado.
- **WebSocket sin prueba manual desde el front.** El contrato de §8 sale del código y de `CocinaWebSocketIntegracionTest`; falta probarlo con el cliente Angular.
- **Valores inválidos no dan un `400` limpio.** JSON mal formado, un enum inexistente en body o query (`estado=FOO`) o una fecha mal formada no tienen manejo específico y probablemente terminan en `500`. Validar en el cliente.
- **Endpoints eliminados:** `POST /restaurantes/actual/suscripcion/renovar` y `PATCH /restaurantes/actual/plan` ya no existen. No llamarlos; el código de respuesta no está verificado (`CAMBIOS_FRONTEND.md` indica `404`).
- **Diferencias con `CAMBIOS_FRONTEND.md` §2:** ese documento indica `400` para contraseña vacía o incorrecta al cancelar la suscripción. Por código son `422` (vacía) y `401` (incorrecta).
- **Ids de rol:** `rolRestauranteId` y `nuevoRolId` son ids numéricos que dependen de la base de datos y no hay endpoint que los liste (ver §11).
- `sort` en `/productos`, `/categorias` y `/restaurantes`: los nombres de campo aceptados no están confirmados.

## 11. Lo que NO existe (no diseñar contra esto)

- Renovación o cambio de plan desde el tenant; token o login restringido para restaurantes bloqueados.
- `GET /pedidos/{id}` (detalle de un pedido con sus ítems fuera de cocina), edición (`PUT`) de pedido.
- Listado de pagos, listado de usuarios, listado de roles.
- Endpoints de `mesa` (solo se valida `mesaId` contra la tabla).
- Alta por API del primer ADMINISTRADOR de un restaurante nuevo.
- Filtros o búsqueda en el dashboard de plataforma (`GET /restaurantes` solo pagina).
- Mensajes cliente → servidor por WebSocket; otros tópicos además del de cocina.
- Reembolso parcial; control de cancelación o reembolso por quien tomó el pedido.
- Facturación / SUNAT.
