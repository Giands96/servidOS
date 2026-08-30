# Ordering Module (CrearPedido) Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Implementar CrearPedido vertical slice completo en ordering con patrón hexagonal idéntico a catalog (dominio crear + mapper sin mutar restaurante_id + validación tenant).

**Architecture:** Domain POJOs puros con `Pedido.crear()/DetallePedido.crear()` validando con BusinessException; JpaEntities con @PrePersist/@PreUpdate; Mappers @Component; UseCase @Service @Transactional con Command(Item) anidado y validación cross-módulo a Producto por ID plano.

**Tech Stack:** Spring Boot, Spring Modulith, Spring Data JPA, PostgreSQL, Lombok (@Data @Builder @NoArgsConstructor @AllArgsConstructor), Java 17 records, BusinessException, EventPublisher

## Global Constraints
- Monolito modular Spring Modulith — boundaries por paquete `v1.<modulo>` verificables con ApplicationModules
- Multi-tenancy shared schema: todo `restaurante_id` viene de TenantContext/JWT, nunca del frontend — validar `restauranteId == null` en UseCase
- Entre módulos usar `Long productoId` plano, nunca `@ManyToOne` hacia catalog
- Dominio nunca toca JPA — solo POJOs con `BusinessException`, sin `IllegalArgumentException`
- `updateEntity` nunca muta `restaurante_id`
- `Command` usa records Java 17, con `Item` anidado dentro de `Command`
- Estado inicial `PENDIENTE` hardcodeado en dominio, no viene del frontend
- precio_unitario tomado de `Producto.precio` real, no del frontend
- Testing deferido — no se agregan dependencias test en este ciclo

---

### Task 1: Dominio Pedido + DetallePedido con crear()

**Files:**
- Modify: `src/main/java/com/servidos/v1/ordering/domain/Pedido.java`
- Modify: `src/main/java/com/servidos/v1/ordering/domain/DetallePedido.java`

**Interfaces:**
- Consumes: `BusinessException`, `EstadoPedido`, `TipoPedido`, `BigDecimal`
- Produces: `Pedido.crear(Long restauranteId, Long usuarioId, Long mesaId, TipoPedido tipoPedido, String observacion, String repartidorNombre, BigDecimal total)` y `DetallePedido.crear(Long restauranteId, Long pedidoId, Long productoId, Integer cantidad, BigDecimal precioUnitario, String observacion)` — usadas por Task 5

- [ ] **Step 1: Actualizar Pedido.java con @Builder y crear()**

```java
package com.servidos.v1.ordering.domain;

import com.servidos.v1.shared.exception.BusinessException;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Pedido {
    private Long pedido_id;
    private Long restaurante_id;
    private Long mesa_id;
    private Long usuario_id;
    private TipoPedido tipoPedido;
    private String observacion;
    private String repartidor_nombre;
    private EstadoPedido estado;
    private BigDecimal total;
    private LocalDateTime created_at;
    private LocalDateTime updated_at;

    public static Pedido crear(Long restaurante_id, Long usuario_id, Long mesa_id, TipoPedido tipoPedido, String observacion, String repartidor_nombre, BigDecimal total) {
        if (restaurante_id == null) throw new BusinessException("El restaurante_id es obligatorio");
        if (tipoPedido == null) throw new BusinessException("El tipo de pedido es obligatorio");
        if (tipoPedido == TipoPedido.MESA && mesa_id == null) throw new BusinessException("La mesa es obligatoria para pedidos en mesa");
        if (total != null && total.compareTo(BigDecimal.ZERO) < 0) throw new BusinessException("El total no puede ser negativo");
        return Pedido.builder()
                .restaurante_id(restaurante_id)
                .usuario_id(usuario_id)
                .mesa_id(mesa_id)
                .tipoPedido(tipoPedido)
                .observacion(observacion)
                .repartidor_nombre(repartidor_nombre)
                .estado(EstadoPedido.PENDIENTE)
                .total(total != null ? total : BigDecimal.ZERO)
                .build();
    }
}
```

- [ ] **Step 2: Actualizar DetallePedido.java con @Builder y crear()**

