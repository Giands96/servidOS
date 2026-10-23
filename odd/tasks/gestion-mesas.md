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
- "Open order" = `pedido.mesa_id = X AND estado <> 'CANCELADO' AND NOT (estado = 'ENTREGADO' AND a PAGADO pago exists)`.
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
| T1 | V14 migration + `Mesa` domain/JPA/mapper/repository + create/list/update use cases + unit tests | delegated (writer trigger: 2+ non-trivial files) | [x] | (T1 commit; hash in final bookkeeping commit) |
| T2 | Ocupar / liberar use cases with row lock and open-orders query + unit tests | delegated | [ ] | |
| T3 | `MesaController` + DTOs + OpenAPI + controller tests | delegated | [ ] | |
| T4 | Order integration: auto-occupy in `CrearPedidoUseCase`; release listener on paid/delivered/cancelled + tests | delegated | [ ] | |
| T5 | Integration test: create table, occupy, 2 orders, deliver + pay both -> `LIBRE`; partial -> stays `OCUPADA` | delegated | [ ] | |
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

## Next step
- T2.
