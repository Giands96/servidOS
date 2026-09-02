# Design Spec — Payment Module (RegistrarPago) — 2026-08-30

## Contexto
Módulo payment registra cobros de pedidos. Se replica patrón hexagonal de catalog/ordering/tenant. Izipay POS es desconectado en MVP: pago se registra manualmente (vuelto solo para EFECTIVO, referencia_externa opcional para voucher). Integración Izipay online queda para fase 2.

## 1. Arquitectura y Límites
```
payment/
├── domain/ Pago (@Builder, crear()) + MetodoPago (EFECTIVO/YAPE/PLIN/TARJETA_*) + EstadoPago (PENDIENTE/PAGADO/CANCELADO)
├── infrastructure/jpa/ PagoJpaEntity (@Entity pago, vuelto nullable, referencia_externa) + PagoJpaRepository
├── infrastructure/mapper/ PagoMapper (@Component, nunca muta restaurante_id)
└── application/ RegistrarPagoUseCase (@Service, Command record, validar(), BusinessException)
```
- pago referencia pedido_id y restaurante_id como Long plano (no @ManyToOne a ordering).
- vuelto nullable solo para EFECTIVO.
- restaurante_id y usuario_id vienen de TenantContext/JWT, no del JSON.

## 2. Modelo y Flujo

### Pago.crear(pedidoId, restauranteId, usuarioId, metodoPago, monto, vuelto, referenciaExterna)
- pedidoId/restauranteId/usuarioId/metodoPago != null, monto >0, vuelto >=0 o null, estado siempre PAGADO
- vuelto == null si metodo != EFECTIVO

### RegistrarPagoUseCase.Command(Long pedidoId, MetodoPago metodoPago, BigDecimal montoEntregado, String referenciaExterna)
- montoEntregado es lo entregado en efectivo; para YAPE/PLIN/TARJETA null o igual a total
```
@Transactional ejecutar(Command cmd, Long restauranteId, Long usuarioId):
  validar: pedidoId/metodoPago != null antes DB
  pedido = pedidoRepository.findByPedidoIdAndRestauranteId(cmd.pedidoId(), restauranteId).orElseThrow
  if pedido.estado == CANCELADO throw
  if pagoRepository.existsByPedidoIdAndEstado(cmd.pedidoId(), PAGADO) throw Ya pagado
  total = pedido.getTotal()
  vuelto = null
  if metodo == EFECTIVO:
    if montoEntregado == null || montoEntregado < total throw Monto insuficiente
    vuelto = montoEntregado.subtract(total)
  pago = Pago.crear(cmd.pedidoId(), restauranteId, usuarioId, cmd.metodoPago(), total, vuelto, cmd.referenciaExterna())
  saved = pagoRepository.save(mapper.toEntity(pago))
  publish(PagoRegistradoEvent)
  return mapper.toDomain(saved)
```

## 3. Persistencia y Mappers

### Entity
- PagoJpaEntity -> pago (pago_id IDENTITY, pedido_id FK, restaurante_id FK, usuario_id FK, metodo_pago STRING, monto 10,2, vuelto 10,2 nullable, referencia_externa 100 nullable, estado PAGADO, fecha_pago timestamp now, created_at/updated_at @PrePersist)

### Repository
```
PagoJpaRepository: existsByPedidoIdAndEstado, findByPedidoIdAndRestauranteId, findByRestauranteId
```

### Mapper
- toDomain mapea created_at/updated_at; toEntity new+setters; updateEntity nunca toca restaurante_id/pedido_id.

### Índices
- Los de BD.sql (restaurante_id, pedido_id) — no se agregan más.

## 4. Testing — Deferido
Sin deps test.

## Fuera de Alcance
- Integración Izipay API/SDK online (Link de pago, Yape QR dinámico) — fase 2.
- No se procesa pago, solo se registra.

