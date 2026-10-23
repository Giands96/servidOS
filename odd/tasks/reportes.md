# Feature: reports (reportes) - sales and product ranking with PDF/xlsx export

## Objective
Restaurant administrators see the sales of a period (daily, weekly, monthly) as a list of paid orders and as a
ranking of sold products, sort the orders (highest sale, most recent, oldest), and export each report to PDF and xlsx.

## Problem / why
The `reporting` module only has stubs: `GenerarReporteVentas` logs a TODO and is never called, and both listeners
only log. There is no endpoint, query, or test. Admins have no way to know what they sold.

## Scope
- Two reports, both multi-tenant (every query filters by `restaurante_id` on `pedido`, `pago`, `detalle_pedido`):
  - Sales: paginated list of paid orders of the period + summary (count, total sold).
  - Products: ranking of products sold in the period (quantity, amount) + the same summary.
- Endpoints (role `ADMINISTRADOR` only):
  - `GET /api/v1/reportes/ventas?periodo&fecha&orden&page&size`
  - `GET /api/v1/reportes/productos?periodo&fecha`
  - `GET /api/v1/reportes/ventas/export?formato=PDF|XLSX&periodo&fecha&orden`
  - `GET /api/v1/reportes/productos/export?formato=PDF|XLSX&periodo&fecha`
- Exports: one file per report, PDF (OpenPDF) and xlsx (fastexcel), with the agreed header and columns.
- The unused stub `GenerarReporteVentas` is replaced by `GenerarReporteVentasUseCase` (naming convention).

## Out of scope (pending)
- Product ranking sorted by recency (the ranking is always by amount sold).
- An index on `pago(restaurante_id, fecha_pago)`: it needs a migration, to be discussed with the team.
- A row cap for exports; per-tenant time zones; the `PagoRegistradoListener` / `PedidoCreadoListener` stubs;
  Angular UI.

## Constraints
- No migration, no schema change (read-only queries over existing tables).
- No architecture change: same per-module layering (`domain`, `application`, `infrastructure`, `api`).
- Commit with explicit pathspecs only; never `git add -A`.

## Decisions (agreed with the user on 2026-10-23)
- Sale = order with a `PAGADO` payment and not `CANCELADO`. Refunded (`REEMBOLSADO`), unpaid, and cancelled orders
  are excluded.
- The period filter and the date ordering use `pago.fecha_pago` (when the money came in).
- Products report = ranking of sold products (quantity, amount) from `detalle_pedido` of the period's sales.
  The name is the latest `nombre_producto` snapshot, falling back to `producto.nombre`.
- "Highest sale" (`MAYOR_VENTA`) sorts orders by `pedido.total`; the ranking is sorted by amount sold.
- Periods are calendar periods containing a reference date, in `America/Lima`: the day, the Monday-Sunday week,
  the calendar month. Range is `[desde 00:00, hasta + 1 day 00:00)`.
- Only `ADMINISTRADOR` can see and export reports.
- Export columns:
  - Sales: N° pedido, Fecha de pago, Tipo, Método de pago, Total.
  - Products: #, Producto, Cantidad, Monto vendido.
  - Header on both: restaurant name, period (e.g. `Semanal · 19/10/2026 – 25/10/2026`), sales count, total sold.
    Rows follow the order chosen in the frontend.
- Libraries approved by the user: `com.github.librepdf:openpdf:3.0.5` (package `org.openpdf`, LGPL/MPL) and
  `org.dhatim:fastexcel:0.20.2` (Apache 2.0, write-only, light). iText (AGPL) and Apache POI (heavier) discarded.

## TDD
- Mode: strict (source: user global config `Strict TDD Mode: enabled`).
- Runner: `./mvnw test -Dtest=<TestClass>` (Windows: `mvnw.cmd`); integration tests need the test Postgres on 5433.

## Delivery
- Branch: `feat/reportes` from `origin/main` (41f522d), in worktree `D:/v1 (1)/v1-worktrees/reportes`.
- Strategy: `single-pr`, one commit per phase. Push/PR are the user's decision.
- Review: RDD is on (global); assess after each work-unit commit.

