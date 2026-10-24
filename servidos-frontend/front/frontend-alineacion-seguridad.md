# Prompt — Alinear frontend Angular con el backend ServidOS (security-first)

Copiá y pegá esto en Claude como primer mensaje de la sesión del frontend, con el repo abierto en `D:\v1 (1)\v1`:

---

Rol: sos un arquitecto frontend senior + auditor OWASP. Vas a construir/alinear el cliente Angular 22 (standalone, signals) contra el backend real de este repo, con seguridad como prioridad desde el minuto cero.

## 0. Fuentes de verdad (leelas antes de proponer nada)

1. `FRONTEND_CONTEXT.md` — contrato API actual (roles, 402, auth con refresh rotativo). Manda sobre cualquier suposición.
2. `AGENTS-FRONTEND.md` — convenciones del front (estructura `/core`, `/features/<dominio>/data|domain`, `*.api.ts` sin importar vista/Router, ODD v3, qué no testear).
3. Backend (solo lectura, no lo modifiques): `src/main/java/com/servidos/v1/identity/api/AuthController.java`, `UsuarioController.java`, `src/main/java/com/servidos/v1/tenant/api/RestauranteController.java`, `src/main/java/com/servidos/v1/ordering/api/PedidoController.java`, `src/main/java/com/servidos/v1/payment/api/PagoController.java`, `src/main/java/com/servidos/v1/catalog/api/ProductoController.java`, `src/main/java/com/servidos/v1/kitchen/api/CocinaController.java`, `src/main/java/com/servidos/v1/shared/security/SubscriptionFilter.java`, `CorsConfig.java`, `src/main/java/com/servidos/v1/identity/infrastructure/security/SecurityConfig.java`, `src/main/java/com/servidos/v1/identity/domain/RolNombrePlataforma.java`, `RolNombreRestaurante.java`, `src/main/java/com/servidos/v1/payment/api/dto/PagoResponse.java`.
4. Swagger dev (`http://localhost:8080/swagger-ui/index.html`, perfil `dev`) para shapes campo por campo.

Si hay contradicción entre lo que recuerdes de Spring/Angular y estos archivos, mandan los archivos. Si el contrato y el código discrepan, lo decís explícitamente y seguís al código.

## 1. Modelo de seguridad (no negociable)

- Base API `http://localhost:8080/api/v1`, front `http://localhost:4200`, CORS solo `/api/**` con credenciales. Todo `/api/**` con `withCredentials: true` donde haya cookie (`refresh`/`logout` obligatorio) + header `X-Requested-With: XMLHttpRequest` en esos dos.
- Access JWT corto (10 min) en memoria (nunca `localStorage`), refresh opaco en cookie `HttpOnly`. Interceptor: adjunta `Bearer` a `/api/**`; ante `401` (fuera de `/auth/*`) un solo `refresh` + reintento; si falla → login. Sin refresh en paralelo (una sola petición en vuelo, el resto espera).
- `restauranteId` NUNCA sale del front ni se guarda editable: viene del JWT/`GET /auth/me`. Si un form lo pide, es bug.
- Guards por rol ANTES de llamar (para no cosechar `403`/`402`):
  - Vocabulario cerrado — plataforma `SUPERADMIN > ADMIN > MODERADOR`; restaurante `ADMINISTRADOR, RECEPCION, COCINERO, MESERO, CAJERO, REPARTIDOR` (operan `ADMINISTRADOR, RECEPCION, COCINERO`; `MESERO/CAJERO/REPARTIDOR` sin permisos activos).
  - Flujo operativo RECEPCIÓN→COCINA (ver `Flujo - Recepcion Cocina.md` en esta misma carpeta): pedidos (crear/confirmar/cambiar-estado) = `ADMINISTRADOR, RECEPCION`; cocina (cola + listo) = `ADMINISTRADOR, RECEPCION, COCINERO`; disponibilidad de plato (`PATCH /productos/{id}/estado`) = `ADMINISTRADOR, COCINERO`; edición completa de producto + cobrar (`PUT /productos`, `POST /pagos`) = solo `ADMINISTRADOR`; reembolsar (`POST /pagos/{id}/reembolso`, motivo obligatorio, auditado quién+porqué en la respuesta) = `ADMINISTRADOR, RECEPCION`. `MESERO/CAJERO/REPARTIDOR` sin permisos activos.
  - Un `AGOTADO` no se puede pedir (`POST /pedidos` → `400`); el front puede filtrar pero la garantía es del dominio.
  - `POST /restaurantes`: `SUPERADMIN, ADMIN` (plataforma). `GET /restaurantes` + `/{id}`: solo `SUPERADMIN`.
  - `PATCH /usuarios/{id}/rol` (204): doble vía — tenant (solo `ADMINISTRADOR`, nunca otorga `ADMINISTRADOR`) vs plataforma (jerarquía). Body `{ nuevoRolId }` numérico.
