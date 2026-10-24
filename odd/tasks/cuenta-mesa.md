# Feature: cuenta de mesa (cobro conjunto de los pedidos de una mesa)

## Objetivo
Cobrar en una sola operación atómica todo lo pendiente de una mesa: sus rondas `MESA` y los pedidos
`RECOJO`/`DELIVERY` vinculados con `mesaId`.

## Problema / por qué
- `POST /api/v1/pagos` cobra un único `pedidoId` (`UNIQUE(pedido_id)` en `pago`). Una mesa con 3 rondas + 1 para
  llevar exige 4 cobros separados, y agruparlos desde el front no es atómico.
- `contarAbiertosEnMesa` solo cuenta pedidos `MESA`: la mesa puede quedar LIBRE con un `RECOJO` vinculado sin cobrar.

## Alcance
1. `GET /api/v1/mesas/{mesaId}/cuenta` (ADMINISTRADOR, RECEPCION, CAJERO): pedidos pendientes de cobro de la mesa
   (no CANCELADO, sin pago PAGADO ni REEMBOLSADO, cualquier `tipoPedido`) con ítems y total calculado en la base.
2. `POST /api/v1/mesas/{mesaId}/cuenta/pagar` (mismos roles): en una sola `@Transactional`, bloquea la mesa,
   recalcula los pendientes, exige que coincidan exactamente con `pedidoIds`, crea un `Pago` por pedido unidos por
   `cuenta_mesa`, calcula el vuelto una vez sobre el total y publica `PagoRegistradoEvent` por cada pago.
3. Regla de liberación refinada en `contarAbiertosEnMesa`.

## Fuera de alcance
- Dividir la cuenta, pagos parciales, reembolso de la cuenta completa, mover/unir mesas.
- El contrato de `POST /api/v1/pagos` no cambia.

## Decisiones
- **Liberación (decidido por el usuario el 2026-10-24):** rondas `MESA` cuentan como hoy (abiertas hasta ENTREGADO
  y cobradas). `RECOJO`/`DELIVERY` vinculados bloquean la liberación solo mientras no estén cobrados (y no
  cancelados), estén entregados o no. Descartado quitar el filtro `tipo_pedido = 'MESA'`: un DELIVERY cobrado pero en
  camino retendría la mesa OCUPADA hasta que llegue el repartidor. Afecta también a `LiberarMesaUseCase` y
  `DesactivarMesaUseCase` (una mesa con un RECOJO vinculado sin cobrar no se libera ni se da de baja).
- **Módulo:** la cuenta vive en `payment` (`payment/application/cuenta`, `payment/api/CuentaMesaController`), igual
  que `RegistrarPagoUseCase`, que ya lee `PedidoJpaRepository`. `MesaController` no se toca.
- **Persistencia:** migración `V16__cuenta_mesa.sql`: tabla `cuenta_mesa` (restaurante, mesa, cajero, método, total,
  monto entregado, vuelto, referencia) y `pago.cuenta_id` nullable con FK. Los pagos de la cuenta llevan
  `vuelto = null`; el vuelto vive en `cuenta_mesa`. Los pagos individuales siguen con `cuenta_id = null`.
- **Concurrencia:** lock `bloquearPorIdYRestaurante` (el mismo de `AsignarMesaAPedidoUseCase`). Una ronda MESA nueva
  espera al lock; un RECOJO vinculado no toma el lock, pero el conjunto se recalcula bajo lock y se compara con
  `pedidoIds`, así nunca se cobra algo que el cajero no vio. Un pago individual concurrente del mismo pedido choca
  con `UNIQUE(pedido_id)` y revierte toda la cuenta.
- **Eventos:** primero se guardan todos los pagos, después se publican los eventos, así la primera liberación
  automática ya ve la cuenta entera cobrada.
- **Errores:** mesa inexistente o de otro tenant → mismo mensaje que los endpoints de mesa ("Mesa no encontrada", 400).
  Conjunto distinto → 400 "La cuenta cambió, vuelva a cargarla". Cuenta vacía al pagar → 400.
