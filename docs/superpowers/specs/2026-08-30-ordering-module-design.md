# Design Spec — Ordering Module (CrearPedido) — 2026-08-30

## Contexto
Módulo `ordering` es el corazón del negocio ServidOS. Tras dejar `catalog` verde con patrón hexagonal (dominio `crear/actualizar` + `BusinessException` + `Mapper` sin mutar `restaurante_id` + validación `restauranteId` del `TenantContext`), se replica el enfoque A (vertical slice completo) al resto de módulos, empezando por `ordering`.

Decisión de orden: `ordering` -> `tenant` -> `identity` -> `payment` -> `kitchen` -> `reporting`. Se eligió `ordering` primero por tener las reglas más ricas (BPMN `estado_pedido`, `detalle_pedido.restaurante_id` denormalizado, validación cross-módulo a `catalog` por ID plano).

## 1. Arquitectura y Límites
```
ordering/
├── domain/ Pedido + DetallePedido (POJOs puros, @Builder, @Data, crear())
├── domain/event/ PedidoCreadoEvent
├── infrastructure/jpa/ PedidoJpaEntity + DetallePedidoJpaEntity (@Entity, @Table, @PrePersist/@PreUpdate) + PedidoJpaRepository + DetallePedidoJpaRepository
├── infrastructure/mapper/ PedidoMapper + DetallePedidoMapper (@Component, toDomain/toEntity/updateEntity)
└── application/ CrearPedidoUseCase (Command record, validar(), ejecutar(restauranteId), @Transactional)
```
- **Boundaries Modulith:** `producto_id` es `Long` plano, nunca `@ManyToOne` hacia `catalog` (PROJECT_CONTEXT.md:113). Validación cross-módulo vía `ProductoJpaRepository.existsByProductoIdAndRestauranteId` / `findBy...`.
- `restaurante_id` denormalizado en `pedido` y `detalle_pedido` para aislamiento tenant sin JOINs.
- Eventos vía `shared/event/EventPublisher.publish(new PedidoCreadoEvent(...))` — `kitchen` y `reporting` reaccionan vía listeners, no escritura directa.

## 2. Modelo de Dominio y Validaciones

### Command (application)
```java
record Command(TipoPedido tipoPedido, Long mesaId, String observacion, String repartidorNombre, List<Item> items) {
  record Item(Long productoId, Integer cantidad, String observacion) {}
}
```
`restauranteId` y `usuarioId` no vienen en Command — se obtienen de `TenantContext`/JWT (anti Broken Access Control, igual que CrearProductoUseCase).

### Pedido.crear() / DetallePedido.crear() (domain)
- `restaurante_id != null` else BusinessException
- `tipoPedido != null` else BusinessException
- `items` no vacío else BusinessException
- Si `tipoPedido == MESA` => `mesaId` obligatorio
- `cantidad > 0` else BusinessException
- `estado` inicial siempre `PENDIENTE` (se ignora input del frontend)
- `precio_unitario` tomado de `Producto.precio` actual (no del frontend) para evitar manipulación de total
- `subtotal = precio_unitario * cantidad`; `total = sum(subtotales)`
- Builder con `nombre.trim()` style para observacion si aplica

### Flujo CrearPedidoUseCase.ejecutar(Command cmd, Long restauranteId)
1. `if (restauranteId == null) throw BusinessException("Restaurante no identificado")`
2. `validar(cmd, restauranteId)` — primero null/blank sin DB, luego DB:
   - cada `productoId` existe y pertenece al tenant: `productoRepository.existsByProductoIdAndRestauranteId(productoId, restauranteId)` else BusinessException
3. Crear dominio: `Pedido.crear(...)` + loop `DetallePedido.crear(restauranteId, pedidoId, productoId, cantidad, precio_unitario, subtotal)`
4. Mapper `toEntity` + `pedidoRepository.save()` (obtiene pedido_id) + `detalleRepository.saveAll(detalles)`
5. `eventPublisher.publish(new PedidoCreadoEvent(pedidoId, restauranteId))`
6. `return pedidoMapper.toDomain(saved)`

## 3. Persistencia y Mappers

### JpaEntities
- `PedidoJpaEntity` -> `pedido` (pedido_id PK IDENTITY, restaurante_id not null, usuario_id, mesa_id nullable, tipo_pedido STRING, estado STRING default PENDIENTE, total precision 10 scale 2, observacion, repartidor_nombre, created_at/updated_at con @PrePersist/@PreUpdate)
- `DetallePedidoJpaEntity` -> `detalle_pedido` (detalle_id PK, pedido_id FK, restaurante_id, producto_id, cantidad, precio_unitario, subtotal, observacion, created_at/updated_at)

### Repositories
```java
interface PedidoJpaRepository extends JpaRepository<PedidoJpaEntity, Long> {
  Optional<PedidoJpaEntity> findByPedidoIdAndRestauranteId(Long id, Long restauranteId);
  List<PedidoJpaEntity> findByRestauranteId(Long restauranteId);
}
interface DetallePedidoJpaRepository extends JpaRepository<DetallePedidoJpaEntity, Long> {
  List<DetallePedidoJpaEntity> findByPedidoIdAndRestauranteId(Long pedidoId, Long restauranteId);
}
```

### Mappers
- `toDomain` mapea `created_at/updated_at`; `toEntity` usa `new Entity()` + setters (no Builder para JPA); `updateEntity` nunca toca `restaurante_id`.

### Transacción y Errores
- `@Transactional` en ejecutar(); guarda pedido primero para FK.
- Todo error de negocio -> `BusinessException` (400 vía GlobalExceptionHandler). Nunca `IllegalArgumentException` ni NPE por `trim()` sin null-check.
- Unicidad no aplica a pedido (pedidos se repiten), pero sí aislamiento tenant en todas las queries.

## 4. Testing
- Unit: `Pedido.crear()` y `DetallePedido.crear()` — casos restauranteId null, cantidad <=0, items vacío, tipo MESA sin mesaId.
- Unit UseCase: mock `ProductoJpaRepository`, verificar que total se calcula de precio real y que producto de otro tenant es rechazado.
- Integración: `@DataJpaTest` para repositories tenant-scoped.

## Fuera de Alcance (YAGNI)
- CambiarEstadoPedido, ConfirmarPedido se implementan en ciclos siguientes.
- `mesa` como entidad opcional no se modela aún.
- Facturación SUNAT / cierre_caja fuera de MVP.

## Referencias
- PROJECT_CONTEXT.md (multi-tenancy shared schema, YAGNI, BPMN)
- catalog pattern: Producto.crear/actualizar, ProductoMapper, CrearProductoUseCase, ActualizarProductoUseCase, GestionarCategoriaUseCase