```java
package com.servidos.v1.ordering.domain;

import com.servidos.v1.shared.exception.BusinessException;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DetallePedido {
    private Long detalle_id;
    private Long pedido_id;
    private Long restaurante_id;
    private Long producto_id;
    private Integer cantidad;
    private BigDecimal precio_unitario;
    private BigDecimal subtotal;
    private String observacion;
    private LocalDateTime created_at;
    private LocalDateTime updated_at;

    public static DetallePedido crear(Long restaurante_id, Long pedido_id, Long producto_id, Integer cantidad, BigDecimal precio_unitario, String observacion) {
        if (restaurante_id == null) throw new BusinessException("El restaurante_id es obligatorio");
        if (producto_id == null) throw new BusinessException("El producto_id es obligatorio");
        if (cantidad == null || cantidad <= 0) throw new BusinessException("La cantidad debe ser mayor a 0");
        if (precio_unitario == null || precio_unitario.compareTo(BigDecimal.ZERO) <= 0) throw new BusinessException("El precio unitario debe ser mayor a 0");
        BigDecimal subtotal = precio_unitario.multiply(BigDecimal.valueOf(cantidad));
        return DetallePedido.builder()
                .restaurante_id(restaurante_id)
                .pedido_id(pedido_id)
                .producto_id(producto_id)
                .cantidad(cantidad)
                .precio_unitario(precio_unitario)
                .subtotal(subtotal)
                .observacion(observacion)
                .build();
    }
}
```

- [ ] **Step 3: Verificar compilación**

Run: `mvn compile -DskipTests`
Expected: BUILD SUCCESS

- [ ] **Step 4: Commit**

```bash
git add src/main/java/com/servidos/v1/ordering/domain/Pedido.java src/main/java/com/servidos/v1/ordering/domain/DetallePedido.java
git commit -m "feat(ordering): add Pedido and DetallePedido domain with crear()"
```

---

### Task 2: JPA Entities

**Files:**
- Create: `src/main/java/com/servidos/v1/ordering/infrastructure/jpa/PedidoJpaEntity.java`
- Create: `src/main/java/com/servidos/v1/ordering/infrastructure/jpa/DetallePedidoJpaEntity.java`

**Interfaces:**
- Consumes: `EstadoPedido`, `TipoPedido`
- Produces: Entities usadas por Task 3 y Task 4

- [ ] **Step 1: Crear PedidoJpaEntity.java**

```java
package com.servidos.v1.ordering.infrastructure.jpa;

import com.servidos.v1.ordering.domain.EstadoPedido;
import com.servidos.v1.ordering.domain.TipoPedido;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import java.math.BigDecimal;
import java.time.LocalDateTime;

@Entity
@Table(name = "pedido")
@Data
@NoArgsConstructor
@AllArgsConstructor
public class PedidoJpaEntity {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "pedido_id")
    private Long pedidoId;
    @Column(name = "restaurante_id", nullable = false)
    private Long restauranteId;
    @Column(name = "usuario_id")
    private Long usuarioId;
    @Column(name = "mesa_id")
    private Long mesaId;
    @Enumerated(EnumType.STRING)
    @Column(name = "tipo_pedido", nullable = false, length = 20)
    private TipoPedido tipoPedido;
    @Column(name = "observacion", length = 500)
    private String observacion;
    @Column(name = "repartidor_nombre", length = 100)
    private String repartidorNombre;
    @Enumerated(EnumType.STRING)
    @Column(name = "estado", nullable = false, length = 20)
    private EstadoPedido estado;
    @Column(name = "total", nullable = false, precision = 10, scale = 2)
    private BigDecimal total;
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;
    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;
    @PrePersist protected void onCreate() { LocalDateTime now = LocalDateTime.now(); this.createdAt = now; this.updatedAt = now; }
    @PreUpdate protected void onUpdate() { this.updatedAt = LocalDateTime.now(); }
}
```

- [ ] **Step 2: Crear DetallePedidoJpaEntity.java**

```java
package com.servidos.v1.ordering.infrastructure.jpa;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import java.math.BigDecimal;
import java.time.LocalDateTime;

@Entity
@Table(name = "detalle_pedido")
@Data
@NoArgsConstructor
@AllArgsConstructor
public class DetallePedidoJpaEntity {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "detalle_id")
    private Long detalleId;
    @Column(name = "pedido_id", nullable = false)
    private Long pedidoId;
    @Column(name = "restaurante_id", nullable = false)
    private Long restauranteId;
    @Column(name = "producto_id", nullable = false)
    private Long productoId;
    @Column(name = "cantidad", nullable = false)
    private Integer cantidad;
    @Column(name = "precio_unitario", nullable = false, precision = 10, scale = 2)
    private BigDecimal precioUnitario;
    @Column(name = "subtotal", nullable = false, precision = 10, scale = 2)
    private BigDecimal subtotal;
    @Column(name = "observacion", length = 500)
    private String observacion;
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;
    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;
    @PrePersist protected void onCreate() { LocalDateTime now = LocalDateTime.now(); this.createdAt = now; this.updatedAt = now; }
    @PreUpdate protected void onUpdate() { this.updatedAt = LocalDateTime.now(); }
}
```

