# Feature: table management (mesas) - model A

## Objective
Restaurants manage their tables: each table has a seat capacity, is occupied by a party (`comensales`), holds one or
more `MESA` orders, and is released automatically once every one of its orders is finished and paid, becoming
available for the next customers.

## Problem / why
Table `mesa` exists since V1 (`numero`, `estado` default `LIBRE`) but there is no domain, use case, or endpoint:
the API cannot create or list tables, and `MESA` orders only work if someone inserts the row by hand. Nothing ever
changes `mesa.estado`.

## Scope
- Migration `V14__mesa_capacidad_comensales.sql` (V13 is already taken on main by `V13__pedido_listo_at.sql`):
  `capacidad INT NOT NULL CHECK (> 0)` (existing rows backfilled to 4), `comensales INT NULL CHECK (> 0)`,
  `CHECK (estado IN ('LIBRE','OCUPADA'))`.
- Mesa lives inside the `ordering` module (`ordering/domain/Mesa`, `ordering/application/mesa`, `ordering/api/MesaController`,
  `ordering/infrastructure/...`) - same layering, no new bounded context, no cross-module port needed.
- Endpoints under `/api/v1/mesas`: create, list (optional `estado` filter), update (`numero`, `capacidad`),
  `POST /{id}/ocupar` (`comensales`), `POST /{id}/liberar` (manual; only when no open orders).
- `CrearPedidoUseCase` for `MESA`: locks the table and auto-occupies it when `LIBRE` (`comensales` stays null).
- Automatic release: on `PagoRegistradoEvent`, `PedidoEntregadoEvent`, `PedidoCanceladoEvent`, if the order has a
  table and the table has zero open orders, set `LIBRE` and clear `comensales`.

## Out of scope (pending)
- Deleting tables (FK from `pedido`; needs team discussion), zones/floor plan, joining/transferring tables,
  reservations, a single bill per table (model B), split bills, Angular UI.

## Constraints
- Migrations: new file only, never edit applied ones. Approved by the user on 2026-10-23 (asked as "V13").
- Payment stays per order (`UNIQUE(pedido_id)` untouched); `legacy-billing/` untouched.
- Multi-tenant: every query filters by `restaurante_id`.
- Commit with explicit pathspecs only: the index holds unrelated staged files (skills, frontend docs, package.json).

## Decisions
- "Open order" = `pedido.mesa_id = X AND estado <> 'CANCELADO' AND NOT (estado = 'ENTREGADO' AND a PAGADO or REEMBOLSADO pago exists)` (REEMBOLSADO added in T2, see Progress).
  The table is released only when the party finished (delivered) and paid, matching "termina con los pedidos y se cobra".
- Auto-occupy on order creation instead of rejecting orders on a `LIBRE` table: keeps current clients working and
  matches the request ("a local order gets a table"); `ocupar` is the explicit way to record `comensales`.
- `comensales` must be 1..`capacidad` (the table "has X seats"). Lowering `capacidad` below current `comensales` is rejected.
- Concurrency: the table row is locked (`PESSIMISTIC_WRITE`) in ocupar, liberar (manual and automatic), and MESA order
  creation, so a release cannot interleave with a new order on the same table.
- Existing rows get `capacidad = 4` as a neutral default; admins adjust it via the update endpoint.

## TDD
- Mode: strict (source: user global config `Strict TDD Mode: enabled`).
- Runner: `./mvnw test -Dtest=<TestClass>` (Windows: `mvnw.cmd`); comma-separated selectors for several classes.

## Delivery
- Branch: `feat/gestion-mesas` from `main` (c32dcba). Strategy: `single-pr` (one cohesive feature, same as the previous
  feature). Push/PR are the user's decision.
- Review: RDD is on (global); assess after each work-unit commit.

## Tasks
| ID | Task | Route | Status | Commit |
|----|------|-------|--------|--------|
| T1 | V14 migration + `Mesa` domain/JPA/mapper/repository + create/list/update use cases + unit tests | delegated (writer trigger: 2+ non-trivial files) | [x] | 10d57f0 |
| T2 | Ocupar / liberar use cases with row lock and open-orders query + unit tests | delegated | [x] | 393e157 |
| T3 | `MesaController` + DTOs + OpenAPI + controller tests | delegated | [x] | db060d2 |
| T4 | Order integration: auto-occupy in `CrearPedidoUseCase`; release listener on paid/delivered/cancelled + tests | delegated | [x] | 1bbc70b |
| T5 | Integration test: create table, occupy, 2 orders, deliver + pay both -> `LIBRE`; partial -> stays `OCUPADA` | delegated | [x] | (hash in final bookkeeping commit) |
| T6 | Docs: `docs/progress/2026-10-23-gestion-mesas.md` | delegated | [ ] | |