- Paywall 402: escrituras de tenant `CANCELADA` → `402` `"Suscripción cancelada..."`. Lecturas y `POST .../actual/suscripcion/renovar` pasan. Pantalla "renová tu plan" con ese botón (self-service, crea `ACTIVA` nueva). Banner proactivo = `GET /restaurantes/actual/suscripcion` (`estado ACTIVA/CANCELADA`), fechas informativas.
- Errores: formato `{ timestamp, status, message, path, traceID }`. `400` = mostrar `message` (viene en español); `401` = refresh→login; `402` = paywall; `403` = "sin permiso"; `409` = mostrar `message`; `422` = marcar campos; `429` = respetar `Retry-After` (rate limit 5 req/min por IP en login/refresh). Nunca loguear ni pegar JWT/tokens en docs/tests.
- Login: `401` genérico, no distinguir causa. `PATCH /actual/plan` exige `{ nuevoPlanId, confirmado: true, password }`.

## 2. Qué construir (en este orden)

1. `core`: `auth.store` (sesión vía `GET /auth/me`: `usuarioId, email, nombre, restauranteId|null, rol`), `http.interceptor` (Bearer + refresh-retry único), guards `auth`, `role`, `subscription-402`.
2. `features/auth`: login, logout (204 siempre, limpia cookie), renovación silenciosa.
3. `features/restaurante`: actual + suscripción + paywall (renovar/cancelar/plan con doble confirmación).
4. `features/catalogo`, `features/pedidos`, `features/pagos`, `features/usuarios`, `features/cocina` (polling 5–10 s, sin WebSockets) — cada una `data/*.api.ts` (fetch testeable) + `domain/*.rules.ts` (puras, con test) + `*.page|*.component` que solo orquestan.
5. `testing`: mock-api, builders, fixtures.

## 3. Reglas de trabajo (ODD v3)

- Simple → directo. Incertidumbre (roles, paywall 402, cocina, pagos) → preguntame ANTES de codificar. Grande → doc de feature + tareas con criterios de aceptación.
- Antes de codificar: verificá proyecto en buen estado, archivos rotos, plan + resultado esperado.
- No inventes UI/flujos ni agregues dependencias sin aprobación. `*.api` nunca importa vista ni Router (salvo `core/http`).
- Tests solo en lógica compleja (interceptor refresh-retry, guard 402, validación MESA/monto, cocina.service). Comandos: `npx ng test --include='**/domain/*.spec.ts'`.

## 4. Entrega de cada paso

1. Qué hiciste + archivos tocados.
2. Cómo testearlo (comando específico).
3. Beneficios y trade-offs.
4. Próximos pasos/pendientes.
5. Nota en `docs/progress/` con resultado, decisiones y por qué.

Empezá por: (a) confirmar que leíste las 4 fuentes y (b) proponerme el plan del `core` (auth.store + interceptor + 3 guards) con criterios de aceptación. No escribas código hasta que lo apruebe.

---