- [ ] **Step 3: Verificar compilación**

Run: `mvn compile -DskipTests`
Expected: BUILD SUCCESS

- [ ] **Step 4: Commit**

```bash
git add src/main/java/com/servidos/v1/ordering/infrastructure/jpa/PedidoJpaEntity.java src/main/java/com/servidos/v1/ordering/infrastructure/jpa/DetallePedidoJpaEntity.java
git commit -m "feat(ordering): add Pedido and DetallePedido JPA entities"
```

---

### Task 3: Repositories

**Files:**
- Modify: `src/main/java/com/servidos/v1/ordering/infrastructure/jpa/PedidoJpaRepository.java`
- Create: `src/main/java/com/servidos/v1/ordering/infrastructure/jpa/DetallePedidoJpaRepository.java`

**Interfaces:**
- Consumes: `PedidoJpaEntity`, `DetallePedidoJpaEntity`
- Produces: `findByPedidoIdAndRestauranteId`, `findByRestauranteId` — usadas por Task 5

- [ ] **Step 1: Actualizar PedidoJpaRepository.java**

```java
package com.servidos.v1.ordering.infrastructure.jpa;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import java.util.List;
import java.util.Optional;

@Repository
public interface PedidoJpaRepository extends JpaRepository<PedidoJpaEntity, Long> {
    Optional<PedidoJpaEntity> findByPedidoIdAndRestauranteId(Long pedidoId, Long restauranteId);
    List<PedidoJpaEntity> findByRestauranteId(Long restauranteId);
}
```

- [ ] **Step 2: Crear DetallePedidoJpaRepository.java**

```java
package com.servidos.v1.ordering.infrastructure.jpa;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import java.util.List;

@Repository
public interface DetallePedidoJpaRepository extends JpaRepository<DetallePedidoJpaEntity, Long> {
    List<DetallePedidoJpaEntity> findByPedidoIdAndRestauranteId(Long pedidoId, Long restauranteId);
}
```

- [ ] **Step 3: Verificar compilación**

Run: `mvn compile -DskipTests`
Expected: BUILD SUCCESS

- [ ] **Step 4: Commit**

```bash
git add src/main/java/com/servidos/v1/ordering/infrastructure/jpa/PedidoJpaRepository.java src/main/java/com/servidos/v1/ordering/infrastructure/jpa/DetallePedidoJpaRepository.java
git commit -m "feat(ordering): add pedido repositories with tenant methods"
```

---

### Task 4: Mappers

**Files:**
- Create: `src/main/java/com/servidos/v1/ordering/infrastructure/mapper/PedidoMapper.java`
- Create: `src/main/java/com/servidos/v1/ordering/infrastructure/mapper/DetallePedidoMapper.java`

**Interfaces:**
- Consumes: `Pedido`, `DetallePedido`, `PedidoJpaEntity`, `DetallePedidoJpaEntity`
- Produces: `toDomain`, `toEntity`, `updateEntity` — usadas por Task 5

- [ ] **Step 1: Crear PedidoMapper.java**

```java
package com.servidos.v1.ordering.infrastructure.mapper;

import com.servidos.v1.ordering.domain.Pedido;
import com.servidos.v1.ordering.infrastructure.jpa.PedidoJpaEntity;
import org.springframework.stereotype.Component;

@Component
public class PedidoMapper {
    public Pedido toDomain(PedidoJpaEntity e) {
        if (e == null) return null;
        return Pedido.builder()
                .pedido_id(e.getPedidoId())
                .restaurante_id(e.getRestauranteId())
                .usuario_id(e.getUsuarioId())
                .mesa_id(e.getMesaId())
                .tipoPedido(e.getTipoPedido())
                .observacion(e.getObservacion())
                .repartidor_nombre(e.getRepartidorNombre())
                .estado(e.getEstado())
                .total(e.getTotal())
                .created_at(e.getCreatedAt())
                .updated_at(e.getUpdatedAt())
                .build();
    }
    public PedidoJpaEntity toEntity(Pedido d) {
        if (d == null) return null;
        PedidoJpaEntity e = new PedidoJpaEntity();
        e.setPedidoId(d.getPedido_id());
        e.setRestauranteId(d.getRestaurante_id());
        e.setUsuarioId(d.getUsuario_id());
        e.setMesaId(d.getMesa_id());
        e.setTipoPedido(d.getTipoPedido());
        e.setObservacion(d.getObservacion());
        e.setRepartidorNombre(d.getRepartidor_nombre());
        e.setEstado(d.getEstado());
        e.setTotal(d.getTotal());
        return e;
    }
    public void updateEntity(Pedido domain, PedidoJpaEntity entity) {
        if (domain == null || entity == null) return;
        entity.setMesaId(domain.getMesa_id());
        entity.setTipoPedido(domain.getTipoPedido());
        entity.setObservacion(domain.getObservacion());
        entity.setRepartidorNombre(domain.getRepartidor_nombre());
        entity.setEstado(domain.getEstado());
        entity.setTotal(domain.getTotal());
    }
}
```

