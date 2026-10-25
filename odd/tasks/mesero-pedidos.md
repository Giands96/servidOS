# Feature: el rol MESERO toma, confirma y entrega pedidos de mesa

Objetivo: el MESERO atiende el salón completo (toma pedidos de mesa, los manda a cocina y los marca entregados) sin
cobrar, reembolsar, cancelar ni manejar delivery. Rama: `feat/mesero-pedidos` desde `feat/cuenta-mesa` (096f984).
Entrega: `single-pr` apilado sobre `feat/cuenta-mesa`. Runner: `./mvnw -q test -Dtest=<Clase>`.

## Specs
- **S1 — Permisos (`@PreAuthorize`).**
  | Endpoint | Hoy | Después |
  |---|---|---|
  | `POST /api/v1/pedidos` | ADMINISTRADOR, RECEPCION | + MESERO |
  | `GET /api/v1/pedidos` (incluye `?porCobrar=true`) | ADMINISTRADOR, RECEPCION, CAJERO | + MESERO |
  | `GET /api/v1/mesas/{mesaId}/cuenta` | ADMINISTRADOR, RECEPCION, CAJERO | + MESERO |
  | `POST /api/v1/pedidos/{id}/confirmar` | ADMINISTRADOR, RECEPCION | + MESERO |
  | `PATCH /api/v1/pedidos/{id}/estado` | ADMINISTRADOR, RECEPCION | + MESERO, **con la restricción de abajo** |
  | `POST /api/v1/mesas/{mesaId}/cuenta/pagar` | ADMINISTRADOR, RECEPCION, CAJERO | **sin cambios** (el MESERO no cobra) |
- **S2 — Restricción de `/estado`.** "`CambiarEstadoPedidoUseCase.ejecutar(id, restauranteId, estado)` hoy **no recibe
  el rol**: pasarle el rol del usuario autenticado (como ya se hace con `CurrentUser` en otros casos de uso), sin tomarlo
  nunca del JSON." "Si el rol es MESERO, la **única** transición permitida es `LISTO -> ENTREGADO` y **solo para pedidos
  `MESA`**. Cualquier otra (cancelar, `EN_ENTREGA`, delivery/recojo, volver atrás) -> 403 con un mensaje claro (por
  ejemplo "El mesero solo puede marcar como entregado un pedido de mesa listo")."
  "Para ADMINISTRADOR y RECEPCION la matriz actual no cambia." "`confirmar` no necesita restricción extra: ya solo pasa
  `PENDIENTE -> EN_PREPARACION`."
- **S3 — Tipo de pedido del MESERO.** Propuesta adoptada: "el MESERO puede crear `MESA` y el `RECOJO` vinculado a una
  mesa (`mesaId` presente, "para llevar" de la mesa), pero no DELIVERY ni RECOJO sin mesa (403)."
- **S4 — No cambia.** "Liberación automática de la mesa (`LiberarMesaAlCerrarPedidoUseCase`), cobro (`/pagos`,
  `/cuenta/pagar`), reembolsos, reportes, cocina (`/cocina/...`)."
- **S5 — Tests de integración.**
  - "MESERO crea un pedido MESA -> 201; la mesa LIBRE queda OCUPADA (comportamiento actual)."
  - "MESERO lista `GET /pedidos?porCobrar=true` -> 200; consulta `GET /mesas/{id}/cuenta` -> 200."
  - "MESERO confirma un pedido PENDIENTE -> EN_PREPARACION."
  - "MESERO pasa un pedido MESA de LISTO a ENTREGADO -> 200 (y si ya está pagado, la mesa se libera sola como hoy)."
  - "MESERO intenta CANCELADO, EN_ENTREGA o ENTREGADO sobre un DELIVERY/RECOJO -> 403, el estado no cambia."
  - "MESERO intenta `POST /cuenta/pagar` o `POST /pagos` -> 403."
  - "(Según la decisión) MESERO crea DELIVERY / RECOJO sin mesa -> 403."
  - "ADMINISTRADOR y RECEPCION: todas sus transiciones actuales siguen pasando (regresión)."
  - "Aislamiento multi-tenant: un MESERO de otro restaurante -> rechazado."
- **S6 — Entregables.** "Swagger actualizado (roles en las descripciones y la nueva respuesta 403 de `/estado`)."
  "`FRONTEND_CONTEXT.md` y `CAMBIOS_FRONTEND.md`: tabla de roles nueva, restricción de MESERO en `/estado` y la
  decisión sobre DELIVERY/RECOJO."

## Decisiones
- Rol: el controlador pasa `CurrentUser.getRole()` (lo setea `TenantFilter` desde el JWT). Los casos de uso suman una
  sobrecarga con `rol`; la firma sin rol queda para los flujos internos (`ConfirmarPedidoUseCase`,
  `CancelarPedidoAlReembolsarListener`), que no aplican restricción de rol.
- Orden en `/estado`: primero se busca el pedido en el tenant (inexistente u otro tenant → 400 "Pedido no encontrado",
  igual que hoy), después la regla del MESERO (403), después la matriz de siempre.
