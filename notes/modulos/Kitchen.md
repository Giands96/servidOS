---
title: Kitchen
date: 2026-08-30
tags:
  - servidos
  - modulo
  - kitchen
aliases:
  - Módulo Kitchen
---

# Kitchen

> [!info] Estado: Verde ✅ — Proyección sin tabla, WebSocket deferido
> Parte de [[ServidOS - Módulos Implementados]] · `PROJECT_CONTEXT:121`

## Qué es

Módulo de **cocina Kanban**: no tiene tabla propia. `PreparacionPedido` es **proyección** derivada de `pedido.estado` — es una vista del pedido en `EN_PREPARACION`/`LISTO`. Nunca escribe directo en `pedido`.

## Qué hace

- **Listar en preparación:** `findByRestauranteIdAndEstado(EN_PREPARACION)` + `findByPedidoIdAndRestauranteId` para detalles → `PreparacionPedido` con `items`
- **Marcar listo:** Valida `pedido` del tenant y `estado == EN_PREPARACION`, hace `setEstado(LISTO)`, `save`, `publish(PedidoPreparadoEvent)` — idealmente delega a `ordering.CambiarEstadoPedidoUseCase` (boundary), en MVP hace `set` directo documentado
- **Ocultar del tablero:** No es escritura — es filtro frontend `?estado=LISTO`, no columna `visible_en_tablero`

## Clases clave

| Clase | Qué es | Nota |
|-------|--------|------|
| `PreparacionPedido` | Dominio `@Builder`, **no `@Entity`** | `pedido_id, restaurante_id, mesa_id, tipoPedido, estado, total, items: List<DetallePedido>` |
| `PedidoPreparadoEvent` | Evento `@Getter @AllArgsConstructor` | `pedidoId, restauranteId` |
| `GestionarColaCocinaUseCase` | `@Service`, usa `ordering.PedidoJpaRepository` | Solo lee ordering, nunca escribe tabla propia |
| `WebSocketConfig` / `CocinaWebSocketController` | **Stubs deferidos** | Handshake + validación JWT + tenant + broadcast se enseña aparte |

## Relaciones

- **← `ordering`:** Lee `Pedido` + `DetallePedido` — si cambia `EstadoPedido`, kitchen se entera por query o evento
- **→ `ordering`:** `marcarListo` debería invocar `CambiarEstadoPedidoUseCase` para respetar boundary (MVP hace `save` directo con comentario)
- **→ `payment`/`reporting`:** No relación directa
- **Eventos:** `PedidoCreadoEvent` (ordering) → kitchen podría pre-cargar Kanban; `PedidoPreparadoEvent` → reporting

## Decisiones

- Sin `@Entity`, sin `*JpaRepository` propio
- `EstadoPedido` extendido con `EN_PREPARACION/LISTO` manteniendo 4 originales
- `findByRestauranteIdAndEstado` agregado a `PedidoJpaRepository` para Kanban
- WebSocket deferido — no se implementa `WebSocketConfig` ahora