- [ ] **Step 2: Crear DetallePedidoMapper.java**

```java
package com.servidos.v1.ordering.infrastructure.mapper;

import com.servidos.v1.ordering.domain.DetallePedido;
import com.servidos.v1.ordering.infrastructure.jpa.DetallePedidoJpaEntity;
import org.springframework.stereotype.Component;

@Component
public class DetallePedidoMapper {
    public DetallePedido toDomain(DetallePedidoJpaEntity e) {
        if (e == null) return null;
        return DetallePedido.builder()
                .detalle_id(e.getDetalleId())
                .pedido_id(e.getPedidoId())
                .restaurante_id(e.getRestauranteId())
                .producto_id(e.getProductoId())
                .cantidad(e.getCantidad())
                .precio_unitario(e.getPrecioUnitario())
                .subtotal(e.getSubtotal())
                .observacion(e.getObservacion())
                .created_at(e.getCreatedAt())
                .updated_at(e.getUpdatedAt())
                .build();
    }
    public DetallePedidoJpaEntity toEntity(DetallePedido d) {
        if (d == null) return null;
        DetallePedidoJpaEntity e = new DetallePedidoJpaEntity();
        e.setDetalleId(d.getDetalle_id());
        e.setPedidoId(d.getPedido_id());
        e.setRestauranteId(d.getRestaurante_id());
        e.setProductoId(d.getProducto_id());
        e.setCantidad(d.getCantidad());
        e.setPrecioUnitario(d.getPrecio_unitario());
        e.setSubtotal(d.getSubtotal());
        e.setObservacion(d.getObservacion());
        return e;
    }
}
```

- [ ] **Step 3: Verificar compilación**

Run: `mvn compile -DskipTests`
Expected: BUILD SUCCESS

- [ ] **Step 4: Commit**

```bash
git add src/main/java/com/servidos/v1/ordering/infrastructure/mapper/PedidoMapper.java src/main/java/com/servidos/v1/ordering/infrastructure/mapper/DetallePedidoMapper.java
git commit -m "feat(ordering): add pedido mappers"
```

---

### Task 5: UseCase CrearPedido

**Files:**
- Modify: `src/main/java/com/servidos/v1/ordering/application/CrearPedidoUseCase.java`

**Interfaces:**
- Consumes: `Pedido.crear`, `DetallePedido.crear`, `PedidoMapper`, `DetallePedidoMapper`, `PedidoJpaRepository`, `DetallePedidoJpaRepository`, `ProductoJpaRepository` (catalog), `EventPublisher`
- Produces: `CrearPedidoUseCase.ejecutar(Command, Long restauranteId) -> Pedido` consumido por controllers futuros

- [ ] **Step 1: Implementar CrearPedidoUseCase.java**

