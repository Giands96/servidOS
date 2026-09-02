# Payment Module Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Implementar payment vertical slice: Pago con RegistrarPago manual (vuelto solo efectivo, referencia_externa opcional).

**Architecture:** Domain POJO con crear() validando BusinessException; JpaEntity con vuelto nullable; Mapper @Component sin mutar restaurante_id; UseCase @Service @Transactional con Command record y validación tenant.

**Tech Stack:** Spring Boot, Spring Data JPA, PostgreSQL, Lombok, Java 17 records, BusinessException

## Global Constraints
- Monolito modular Spring Modulith — boundaries por paquete
- Dominio nunca toca JPA — solo POJOs con BusinessException
- Command usa records Java 17
- pago referencia pedido_id/restaurante_id como Long plano
- vuelto nullable solo para EFECTIVO
- restaurante_id/usuario_id vienen de TenantContext/JWT, no del JSON
- Testing deferido

---

### Task 1: Dominio Pago crear()

**Files:**
- Modify: `src/main/java/com/servidos/v1/payment/domain/Pago.java`
- Modify: `src/main/java/com/servidos/v1/payment/domain/MetodoPago.java` (if needed) / `EstadoPago.java` (keep as is)

**Interfaces:**
- Consumes: `BusinessException`, `MetodoPago`, `EstadoPago`, `BigDecimal`
- Produces: `Pago.crear` usado por Task 5

- [ ] **Step 1: Actualizar Pago.java con @Builder y crear()**

```java
@Data @Builder @NoArgsConstructor @AllArgsConstructor
public class Pago {
    private Long pago_id; private Long pedido_id; private Long restaurante_id; private Long usuario_id;
    private MetodoPago metodo_pago; private BigDecimal monto; private BigDecimal vuelto;
    private EstadoPago estado; private LocalDateTime fecha_pago; private String referenciaExterna;
    private LocalDateTime created_at; private LocalDateTime updated_at;
    public static Pago crear(Long pedido_id, Long restaurante_id, Long usuario_id, MetodoPago metodo_pago, BigDecimal monto, BigDecimal vuelto, String referenciaExterna) {
        if (pedido_id == null) throw new BusinessException("El pedido_id es obligatorio");
        if (restaurante_id == null) throw new BusinessException("El restaurante_id es obligatorio");
        if (usuario_id == null) throw new BusinessException("El usuario_id es obligatorio");
        if (metodo_pago == null) throw new BusinessException("El método de pago es obligatorio");
        if (monto == null || monto.compareTo(BigDecimal.ZERO) <= 0) throw new BusinessException("El monto debe ser mayor a 0");
        if (vuelto != null && vuelto.compareTo(BigDecimal.ZERO) < 0) throw new BusinessException("El vuelto no puede ser negativo");
        if (metodo_pago != MetodoPago.EFECTIVO && vuelto != null) throw new BusinessException("El vuelto solo aplica para efectivo");
        return Pago.builder().pedido_id(pedido_id).restaurante_id(restaurante_id).usuario_id(usuario_id).metodo_pago(metodo_pago).monto(monto).vuelto(vuelto).referenciaExterna(referenciaExterna).estado(EstadoPago.PAGADO).fecha_pago(LocalDateTime.now()).build();
    }
}
```

- [ ] **Step 2: Verificar compilación**

Run: `mvn compile -DskipTests` with JAVA_HOME=C:\Users\dsg48\.jdks\corretto-26.0.1
Expected: BUILD SUCCESS

- [ ] **Step 3: Commit**

```bash
git add src/main/java/com/servidos/v1/payment/domain/Pago.java
git commit -m "feat(payment): add Pago domain crear() with vuelto logic"
```

---

### Task 2: JPA Entity

**Files:**
- Create: `src/main/java/com/servidos/v1/payment/infrastructure/jpa/PagoJpaEntity.java`

**Interfaces:**
- Consumes: `MetodoPago`, `EstadoPago`
- Produces: Entity usada por repos/mappers

- [ ] **Step 1: Crear PagoJpaEntity.java**

```java
@Entity @Table(name = "pago")
@Data @NoArgsConstructor @AllArgsConstructor
public class PagoJpaEntity {
    @Id @GeneratedValue(IDENTITY) @Column(name = "pago_id") private Long pagoId;
    @Column(name = "pedido_id", nullable = false) private Long pedidoId;
    @Column(name = "restaurante_id", nullable = false) private Long restauranteId;
    @Column(name = "usuario_id", nullable = false) private Long usuarioId;
    @Enumerated(STRING) @Column(name = "metodo_pago", nullable = false, length = 20) private MetodoPago metodoPago;
    @Column(name = "monto", nullable = false, precision = 10, scale = 2) private BigDecimal monto;
    @Column(name = "vuelto", precision = 10, scale = 2) private BigDecimal vuelto;
    @Column(name = "referencia_externa", length = 100) private String referenciaExterna;
    @Enumerated(STRING) @Column(name = "estado", nullable = false, length = 20) private EstadoPago estado;
    @Column(name = "fecha_pago", nullable = false) private LocalDateTime fechaPago;
    @Column(name = "created_at", nullable = false, updatable = false) private LocalDateTime createdAt;
    @Column(name = "updated_at", nullable = false) private LocalDateTime updatedAt;
    @PrePersist protected void onCreate() { var now=LocalDateTime.now(); createdAt=now; updatedAt=now; if (fechaPago==null) fechaPago=now; }
    @PreUpdate protected void onUpdate() { updatedAt=LocalDateTime.now(); }
}
```