- **FRONTEND_CONTEXT.md** no existe en `main` (solo en la rama sin mergear `chore/agentes-docs-frontend`). Se
  actualiza `CAMBIOS_FRONTEND.md`; el fragmento para `FRONTEND_CONTEXT.md` queda en el doc de progreso.

## TDD
- Modo: strict (fuente: config global del usuario `Strict TDD Mode: enabled`).
- Runner: `./mvnw test -Dtest=<TestClass>` (selectores separados por coma).

## Entrega
- Rama: `feat/cuenta-mesa` desde `main` (195af79). Estrategia: `single-pr` (squash merge, igual que las features
  anteriores). Push/PR: decisión del usuario.
- Pronóstico: ~800-1000 líneas con tests (por encima de 400: se mantiene un PR único por la política de squash).
- Review: evaluar con `gentle-ai review assess` después de cada commit de unidad de trabajo.

## Tareas
| ID | Tarea | Ruta | Estado | Commit |
|----|-------|------|--------|--------|
| T1 | Regla de liberación refinada en `contarAbiertosEnMesa` + tests de integración | delegada (writer único T1-T4: 2+ archivos no triviales) | [x] | c8b203e |
| T2 | `GET /mesas/{id}/cuenta`: query de pendientes, ítems, caso de uso, controlador, OpenAPI + tests | delegada | [x] | be25b54 |
| T3 | `POST /mesas/{id}/cuenta/pagar`: V16, entidad/mapper, caso de uso, OpenAPI + tests | delegada | [x] | c2a4577 |
| T4 | `CAMBIOS_FRONTEND.md` + `docs/progress/2026-10-24-cuenta-mesa.md` | delegada | [x] | ver git log (`docs(frontend): contrato de la cuenta de mesa`) |

## Criterios de aceptación
- Mesa con 2 rondas MESA + 1 RECOJO vinculado → GET devuelve los 3 y el total correcto.
- Cobrar con `pedidoIds` correctos → 3 pagos PAGADO con el mismo `cuentaId`; con los pedidos ENTREGADO la mesa queda LIBRE.
- Ronda nueva entre el GET y el POST → POST rechazado, ningún pago creado.
- Efectivo con monto insuficiente → error, ningún pago creado.
- Pedido ya pagado por la vía individual → no aparece en la cuenta.
- Mesa de otro restaurante → rechazada.
- Rol sin permiso → 403.
- RECOJO vinculado sin cobrar impide la liberación automática y manual; cobrado (aunque no entregado) no la impide.

## Progreso / evidencia
- Exploración: `RegistrarPagoUseCase`, `LiberarMesaAlCerrarPedidoUseCase`, `AsignarMesaAPedidoUseCase`,
  `PedidoJpaRepository.contarAbiertosEnMesa`, `MesaJpaRepository`, `PagoJpaEntity`. Última migración: V15.

- Baseline (2026-10-24): `./mvnw -q test` -> exit 1; 534 tests, 0 fallos, 97 errores. Causa: Docker no corre
  (`failed to connect to the docker API at npipe:////./pipe/dockerDesktopLinuxEngine`); los tests de integración no
  levantan el contexto (sin Postgres). Es el entorno, no el código: los 437 tests unitarios pasan.

- Baseline con Docker levantado: `./mvnw -q test` -> exit 0; 534 tests, 0 fallos, 0 errores, 0 omitidos.
- RDD: on (global).

- T1 RED: `./mvnw -q test -Dtest=MesaCicloCompletoIntegracionTest,MesaOcuparLiberarIntegracionTest` -> 4 fallos
  (24 tests): recojo vinculado sin cobrar no retenía la mesa (auto y manual) y `contarAbiertosEnMesa` daba 0 en vez de 3.
- T1 GREEN: mismo comando + `*Mesa*Test,*Pago*Test,Mesa*IntegracionTest,ReembolsoCancelaPedidoIntegracionTest,
  ListarPedidosIntegracionTest` -> exit 0, 0 fallos. Tests existentes cambiados en `MesaOcuparLiberarIntegracionTest`:
  `losPedidosDeliveryYRecojoConMesaNoRetienenLaMesa` y `liberarManualIgnoraPedidosNoMesaAbiertos` asumían la regla vieja
  (no-MESA nunca retiene); se reemplazaron por 4 tests de la regla nueva.