- Literal `"MESERO"` como en `RegistrarReembolsoUseCase`: `ordering` no depende de `identity.domain`.
- El RECOJO "para llevar" que crea el MESERO lo entrega RECEPCION/ADMINISTRADOR (S2 solo permite `MESA`).
- `FRONTEND_CONTEXT.md` no existe en esta rama (solo en `chore/agentes-docs-frontend`): mismo criterio que
  `cuenta-mesa`, se actualiza `CAMBIOS_FRONTEND.md` y el fragmento queda en `docs/progress/`.

## Tasks
| ID | Tarea | S# | Ruta | Estado | Commit |
|----|-------|----|------|--------|--------|
| T1 | Permisos + restricción de `/estado` y de tipo en `POST /pedidos`, Swagger, tests unitarios y HTTP | S1-S5 | inline (una sola unidad de seguridad, ~300 líneas) | [x] | 24c500f |
| T2 | `CAMBIOS_FRONTEND.md` + progreso con fragmento de `FRONTEND_CONTEXT.md` | S6 | inline | [x] | a577c01 + commit de docs(odd) |
| T3 | Endurecer: rol nulo o desconocido no saltea la regla (lista de roles permitidos) | S2, S3 | inline (2 archivos) | [x] | ver git log (`fix(pedidos): ...`) |

## Log
- L1 (2026-10-24, pedido original del usuario): feature "el rol MESERO toma, confirma y entrega pedidos de mesa" (texto
  completo pegado en la sesión; Specs S1-S6 lo citan textual).
- L2: baseline en `feat/mesero-pedidos` con Docker: `./mvnw -q test` -> exit 0; 586 tests, 0 fallos, 0 errores.
- L3: Risk: item 2 (permisos/guards) → verificador independiente después de los checks propios.
- L4: T1 RED: `./mvnw -q test -Dtest='CambiarEstadoPedidoUseCaseTest,CrearPedidoUseCaseTest,PedidoControllerTest,CuentaMesaControllerTest'`
  -> compilación fallida (faltaban `ejecutar(..., rol)`). GREEN: mismo comando + `ConfirmarPedidoUseCaseTest,
  CancelarPedidoAlReembolsarListenerTest` -> exit 0 (35 + 15 + 6 + 5). HTTP: `-Dtest='PedidoMeseroIntegracionTest,
  CuentaMesaSeguridadIntegracionTest'` -> exit 0 (21 + 15). Base de test sin residuos (`it-mesero-%`: 0 filas).
- L5: tests existentes cambiados por S1: `CuentaMesaControllerTest` (permiso de `consultar`),
  `CuentaMesaSeguridadIntegracionTest` (MESERO pasa de 403 a permitido al consultar), `PedidoControllerTest` (el
  controlador pasa el rol del JWT).
- L6: suite completa `./mvnw -q test` -> exit 0; 638 tests, 0 fallos, 0 errores (52 nuevos).
- L7: verificador independiente (Risk ítem 2): S1-S6 PASS, sin bloqueantes. Mutación (rol `null` en el controlador) ->
  9 de 21 tests HTTP fallan. Advisory adoptado: `CAMBIOS_FRONTEND.md` §20 aclara que `/confirmar` no restringe el tipo
  (sigue S2). Advisories no adoptados: la sobrecarga sin rol es pública (documentada como interna); el MESERO recibe 403
  en vez de 400 al pedir el mismo estado (sigue S2).
- L8: progreso local en `docs/progress/2026-10-24-mesero-pedidos.md` (`docs/` está en `.gitignore`) con el fragmento para
  `FRONTEND_CONTEXT.md`.
- L9: review RDD: assess nativo `medium/under_budget`, escalado por ítem 2 (permisos) -> `high`. Consentimiento otorgado
  por el usuario. Lineage `review-01b53930f0d7fa8b`, 3 lentes (riesgo, legibilidad, confiabilidad) -> `approved`, sin
  bloqueantes; acknowledge -> `authority: burned`. Cubre 096f984..d67c5f5.
- L10: advisories de la review (no bloqueantes, trabajo aparte): R1-001/R2-001/R3: la regla del MESERO falla abierta si el
  rol llega `null` (la sobrecarga sin rol es pública); sugerencia: lista de roles permitidos o sobrecarga interna no
  pública. R2-002: "salón" nombra dos conjuntos de roles distintos en `PedidoControllerTest` y `CuentaMesaControllerTest`.
- L11 (2026-10-24, pedido del usuario, textual): "Sí, endurecé la regla del MESERO para que un rol null o desconocido no la
  saltee, con su test." Resuelve R1-001/R2-001/R3.
- L12: T3: las sobrecargas con rol usan una lista de roles permitidos (ADMINISTRADOR y RECEPCION: todo; MESERO: su regla;
  nulo, vacío, otro rol o `ROLE_MESERO` -> 403 "Rol sin permiso para cambiar el estado del pedido" / "Rol sin permiso para
  tomar pedidos", antes de tocar la base). La firma sin rol ya no pasa `null`: llama a un método privado. RED:
  `-Dtest='CambiarEstadoPedidoUseCaseTest,CrearPedidoUseCaseTest'` -> 6 + 6 fallos. GREEN: mismo comando +
  `ConfirmarPedidoUseCaseTest,CancelarPedidoAlReembolsarListenerTest,PedidoControllerTest` -> exit 0 (41 + 21 + 3 + 7 + 6).
  Suite completa: exit 0, 650 tests, 0 fallos.