- [ ] **Step 2: Verificar compilación**

Run: `mvn compile -DskipTests`
Expected: BUILD SUCCESS

- [ ] **Step 3: Commit**

```bash
git add src/main/java/com/servidos/v1/payment/infrastructure/jpa/PagoJpaEntity.java
git commit -m "feat(payment): add Pago JPA entity"
```

---

### Task 3: Repository

**Files:**
- Modify: `src/main/java/com/servidos/v1/payment/infrastructure/jpa/PagoJpaRepository.java`

**Interfaces:**
- Consumes: PagoJpaEntity
- Produces: existsByPedidoIdAndEstado etc usado por Task 5

- [ ] **Step 1: Actualizar PagoJpaRepository.java**

```java
@Repository
public interface PagoJpaRepository extends JpaRepository<PagoJpaEntity, Long> {
    boolean existsByPedidoIdAndEstado(Long pedidoId, EstadoPago estado);
    Optional<PagoJpaEntity> findByPedidoIdAndRestauranteId(Long pedidoId, Long restauranteId);
    List<PagoJpaEntity> findByRestauranteId(Long restauranteId);
}
```

- [ ] **Step 2: Verificar compilación**

Run: `mvn compile -DskipTests`
Expected: BUILD SUCCESS

- [ ] **Step 3: Commit**

```bash
git add src/main/java/com/servidos/v1/payment/infrastructure/jpa/PagoJpaRepository.java
git commit -m "feat(payment): add pago repository"
```

---

### Task 4: Mapper

**Files:**
- Create: `src/main/java/com/servidos/v1/payment/infrastructure/mapper/PagoMapper.java`

**Interfaces:**
- Consumes: Pago, PagoJpaEntity
- Produces: toDomain/toEntity usado por Task 5

- [ ] **Step 1: Crear PagoMapper.java**

```java
@Component
public class PagoMapper {
    public Pago toDomain(PagoJpaEntity e) { /* mapeo completo */ }
    public PagoJpaEntity toEntity(Pago d) { /* new + setters */ }
}
```

- [ ] **Step 2: Verificar compilación**

Run: `mvn compile -DskipTests`
Expected: BUILD SUCCESS

- [ ] **Step 3: Commit**

```bash
git add src/main/java/com/servidos/v1/payment/infrastructure/mapper/PagoMapper.java
git commit -m "feat(payment): add pago mapper"
```

---

### Task 5: UseCase RegistrarPago

**Files:**
- Modify: `src/main/java/com/servidos/v1/payment/application/RegistrarPagoUseCase.java`

**Interfaces:**
- Consumes: Pago.crear, PagoMapper, PagoJpaRepository, PedidoJpaRepository, EventPublisher, BusinessException
- Produces: UseCase consumido por controller futuro

- [ ] **Step 1: Implementar RegistrarPagoUseCase.java**

```java
@Service @RequiredArgsConstructor
public class RegistrarPagoUseCase {
    private final PagoJpaRepository pagoRepository;
    private final PedidoJpaRepository pedidoRepository;
    private final PagoMapper pagoMapper;
    private final EventPublisher eventPublisher;
    public record Command(Long pedidoId, MetodoPago metodoPago, BigDecimal montoEntregado, String referenciaExterna) {}
    @Transactional public Pago ejecutar(Command cmd, Long restauranteId, Long usuarioId) {
        if (restauranteId == null) throw new BusinessException("Restaurante no identificado");
        if (usuarioId == null) throw new BusinessException("Usuario no identificado");
        if (cmd.pedidoId() == null) throw new BusinessException("El pedido es obligatorio");
        if (cmd.metodoPago() == null) throw new BusinessException("El método de pago es obligatorio");
        var pedido = pedidoRepository.findByPedidoIdAndRestauranteId(cmd.pedidoId(), restauranteId).orElseThrow(() -> new BusinessException("Pedido no encontrado"));
        if (pagoRepository.existsByPedidoIdAndEstado(cmd.pedidoId(), EstadoPago.PAGADO)) throw new BusinessException("Pedido ya pagado");
        BigDecimal total = pedido.getTotal();
        BigDecimal vuelto = null;
        if (cmd.metodoPago() == MetodoPago.EFECTIVO) {
            if (cmd.montoEntregado() == null || cmd.montoEntregado().compareTo(total) < 0) throw new BusinessException("Monto insuficiente");
            vuelto = cmd.montoEntregado().subtract(total);
        }
        Pago pago = Pago.crear(cmd.pedidoId(), restauranteId, usuarioId, cmd.metodoPago(), total, vuelto, cmd.referenciaExterna());
        var saved = pagoRepository.save(pagoMapper.toEntity(pago));
        eventPublisher.publish(new PagoRegistradoEvent(saved.getPagoId(), restauranteId, saved.getPedidoId()));
        return pagoMapper.toDomain(saved);
    }
}
```

- [ ] **Step 2: Verificar compilación**

Run: `mvn compile -DskipTests`
Expected: BUILD SUCCESS

- [ ] **Step 3: Commit**

```bash
git add src/main/java/com/servidos/v1/payment/application/RegistrarPagoUseCase.java
git commit -m "feat(payment): implement RegistrarPago use case"
```

---

### Task 6: Verificación final

- [ ] **Step 1: Compilar todo**

Run: `mvn compile -DskipTests`
Expected: BUILD SUCCESS

```