- T2 RED: compilación fallida (sin `ConsultarCuentaMesaUseCase`, `CuentaMesaController`, `CuentaMesa`, queries nuevas).
- T2 GREEN: `./mvnw -q test -Dtest='ConsultarCuentaMesaUseCaseTest,CuentaMesa*Test,*Mesa*Test,*Pago*Test,Mesa*IntegracionTest,
  ReembolsoCancelaPedidoIntegracionTest,ListarPedidosIntegracionTest'` -> exit 0 (use case 4, controller 2, integración 4,
  seguridad HTTP 6 tests).

- T3 RED: compilación fallida (sin `PagarCuentaMesaUseCase`, `CuentaMesaJpaRepository`, `Pago.cuentaId`); luego
  `SubscriptionFilterTest` -> 1 fallo (`POST /mesas/{id}/cuenta/pagar` respondía 402 con el restaurante bloqueado).
- T3 GREEN: `./mvnw -q test -Dtest=SubscriptionFilterTest,PagarCuentaMesaUseCaseTest,CuentaMesaControllerTest,
  CuentaMesaIntegracionTest,CuentaMesaSeguridadIntegracionTest` -> exit 0 (16 + 10 + 5 + 11 + 15 tests).
- Suite completa tras T3: `./mvnw -q test` -> exit 0; 584 tests, 0 fallos, 0 errores, 0 omitidos.
- Desviación (confirmada por el usuario el 2026-10-24: se mantiene): `SubscriptionFilter` deja pasar `POST /api/v1/mesas/{id}/cuenta/pagar` con el restaurante bloqueado,
  igual que `POST /api/v1/pagos` (cobrar pedidos en curso). Sin eso la cuenta daría 402 donde el pago individual no.

- T4: documentación (sin código): `CAMBIOS_FRONTEND.md` §18 (cuenta de mesa) y §19 (regla de liberación), tabla
  resumen, ruta en §5 y checklist; `docs/progress/2026-10-24-cuenta-mesa.md` (local, `docs/` está en .gitignore) con el
  fragmento para `FRONTEND_CONTEXT.md`. Verificación: relectura estructural de ambos archivos.

- Verificación del parent: diff de `PagarCuentaMesaUseCase`, `V16__cuenta_mesa.sql`, `contarAbiertosEnMesa` y
  `SubscriptionFilter` revisado contra las decisiones. Spot check:
  `./mvnw -q test -Dtest='CuentaMesaIntegracionTest,CuentaMesaSeguridadIntegracionTest,MesaCicloCompletoIntegracionTest'`
  -> exit 0 (11 + 15 + 13 tests, 0 fallos).
- Review (RDD): `gentle-ai review assess --base-ref main --committed-only --untracked-scope=exclude` -> riesgo `high`
  (hot path de seguridad: `SubscriptionFilter`), `review_due: true`. START devolvió el consentimiento (lineage
  `review-f6e2a977e26d12b1`). Consentimiento otorgado por el usuario; 4 lentes (riesgo, resiliencia, legibilidad,
  confiabilidad) -> `approved`, sin hallazgos bloqueantes. Acknowledge -> `authority: burned`. Cubre c8b203e..f75ec32.
- Sugerencias no bloqueantes (trabajo posterior aparte):
  - R2-1: el predicado "cobrado" (PAGADO/REEMBOLSADO) se repite en `contarAbiertosEnMesa` y
    `buscarPendientesDeCobroEnMesa`; si una cambia sin la otra, la cuenta y la liberación divergen.
  - R2-2: `PagarCuentaMesaUseCase` traduce cualquier `DataIntegrityViolationException` a "La cuenta cambió", no solo
    la violación de `UNIQUE(pedido_id)`; otro fallo de constraint quedaría con un mensaje engañoso.
- Pendiente de negocio (abierto): ¿los reportes deben leer `cuenta_mesa.vuelto`? Los pagos de una cuenta llevan
  `vuelto = null`.

## Siguiente paso
- Push y PR (`single-pr`, squash merge): decisión del usuario.
- Abierto: decidir si reporting lee `cuenta_mesa.vuelto`; R2-1 y R2-2 como mejoras aparte.