## Tasks
| ID | Task | Route | Status | Commit |
|----|------|-------|--------|--------|
| T1 | Domain: `Periodo` (calendar range in Lima), `OrdenVentas`, report records + unit tests | delegated (writer trigger: 2+ non-trivial files) | [x] | 2be3c6c |
| T2 | Queries + `GenerarReporteVentasUseCase` / `GenerarReporteProductosUseCase` (replace stub) + unit and integration tests | delegated | [x] | 37862d2 |
| T3 | `ReporteController` read endpoints + DTOs + controller tests | delegated | [x] | 5c9023b |
| T4 | Dependencies + PDF and xlsx renderers of a generic report document + tests | delegated | [x] | 5d281cc |
| T5 | Export use cases + export endpoints + tests | delegated | [x] | 6838c54 |
| T6 | Full suite green, docs/progress, close this document | inline | [x] | this closing `docs(odd)` commit; `docs/progress/2026-10-23-reportes.md` is local only (`docs/` is git-ignored) |
| T7 | Review follow-ups: HTTP 403 test for non-admin roles + injectable `Clock` for the default date | inline (1 non-trivial file) | [x] | the `test(reporting)` commit that adds this row |

## Acceptance criteria
- Only `PAGADO` and non-cancelled orders of the caller's restaurant appear; another tenant's data never does.
- A payment at 23:59:59 of the last day is inside the period; one at 00:00 of the next day is outside.
- Weekly period for any date returns its Monday-Sunday week; monthly handles February and December.
- `MAYOR_VENTA`, `MAS_RECIENTES`, `MAS_ANTIGUOS` produce the expected order with deterministic tie-breaks.
- Ranking aggregates quantity and amount per product across orders, sorted by amount.
- PDF and xlsx exports contain the header and the agreed columns, with correct content type and file name.
- Non-admin roles are rejected (`@PreAuthorize("hasRole('ADMINISTRADOR')")`).
- Full suite green.

## Progress
- 2026-10-23: scope agreed with the user, libraries approved, branch and worktree created.
  Baseline on 41f522d: `./mvnw test` green, 301 tests, 0 failures.
- T1-T5 done by one delegated writer (writer trigger), strict TDD: RED observed as compile failures of the new tests,
  then GREEN per task. Reporting tests: 84, all green (re-run by the parent).
- Full suite `./mvnw test` on 6838c54: green, 385 tests, 0 failures (baseline 301).
- Deviations: xlsx tests read the file with `ZipFile` (fastexcel streams entries, `ZipInputStream` fails); product
  name has a last-resort fallback `Producto <id>` to avoid null cells.
- Native review (RDD): medium risk (`pom.xml`), user granted consent; lens `review-reliability`; **approved** and
  acknowledged (lineage `review-4457e03d7ebecd40`, authority burned). Non-blocking advisory findings:
  - `R3-pago-fanout` (WARNING): would double-count a pedido with two `PAGADO` pagos. Not reachable today:
    `pago.pedido_id` is `UNIQUE` (V1), one pago per pedido. Revisit if split payments are ever introduced.
  - `R3-preauthorize-reflection-only` (WARNING): role rejection is checked by reflection, not by a request with a
    non-admin principal. Follow-up: a security-level test returning 403.
  - `R3-today-race` (SUGGESTION): default date reads the wall clock; inject a `Clock` to make it deterministic.
  - `R3-export-unbounded` (SUGGESTION): exports have no row cap (already listed as pending).
- T7 (user-requested follow-up of the two findings above):
  - `ReportesSeguridadIntegracionTest`: real HTTP through `TenantFilter` and method security; RECEPCION, CAJERO,
    MESERO, COCINERO, REPARTIDOR get 403 on the four endpoints, ADMINISTRADOR gets 200. It passed on first run
    (behavior already existed); proven discriminating by removing `@PreAuthorize` from `/ventas` -> 2 failures
    `expected: <403> but was: <200>`, then restored.
  - `ReporteController` takes a `Clock` (bean `RelojConfig`, UTC); "today" is computed in Lima from it.
    RED: `ReporteControllerTest` compile failure (constructor with `Clock`); GREEN: 9/9. The fixed clock is
    23:30 Friday in Lima (already Saturday in UTC), so the test also pins the zone.
  - Full suite: green, 393 tests, 0 failures.

## Next step
- Push and PR to `main` are the user's decision (squash merge, 1 approval).
