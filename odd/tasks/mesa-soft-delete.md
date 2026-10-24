# Feature: baja lógica de mesas (soft delete)

## Objetivo
Un restaurante puede dar de baja mesas que ya no usa (por ejemplo, al mudarse de 5 a 3 mesas) sin perder el
historial de pedidos ni romper los reportes, y puede reactivarlas si la baja fue un error.

## Problema / por qué
Hoy no existe forma de sacar una mesa: no hay `DELETE` ni estado de baja. Un borrado físico no es viable porque
`pedido` referencia a `mesa` (`fk_pedido_mesa_restaurante`, V11) y los reportes de ventas dependen de ese historial.

## Alcance
- Migración nueva `V15__mesa_activa.sql`: `activa BOOLEAN NOT NULL DEFAULT TRUE`; se reemplaza
  `UNIQUE (restaurante_id, numero)` (V1) por un índice único parcial `(restaurante_id, numero) WHERE activa`.
  `uq_mesa_id_restaurante` (V11, usado por la FK de `pedido`) no se toca.
- Dominio `Mesa`: campo `activa`, `desactivar()` (solo `LIBRE` y activa), `reactivar()` (solo inactiva).
  `ocupar`, `ocuparPorPedido` y `actualizar` rechazan una mesa inactiva.
- Casos de uso: `DesactivarMesaUseCase` (lock de fila; rechaza con pedidos abiertos), `ReactivarMesaUseCase`
  (rechaza si otra mesa activa usa el número). Crear/actualizar validan el número solo contra mesas activas.
  Listar devuelve solo activas; nuevo listado de inactivas. Asignar mesa a un pedido rechaza mesas inactivas.
- API: `POST /api/v1/mesas/{id}/desactivar`, `POST /api/v1/mesas/{id}/reactivar`, `GET /api/v1/mesas/inactivas`
  (todos `ADMINISTRADOR`). `MesaResponse` suma `activa`.

## Fuera de alcance
- Borrado físico, Angular UI, zonas, reservas.

## Restricciones
- Migraciones: solo archivos nuevos. Cambio de base de datos aprobado por el usuario el 2026-10-23.
- Multi-tenant: toda consulta filtra por `restaurante_id`.
- Commits con pathspecs explícitos: el índice tiene archivos staged ajenos (skills, docs de frontend, package.json).

## Decisiones
- Baja lógica en lugar de `DELETE`: preserva la FK de `pedido` y los reportes (decidido por el usuario).
- El número se puede reutilizar tras la baja (decidido por el usuario): unicidad solo entre mesas activas.
- La baja es reversible (decidido por el usuario). Reactivar con número ocupado por otra mesa activa → 400.
- Solo se desactiva una mesa `LIBRE` sin pedidos abiertos, para no dejar pedidos colgados de una mesa dada de baja.
- Listado de inactivas como endpoint aparte (`/inactivas`) para restringirlo a `ADMINISTRADOR` sin cambiar el
  contrato ni los roles del `GET` existente.

## TDD
- Modo: strict (fuente: config global del usuario `Strict TDD Mode: enabled`).
- Runner: `./mvnw test -Dtest=<TestClass>` (selectores separados por coma).

## Entrega
- Rama: `feat/mesa-soft-delete` desde `main` (42953cf). Estrategia: `single-pr`. Push/PR: decisión del usuario.
- Review: RDD activo (global); evaluar después de cada commit de unidad de trabajo.
- Pronóstico: ~350-450 líneas (con tests).

## Tareas
| ID | Tarea | Ruta | Estado | Commit |
|----|-------|------|--------|--------|
| T1 | Migración V15 + JPA/mapper + dominio `Mesa` (activa, desactivar, reactivar, guardas) + tests | delegada (writer: 2+ archivos no triviales) | [x] | 6025c3a |
| T2 | Casos de uso desactivar/reactivar + ajustes en crear/actualizar/listar/asignar + tests | delegada | [x] | 675446e |
| T3 | API: endpoints, `MesaResponse.activa`, OpenAPI + tests de controlador | delegada | [x] | dbc8d77 |
| T4 | Docs: `docs/progress/2026-10-23-mesa-soft-delete.md` | inline (un archivo mecánico) | [x] | sin commit: `docs/` está en `.gitignore` (solo local) |
| T5 | Rechazar mesa inactiva en pedidos DELIVERY/RECOJO con `mesaId` + tests | inline (2 archivos chicos ya entendidos) | [x] | ver Progreso |

