# Feature: create restaurant together with its owner (ADMINISTRADOR)

## Objective
`POST /api/v1/restaurantes` creates restaurant + subscription + owner user + `usuario_restaurante` membership with role
`ADMINISTRADOR` in a single transaction, so a restaurant created from the platform can log in.

## Problem / why
Today the endpoint creates restaurant and subscription only. `POST /api/v1/usuarios` is tenant-scoped and forbids granting
ADMINISTRADOR, so new restaurants have no login (dev uses `scripts/sql/crear_admin.sql`). Requested by front (Phase 5
Platform); product owner decided restaurant and owner are created together.

## Scope
- New required `administrador` object in `CrearRestauranteRequest` (nombre, apellido, email, password).
- New identity use case that registers the owner inside the caller's transaction (no CurrentUser/tenant checks: the
  caller is the platform endpoint, already guarded by `@PreAuthorize("hasAnyRole('SUPERADMIN','ADMIN')")`).
- `CrearRestauranteUseCase` orchestrates both; 201 response adds `administrador { usuarioId, email, nombre }`.
- OpenAPI docs for 201/400/409/422.

## Constraints
- No schema/migration change (uses existing `usuario`, `usuario_restaurante`, `rol_restaurante`). Adding a derived query
  `findByNombre` on `RolRestauranteJpaRepository` is not a DB change.
- No architecture change: same domain -> application -> infrastructure layering; cross-module call follows the existing
  precedent (`CrearUsuarioUseCase` already depends on `RestauranteJpaRepository`).
- Global unique email; never link an existing user (LoginUseCase resolves restaurant membership first).
- Password never returned nor logged (records with password redact it in `toString`).
- Do not commit unrelated staged files in the index (commit with explicit pathspecs).

## Decisions
- Separate `RestauranteCreadoResponse` instead of adding a nullable field to `RestauranteResponse`, because
  `RestauranteResponse` is also returned by `GET /actual` and `GET /{id}`, which have no owner to show.
- Email uniqueness is checked before persisting the restaurant and `RestauranteCreadoEvent` is published only after the
  owner is created, so synchronous listeners never observe a restaurant that will be rolled back.
- `nombre` gets `@Size(min = 2)` to match `Usuario.crear` (otherwise a 1-char name would be a 400 instead of 422).
- Max lengths match `usuario` columns: nombre 100, apellido 100, email 150.

## TDD
- Mode: strict (source: user global config `Strict TDD Mode: enabled`).
- Runner: `./mvnw test -Dtest=<TestClass>` (Windows: `mvnw.cmd`).

## Delivery
- Branch: `feat/crear-restaurante-con-administrador`. Strategy: `single-pr` (one cohesive contract change; forecast
  ~150 production + ~350 test lines). Push/PR are the user's decision.
- Review: RDD is on (global); assess after each work-unit commit.

## Tasks
| ID | Task | Route | Status | Commit |
|----|------|-------|--------|--------|
| T1 | Identity: `RegistrarAdministradorRestauranteUseCase` + `RolRestauranteJpaRepository.findByNombre` + unit tests | delegated (writer trigger: 2+ non-trivial files) | [x] | 1169132 |
| T2 | Tenant: command/result + orchestration in `CrearRestauranteUseCase` + unit tests (update `CrearRestauranteDemoTest`) | delegated | [x] | c243ce9 |
| T3 | API: request/response DTOs, controller, OpenAPI + controller tests (update `RestauranteControllerTest`) | delegated | [x] | (this commit) |
| T4 | Integration: happy path (login + `/auth/me`), email dup -> 409 + no restaurant, slug dup -> 400 + no user | delegated | [ ] | |
| T5 | Docs: `docs/progress/2026-10-16-crear-restaurante-con-administrador.md` | delegated | [ ] | |

## Acceptance criteria
- 201 creates restaurant, subscription, ACTIVO user (BCrypt hash) and ADMINISTRADOR membership; owner can log in and
  `/auth/me` returns their `restauranteId` and role ADMINISTRADOR.
- Duplicate email -> 409 `"El email ya existe"` and no restaurant persisted.
- Duplicate slug -> 400 and no user persisted.
- Missing/invalid `administrador` -> 422 with `administrador.<campo>: <mensaje>`.
- Response and logs never contain the password.

## Progress / evidence
- Baseline: `./mvnw -q test -Dtest='CrearRestauranteDemoTest,RestauranteControllerTest,CrearUsuarioSeguridadTest'` -> exit 0, all green
  (the `A+B+C` selector from the brief is not valid for surefire; comma-separated works).
- T1 RED: `./mvnw -q test -Dtest=RegistrarAdministradorRestauranteUseCaseTest` -> COMPILATION ERROR (command/use case/`findByNombre` missing).
- T1 GREEN: `./mvnw test -Dtest='RegistrarAdministradorRestauranteUseCaseTest,CrearUsuarioSeguridadTest'` -> 15 tests, 0 failures, BUILD SUCCESS.
- T2 RED: `./mvnw -q test -Dtest=CrearRestauranteDemoTest` -> COMPILATION ERROR (7-arg `CrearRestauranteCommand`, `RestauranteCreado` missing).
- T2 GREEN: `./mvnw test -Dtest='CrearRestauranteDemoTest,RestauranteControllerTest,RegistrarAdministradorRestauranteUseCaseTest'` -> 32 tests, 0 failures, BUILD SUCCESS.
- T2 note: `RestauranteController.crear` passed `administrador = null` as a transitional compile fix (TODO(T3)); T3 wires the request and removes it.
- T3 RED: `./mvnw -q test -Dtest='RestauranteControllerTest,RestauranteControllerCrearWebTest'` -> COMPILATION ERROR (`AdministradorRequest`, `AdministradorResumenResponse`, `RestauranteCreadoResponse` missing).
- T3 GREEN: `./mvnw test -Dtest='RestauranteControllerTest,RestauranteControllerCrearWebTest,CrearRestauranteDemoTest'` -> 30 tests, 0 failures, BUILD SUCCESS
  (`RestauranteControllerCrearWebTest` is a MockMvc standalone test with `GlobalExceptionHandler`: 201 body, 422 `administrador.email|password|nombre|apellido`, 409).

## Next step
- T4.
