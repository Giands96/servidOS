# Design Spec — Kitchen Module (GestionarColaCocina) — 2026-08-30

## Contexto
Módulo kitchen es proyección Kanban de pedidos en preparación. No tiene tabla propia; es vista derivada de ordering.pedido.estado. WebSocket deferido para sesión de enseñanza.

## 1. Arquitectura y Límites
```
kitchen/
├── domain/ PreparacionPedido (@Builder, no @Entity) + PedidoPreparadoEvent
├── application/ GestionarColaCocinaUseCase (@Service, usa ordering.PedidoJpaRepository)
└── infrastructure/ WebSocketConfig (stub deferido)
```
- Nunca escribe directo en pedido; delega a ordering.CambiarEstadoPedidoUseCase o publica evento.
- Botón "Limpiar" es filtro frontend, no columna visible_en_tablero.

## 2. Operaciones

### PreparacionPedido (proyección)
- pedido_id, restaurante_id, mesa_id, tipoPedido, estado, observacion, items (DetallePedido), total, created_at

### GestionarColaCocinaUseCase
1. listarEnPreparacion(Long restauranteId): findByRestauranteIdAndEstado(EN_PREPARACION) + detalles
2. marcarListo(Long pedidoId, Long restauranteId): valida estado == EN_PREPARACION, delega a ordering para cambiar a LISTO, publish PedidoPreparadoEvent
3. ocultarDelTablero: no-op, es filtro query param estado

Validaciones: restauranteId != null antes DB, pedido del tenant, BusinessException si estado inválido.

## 3. Persistencia
- No hay Entity/Repository propios; usa ordering.PedidoJpaRepository y DetallePedidoJpaRepository
- Mapper PreparacionPedidoMapper: toDomain desde PedidoJpaEntity + detalles

### Reporting
- GenerarReporteVentas y listeners quedan deferidos (solo escuchan eventos, sin tabla propia).

## 4. WebSocket — Deferido
- WebSocketConfig y CocinaWebSocketController quedan stubs; se enseñará handshake + JWT + tenant + broadcast en sesión dedicada.

## 5. Testing — Deferido

