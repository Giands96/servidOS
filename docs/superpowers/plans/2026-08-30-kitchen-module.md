# Kitchen Module Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Implementar kitchen como proyección Kanban sin tabla propia: GestionarColaCocina con listarEnPreparacion y marcarListo.

**Architecture:** Domain proyección PreparacionPedido @Builder; UseCase @Service que solo lee ordering.PedidoJpaRepository y delega cambios de estado a ordering; WebSocket deferido.

**Tech Stack:** Spring Boot, Spring Data JPA, Lombok, Java 17 records, BusinessException

## Global Constraints
- Monolito modular Spring Modulith — boundaries por paquete
- kitchen nunca escribe directo en pedido — delega a ordering
- PreparacionPedido no es @Entity, no tiene tabla
- WebSocket deferido — no implementar WebSocketConfig ahora
- Command usa records Java 17
- Testing deferido

---

### Task 1: Dominio PreparacionPedido

**Files:**
- Modify: `src/main/java/com/servidos/v1/kitchen/domain/PreparacionPedido.java`

**Interfaces:**
- Consumes: `TipoPedido`, `EstadoPedido`, `DetallePedido`, `BigDecimal`
- Produces: `PreparacionPedido` usado por Task 3

- [ ] **Step 1: Actualizar PreparacionPedido.java**

```java
@Data @Builder @NoArgsConstructor @AllArgsConstructor
public class PreparacionPedido {
    private Long pedido_id; private Long restaurante_id; private Long mesa_id;
    private TipoPedido tipoPedido; private EstadoPedido estado; private String observacion;
    private BigDecimal total; private LocalDateTime created_at; private List<DetallePedido> items;
}
```

- [ ] **Step 2: Verificar compilación**

Run: `mvn compile -DskipTests` with JAVA_HOME=C:\Users\dsg48\.jdks\corretto-26.0.1
Expected: BUILD SUCCESS

- [ ] **Step 3: Commit**

```bash
git add src/main/java/com/servidos/v1/kitchen/domain/PreparacionPedido.java
git commit -m "feat(kitchen): add PreparacionPedido projection"
```

---

### Task 2: UseCase GestionarColaCocina

**Files:**
- Modify: `src/main/java/com/servidos/v1/kitchen/application/GestionarColaCocinaUseCase.java`

**Interfaces:**
- Consumes: `PedidoJpaRepository`, `DetallePedidoJpaRepository`, `PreparacionPedido`, `BusinessException`, `PedidoCreadoEvent`
- Produces: UseCase consumido por controller futuro

- [ ] **Step 1: Implementar GestionarColaCocinaUseCase.java**

```java
@Service @RequiredArgsConstructor
public class GestionarColaCocinaUseCase {
    private final PedidoJpaRepository pedidoRepository;
    private final DetallePedidoJpaRepository detalleRepository;
    private final PedidoMapper pedidoMapper;
    private final EventPublisher eventPublisher;
    public List<PreparacionPedido> listarEnPreparacion(Long restauranteId) {
        if (restauranteId == null) throw new BusinessException("Restaurante no identificado");
        var pedidos = pedidoRepository.findByRestauranteIdAndEstado(restauranteId, EstadoPedido.EN_PREPARACION);
        // mapear cada pedido + sus detalles a PreparacionPedido
    }
    @Transactional public void marcarListo(Long pedidoId, Long restauranteId) {
        var pedido = pedidoRepository.findByPedidoIdAndRestauranteId(pedidoId, restauranteId).orElseThrow(() -> new BusinessException("Pedido no encontrado"));
        if (pedido.getEstado() != EstadoPedido.EN_PREPARACION) throw new BusinessException("Pedido no está en preparación");
        pedido.setEstado(EstadoPedido.LISTO);
        pedidoRepository.save(pedido);
        eventPublisher.publish(new PedidoPreparadoEvent(pedidoId, restauranteId));
    }
}
```

Nota: Si ordering ya tiene CambiarEstadoPedidoUseCase, delegar a él en vez de setEstado directo. Para MVP el set directo es aceptable pero documentar boundary.

- [ ] **Step 2: Verificar compilación**

Run: `mvn compile -DskipTests`
Expected: BUILD SUCCESS (si falta findByRestauranteIdAndEstado en PedidoJpaRepository, agregarlo)

- [ ] **Step 3: Commit**

```bash
git add src/main/java/com/servidos/v1/kitchen/application/GestionarColaCocinaUseCase.java
git commit -m "feat(kitchen): implement GestionarColaCocina use case (projection)"
```

---

### Task 3: Verificación final

- [ ] **Step 1: Compilar todo**

Run: `mvn compile -DskipTests`
Expected: BUILD SUCCESS

```