```java
package com.servidos.v1.ordering.application;

import com.servidos.v1.catalog.infrastructure.jpa.ProductoJpaRepository;
import com.servidos.v1.ordering.domain.DetallePedido;
import com.servidos.v1.ordering.domain.Pedido;
import com.servidos.v1.ordering.domain.TipoPedido;
import com.servidos.v1.ordering.infrastructure.jpa.DetallePedidoJpaEntity;
import com.servidos.v1.ordering.infrastructure.jpa.DetallePedidoJpaRepository;
import com.servidos.v1.ordering.infrastructure.jpa.PedidoJpaEntity;
import com.servidos.v1.ordering.infrastructure.jpa.PedidoJpaRepository;
import com.servidos.v1.ordering.infrastructure.mapper.DetallePedidoMapper;
import com.servidos.v1.ordering.infrastructure.mapper.PedidoMapper;
import com.servidos.v1.shared.event.EventPublisher;
import com.servidos.v1.ordering.domain.event.PedidoCreadoEvent;
import com.servidos.v1.shared.exception.BusinessException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

@Service
@RequiredArgsConstructor
public class CrearPedidoUseCase {
    private final PedidoJpaRepository pedidoRepository;
    private final DetallePedidoJpaRepository detalleRepository;
    private final ProductoJpaRepository productoRepository;
    private final PedidoMapper pedidoMapper;
    private final DetallePedidoMapper detalleMapper;
    private final EventPublisher eventPublisher;

    public record Command(TipoPedido tipoPedido, Long mesaId, String observacion, String repartidorNombre, List<Item> items) {
        public record Item(Long productoId, Integer cantidad, String observacion) {}
    }

    @Transactional
    public Pedido ejecutar(Command cmd, Long restauranteId) {
        if (restauranteId == null) throw new BusinessException("Restaurante no identificado");
        validar(cmd, restauranteId);

        // Crear pedido sin total inicial
        Pedido pedidoDomain = Pedido.crear(restauranteId, null, cmd.mesaId(), cmd.tipoPedido(), cmd.observacion(), cmd.repartidorNombre(), BigDecimal.ZERO);
        PedidoJpaEntity pedidoEntity = pedidoMapper.toEntity(pedidoDomain);
        PedidoJpaEntity savedPedido = pedidoRepository.save(pedidoEntity);

        BigDecimal total = BigDecimal.ZERO;
        List<DetallePedidoJpaEntity> detallesToSave = new ArrayList<>();
        for (Command.Item item : cmd.items()) {
            var producto = productoRepository.findByProductoIdAndRestauranteId(item.productoId(), restauranteId)
                    .orElseThrow(() -> new BusinessException("Producto no encontrado: " + item.productoId()));
            DetallePedido detalle = DetallePedido.crear(restauranteId, savedPedido.getPedidoId(), item.productoId(), item.cantidad(), producto.getPrecio(), item.observacion());
            total = total.add(detalle.getSubtotal());
            detallesToSave.add(detalleMapper.toEntity(detalle));
        }
        detalleRepository.saveAll(detallesToSave);

        // Actualizar total del pedido
        savedPedido.setTotal(total);
        // Opcional: usar mapper update, pero aquí es simple set

        eventPublisher.publish(new PedidoCreadoEvent(savedPedido.getPedidoId(), restauranteId));

        return pedidoMapper.toDomain(savedPedido);
    }

    private void validar(Command cmd, Long restauranteId) {
        if (cmd.tipoPedido() == null) throw new BusinessException("El tipo de pedido es obligatorio");
        if (cmd.items() == null || cmd.items().isEmpty()) throw new BusinessException("El pedido debe tener al menos un item");
        for (Command.Item item : cmd.items()) {
            if (item.productoId() == null) throw new BusinessException("El producto es obligatorio");
            if (item.cantidad() == null || item.cantidad() <= 0) throw new BusinessException("La cantidad debe ser mayor a 0");
            if (!productoRepository.existsByProductoIdAndRestauranteId(item.productoId(), restauranteId)) {
                throw new BusinessException("El producto no existe o no pertenece al restaurante: " + item.productoId());
            }
        }
        if (cmd.tipoPedido() == TipoPedido.MESA && cmd.mesaId() == null) {
            throw new BusinessException("La mesa es obligatoria para pedidos en mesa");
        }
    }
}
```

- [ ] **Step 2: Verificar compilación**

Run: `mvn compile -DskipTests`
Expected: BUILD SUCCESS (si falta ProductoJpaRepository metodo, agregar `existsByProductoIdAndRestauranteId`/`findByProductoIdAndRestauranteId` que ya existe en catalog)

- [ ] **Step 3: Commit**

```bash
git add src/main/java/com/servidos/v1/ordering/application/CrearPedidoUseCase.java
git commit -m "feat(ordering): implement CrearPedidoUseCase with tenant validation and Item nested record"
```

---

### Task 6: Verificación final

- [ ] **Step 1: Compilar todo el proyecto**

Run: `mvn compile -DskipTests`
Expected: BUILD SUCCESS

- [ ] **Step 2: Verificar boundaries Modulith (si existe test)**

Run: `mvn test -Dtest=ModularityTests -DfailIfNoTests=false`
Expected: PASS o SKIP si no existe test

```