## Criterios de aceptación
- Un admin desactiva una mesa `LIBRE` sin pedidos abiertos; deja de aparecer en `GET /mesas` y aparece en `/inactivas`.
- Desactivar una mesa `OCUPADA` o con pedidos abiertos → 400.
- Una mesa inactiva no se puede ocupar, editar ni asignar a un pedido nuevo → 400.
- Tras desactivar la mesa 3 se puede crear otra mesa 3.
- Reactivar devuelve la mesa al listado; si otra mesa activa usa su número → 400.
- Los pedidos históricos de una mesa desactivada siguen intactos.
- Mesas de otro restaurante nunca son visibles ni modificables.

## Progreso / evidencia
- Baseline (antes de cambios): `./mvnw -q test` -> exit 0; surefire 489 tests, 0 fallos, 0 errores, 0 omitidos.
- Nombre real del constraint de V1 verificado en la base de test vía `pg_constraint`: `mesa_restaurante_id_numero_key`
  (V15 lo elimina sin `IF EXISTS`).
- T1 RED: `./mvnw -q test -Dtest='MesaTest,MesaPersistenciaIntegracionTest'` -> error de compilación (`desactivar()` no existe).
  GREEN: mismas clases + tests de casos de uso y controlador de Mesa -> exit 0 (MesaTest 24, persistencia 9).
  Persistencia (Postgres real): dos inactivas + una activa con el mismo número se permiten; dos activas violan el índice;
  `activa` arranca en true.
- T2 RED: `./mvnw -q test -Dtest='DesactivarMesaUseCaseTest,ReactivarMesaUseCaseTest,ListarMesasUseCaseTest,MesaBajaLogicaIntegracionTest'`
  -> errores de compilación. GREEN: `./mvnw -q test -Dtest='*Mesa*Test,Mesa*IntegracionTest,CrearPedidoUseCaseTest,PedidoControllerTest'`
  -> exit 0. `MesaBajaLogicaIntegracionTest` (9, Postgres real): la baja libera el número; reactivar con número tomado
  falla y libre funciona; baja rechazada con mesa OCUPADA o con pedido MESA abierto; mesa inactiva rechazada en pedido
  MESA nuevo; cross-tenant -> "Mesa no encontrada"; pedidos históricos intactos.
- T3 RED: `./mvnw -q test -Dtest=MesaControllerTest` -> error de compilación. GREEN: mismo comando -> exit 0.
- Suite completa tras T3: `./mvnw -q test` -> exit 0; 531 tests (489 + 42), 0 fallos, 0 errores, 0 omitidos.
- Spot check del parent: `./mvnw -q test -Dtest='MesaBajaLogicaIntegracionTest,MesaControllerTest'` -> exit 0.
- Reporting: no lee `mesa` (búsqueda sin resultados en `reporting/`); sus tests pasan en la suite completa.
- Review (RDD): `gentle-ai review assess --base-ref main --committed-only` (untracked excluidos) -> riesgo `medium`,
  `review_due: false`, motivo `under_budget`; el slice queda pendiente hasta que un commit posterior alcance el presupuesto.
- `listarInactivas` vive en `ListarMesasUseCase` (misma convención de listado), no en un caso de uso aparte.

- T5 (decidido por el usuario: cualquier pedido con `mesaId` de una mesa inactiva se rechaza, sin importar el tipo).
  `existsByMesaIdAndRestauranteId` se reemplaza por `findByMesaIdAndRestauranteId` (sin lock: un pedido no-MESA no
  cambia la mesa). RED: `./mvnw -q test -Dtest='AsignarMesaAPedidoUseCaseTest,MesaBajaLogicaIntegracionTest'` -> error de
  compilación (`findByMesaIdAndRestauranteId` no existe). GREEN: esas clases + `CrearPedidoUseCaseTest`,
  `CrearPedidoIntegracionTest`, `MesaPedidoIntegracionTest` -> exit 0 (AsignarMesa 9, BajaLogica 10).
  Suite completa: `./mvnw -q test` -> exit 0; 534 tests, 0 fallos, 0 errores, 0 omitidos.
- Revisión RDD: el hook de stop ofreció revisar el candidato `workspace` (70 archivos, 16.381 líneas: los archivos staged
  ajenos, no esta feature). Consentimiento pendiente del usuario.

## Siguiente paso
- Respuesta del usuario al consentimiento de revisión; luego push y PR (decisión del usuario, squash merge).