## Acceptance criteria
- Admin creates a table (`numero` > 0 unique per restaurant -> duplicate is rejected, `capacidad` > 0) and lists them.
- `ocupar` with 1..capacidad comensales on a `LIBRE` table -> `OCUPADA`; on an `OCUPADA` table -> rejected.
- A `MESA` order on a `LIBRE` table occupies it; several orders can share an `OCUPADA` table.
- When the last open order of a table is delivered and paid (either order of events) or cancelled, the table becomes
  `LIBRE` with `comensales` null. With one open order left it stays `OCUPADA`.
- Manual `liberar` succeeds only with zero open orders.
- Tables of another restaurant are never visible nor usable.

## Progress / evidence
- Baseline (before any change): `./mvnw -q test -Dtest='CrearPedidoUseCaseTest,CrearPedidoIntegracionTest,RegistrarPagoUseCaseTest,CambiarEstadoPedidoUseCaseTest'` -> exit 0 (test DB `servidos_test` on 5433, Postgres 16.14).
- Verified no code/seed/script/fixture inserts a `mesa` row (only `V1__init.sql` DDL), so `CHECK (estado IN ('LIBRE','OCUPADA'))` is safe.
- T1 RED: `./mvnw -q test -Dtest='MesaTest,CrearMesaUseCaseTest,ListarMesasUseCaseTest,ActualizarMesaUseCaseTest,MesaPersistenciaIntegracionTest'` -> COMPILATION ERROR (cannot find symbol: `Mesa`, `CrearMesaUseCase`, `MesaJpaRepository`, ...).
- T1 GREEN: same command -> exit 0; surefire: MesaTest 7, CrearMesaUseCaseTest 5, ListarMesasUseCaseTest 3, ActualizarMesaUseCaseTest 6, MesaPersistenciaIntegracionTest 6 (real Postgres, applies V14 and `ddl-auto: validate`), 0 failures.
- Duplicate `numero` maps to `BusinessException` (400), same as duplicate categories; the UNIQUE race is also mapped.
- T2 RED: `./mvnw -q test -Dtest='MesaTest,OcuparMesaUseCaseTest,LiberarMesaUseCaseTest,MesaOcuparLiberarIntegracionTest'` -> COMPILATION ERROR (cannot find symbol: `OcuparMesaUseCase`, `LiberarMesaUseCase`, `contarAbiertosEnMesa`, ...).
- T2 GREEN: same command -> exit 0; surefire: MesaTest 13, OcuparMesaUseCaseTest 5, LiberarMesaUseCaseTest 5, MesaOcuparLiberarIntegracionTest 7 (real Postgres: `SELECT ... FOR UPDATE` lock query and the pedido/pago open-orders query), 0 failures.
- Deviation from the decision above: a delivered order whose pago is `REEMBOLSADO` also counts as settled (not open). Reason: a refund after delivery leaves the order ENTREGADO with no PAGADO pago (the refund use case only cancels undelivered orders), so with the strict rule that order would hold the table open forever and block manual `liberar` (an ENTREGADO order admits no further state change). Consistent with the `porCobrar` filter, which already treats PAGADO and REEMBOLSADO as settled.
- The lock itself (concurrent interleaving) is not covered by an automated test: it would need committed data and two threads; the single-transaction integration tests only prove the locking query is valid against Postgres.
- T3 RED: `./mvnw -q test -Dtest='MesaControllerTest'` -> COMPILATION ERROR (cannot find symbol: `MesaController`, `CrearMesaRequest`, ...).
- T3 GREEN: `./mvnw -q test -Dtest='MesaControllerTest,MesaPersistenciaIntegracionTest'` -> exit 0; MesaControllerTest 10 (delegation, tenant, `@PreAuthorize` expressions by reflection, Jakarta validation of the DTOs), MesaPersistenciaIntegracionTest 6 (also proves the full Spring context boots with the new controller). One test showed a 2020 s duration in that run (host stall; re-run of the class alone took 7 s), so it is environmental, not code.
- Roles: create/update `ADMINISTRADOR`; list `ADMINISTRADOR,RECEPCION,CAJERO,MESERO`; ocupar/liberar `ADMINISTRADOR,RECEPCION,MESERO`. Errors: `BusinessException` -> 400 (duplicate, cross-tenant "Mesa no encontrada", rule violations), `@Valid` -> 422.
- T4 RED: `./mvnw -q test -Dtest='MesaTest,AsignarMesaAPedidoUseCaseTest,LiberarMesaAlCerrarPedidoUseCaseTest,LiberarMesaAlCerrarPedidoListenerTest,CrearPedidoUseCaseTest,CambiarEstadoPedidoUseCaseTest,MesaPedidoIntegracionTest'` -> COMPILATION ERROR (cannot find symbol: `AsignarMesaAPedidoUseCase`, `LiberarMesaAlCerrarPedidoUseCase/Listener`, `PedidoEntregadoEvent` constructor, ...).
- T4 GREEN: same command plus `CrearPedidoIntegracionTest` -> exit 0; MesaTest 15, AsignarMesaAPedidoUseCaseTest 6, LiberarMesaAlCerrarPedidoUseCaseTest 5, LiberarMesaAlCerrarPedidoListenerTest 3, CrearPedidoUseCaseTest 8, CambiarEstadoPedidoUseCaseTest 13, MesaPedidoIntegracionTest 6 (real Postgres: first MESA order occupies, orders share an OCUPADA table, cancel of the last order frees it and clears comensales, cancel with another open order keeps it OCUPADA, cross-tenant table rejected), CrearPedidoIntegracionTest 1; 0 failures.
- Findings that shaped the design: (1) `PedidoEntregadoEvent` existed as an empty class that nothing published, and delivery only emitted the generic `PedidoEstadoCambiadoEvent`; it now carries `pedidoId`/`restauranteId` and `CambiarEstadoPedidoUseCase` publishes it on ENTREGADO (same pattern as `PedidoCanceladoEvent`). (2) A MESA order reaches ENTREGADO via PENDIENTE -> EN_PREPARACION -> LISTO -> ENTREGADO, so "delivered" is the terminal state and the open-order rule stands. (3) The refund path cancels undelivered orders through `CambiarEstadoPedidoUseCase`, so `PedidoCanceladoEvent` also covers refunds.
- `existsMesaEnRestaurante` (native query in `PedidoJpaRepository`) was removed; the check now lives in `AsignarMesaAPedidoUseCase` (`MesaJpaRepository`), which `CrearPedidoUseCase` calls before saving the pedido. Non-MESA orders that carry a `mesaId` still get the same-tenant check but do not occupy the table.
- The release listener is a synchronous `@EventListener` inside the originating transaction (same as `CancelarPedidoAlReembolsarListener`), so the table lock is taken before commit and a failure rolls the operation back; the use case flushes pending pedido changes before counting open orders.
- T5 has no new production code (test-only task), so there is no natural RED. Mutation check instead: with the `@EventListener` removed from `LiberarMesaAlCerrarPedidoListener.on(PagoRegistradoEvent)`, `./mvnw -q test -Dtest='MesaCicloCompletoIntegracionTest'` -> exit 1, 3 of 10 failing (`cobrarYEntregarTodosLosPedidosLiberaLaMesa`, `unPedidoEntregadoYReembolsadoNoRetieneLaMesa`, `laMesaLiberadaSeVuelveAUsar`); the annotation was restored (no diff against T4 in `src/main`).
- T5 GREEN: same command -> exit 0, 10 tests, 0 failures. Covers: deliver+pay both -> LIBRE; reverse order (pay first, deliver after) -> LIBRE; one order unfinished -> stays OCUPADA with comensales kept; delivered-unpaid and paid-undelivered keep the table OCUPADA; delivered+refunded does not hold the table; refund of an undelivered order cancels it and frees the table; manual liberar only without open orders; freed table is reusable; DELIVERY/RECOJO orders (no table) pay, deliver and cancel without errors.

## Next step
- Full suite, then T6.
