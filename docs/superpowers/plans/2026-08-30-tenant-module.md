# Tenant Module Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Implementar tenant vertical slice completo: Restaurante + Suscripcion con CrearRestaurante, GestionarSuscripcion (suscribir/cancelar/renovar) y CambiarPlan con patrón hexagonal idéntico a catalog/ordering.

**Architecture:** Domain POJOs puros con crear() validando con BusinessException; JpaEntities con @PrePersist/@PreUpdate; Mappers @Component sin mutar restaurante_id; UseCases @Service @Transactional con Command records y validación tenant (slug unique, plan exists, fechas LocalDate).

**Tech Stack:** Spring Boot, Spring Data JPA, PostgreSQL, Lombok (@Data @Builder @NoArgsConstructor @AllArgsConstructor), Java 17 records, BusinessException, EventPublisher

## Global Constraints
- Monolito modular Spring Modulith — boundaries por paquete v1.<modulo>
- Multi-tenancy shared schema: restaurante_id nunca viene del frontend salvo GestionarSuscripcion/CambiarPlan donde es param explícito validado
- Dominio nunca toca JPA — solo POJOs con BusinessException
- updateEntity nunca muta restaurante_id
- Command usa records Java 17
- Suscripcion estado solo ACTIVA/CANCELADA (sin INACTIVA), fecha_inicio/fin son LocalDate
- Restaurante slug único, 3-100 chars regex ^[a-z0-9-]+$, estado siempre ACTIVO
- Renovación/CambiarPlan siempre INSERT nueva ACTIVA (preserva historial); Cancelar UPDATE ACTIVA->CANCELADA
- Testing deferido — no se agregan dependencias test

---

### Task 1: Dominio Restaurante + Suscripcion crear()

**Files:**
- Modify: `src/main/java/com/servidos/v1/tenant/domain/Restaurante.java`
- Modify: `src/main/java/com/servidos/v1/tenant/domain/Suscripcion.java`

**Interfaces:**
- Consumes: `BusinessException`, `EstadoRestaurante`, `Suscripcion.EstadoSuscripcion`, `LocalDate`
- Produces: `Restaurante.crear(String slug, String nombre, String direccion)` y `Suscripcion.crear(Long restauranteId, Long planId, LocalDate fechaInicio, LocalDate fechaFin)` — usadas por Task 5

- [ ] **Step 1: Actualizar Restaurante.java con @Builder y crear()**

```java
package com.servidos.v1.tenant.domain;

import com.servidos.v1.shared.exception.BusinessException;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import java.time.LocalDateTime;

@Data @Builder @NoArgsConstructor @AllArgsConstructor
public class Restaurante {
    private Long restaurante_id;
    private String slug;
    private String nombre;
    private String direccion;
    private EstadoRestaurante estado;
    private LocalDateTime created_at;
    private LocalDateTime updated_at;

    public static Restaurante crear(String slug, String nombre, String direccion) {
        if (slug == null || slug.trim().isEmpty()) throw new BusinessException("El slug es obligatorio");
        String s = slug.trim().toLowerCase();
        if (s.length() < 3 || s.length() > 100) throw new BusinessException("El slug debe tener entre 3 y 100 caracteres");
        if (!s.matches("^[a-z0-9-]+$")) throw new BusinessException("El slug solo puede contener letras minúsculas, números y guiones");
        if (nombre == null || nombre.trim().isEmpty()) throw new BusinessException("El nombre es obligatorio");
        if (nombre.trim().length() > 150) throw new BusinessException("El nombre no puede tener más de 150 caracteres");
        return Restaurante.builder().slug(s).nombre(nombre.trim()).direccion(direccion).estado(EstadoRestaurante.ACTIVO).build();
    }
}
```

- [ ] **Step 2: Verificar Suscripcion.java ya tiene crear() compatible (si no, ajustar)**

Suscripcion.java ya fue corregido a LocalDate y ACTIVA/CANCELADA en fix previo. Verificar que tenga:

```java
public static Suscripcion crear(Long restaurante_id, Long plan_id, LocalDate fecha_inicio, LocalDate fecha_fin) {
    if (restaurante_id == null) throw new BusinessException("El restaurante_id es obligatorio");
    if (plan_id == null) throw new BusinessException("El plan_id es obligatorio");
    if (fecha_inicio == null) throw new BusinessException("La fecha de inicio es obligatoria");
    if (fecha_fin == null || !fecha_fin.isAfter(fecha_inicio)) throw new BusinessException("La fecha de fin debe ser posterior a la fecha de inicio");
    return Suscripcion.builder().restaurante_id(restaurante_id).plan_id(plan_id).estado(EstadoSuscripcion.ACTIVA).fecha_inicio(fecha_inicio).fecha_fin(fecha_fin).build();
}
```

Si no existe, agregarlo.

- [ ] **Step 3: Verificar compilación**

Run: `mvn compile -DskipTests` with JAVA_HOME=C:\Users\dsg48\.jdks\corretto-26.0.1
Expected: BUILD SUCCESS

- [ ] **Step 4: Commit**

```bash
git add src/main/java/com/servidos/v1/tenant/domain/Restaurante.java src/main/java/com/servidos/v1/tenant/domain/Suscripcion.java
git commit -m "feat(tenant): add Restaurante and Suscripcion domain crear()"
```

---

### Task 2: JPA Entities

**Files:**
- Create: `src/main/java/com/servidos/v1/tenant/infrastructure/jpa/RestauranteJpaEntity.java`
- Create: `src/main/java/com/servidos/v1/tenant/infrastructure/jpa/SuscripcionJpaEntity.java` (if not exists, else modify)
- Modify: `src/main/java/com/servidos/v1/tenant/infrastructure/jpa/PlanJpaRepository.java` (ensure exists)

**Interfaces:**
- Consumes: `EstadoRestaurante`, `Suscripcion.EstadoSuscripcion`
- Produces: Entities usadas por Task 3 y 4

- [ ] **Step 1: Crear RestauranteJpaEntity.java**

```java
package com.servidos.v1.tenant.infrastructure.jpa;

import com.servidos.v1.tenant.domain.EstadoRestaurante;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import java.time.LocalDateTime;

@Entity @Table(name = "restaurante", uniqueConstraints = @UniqueConstraint(columnNames = "slug"))
@Data @NoArgsConstructor @AllArgsConstructor
public class RestauranteJpaEntity {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) @Column(name = "restaurante_id") private Long restauranteId;
    @Column(name = "slug", nullable = false, unique = true, length = 100) private String slug;
    @Column(name = "nombre", nullable = false, length = 150) private String nombre;
    @Column(name = "direccion", length = 255) private String direccion;
    @Enumerated(EnumType.STRING) @Column(name = "estado", nullable = false, length = 20) private EstadoRestaurante estado;
    @Column(name = "created_at", nullable = false, updatable = false) private LocalDateTime createdAt;
    @Column(name = "updated_at", nullable = false) private LocalDateTime updatedAt;
    @PrePersist protected void onCreate() { LocalDateTime now = LocalDateTime.now(); this.createdAt = now; this.updatedAt = now; }
    @PreUpdate protected void onUpdate() { this.updatedAt = LocalDateTime.now(); }
}
```

- [ ] **Step 2: Crear/Actualizar SuscripcionJpaEntity.java**

```java
package com.servidos.v1.tenant.infrastructure.jpa;

import com.servidos.v1.tenant.domain.Suscripcion.EstadoSuscripcion;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import java.time.LocalDate;
import java.time.LocalDateTime;

@Entity @Table(name = "suscripcion")
@Data @NoArgsConstructor @AllArgsConstructor
public class SuscripcionJpaEntity {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) @Column(name = "suscripcion_id") private Long suscripcionId;
    @Column(name = "restaurante_id", nullable = false) private Long restauranteId;
    @Column(name = "plan_id", nullable = false) private Long planId;
    @Enumerated(EnumType.STRING) @Column(name = "estado", nullable = false, length = 20) private EstadoSuscripcion estado;
    @Column(name = "fecha_inicio", nullable = false) private LocalDate fechaInicio;
    @Column(name = "fecha_fin", nullable = false) private LocalDate fechaFin;
    @Column(name = "created_at", nullable = false, updatable = false) private LocalDateTime createdAt;
    @Column(name = "updated_at", nullable = false) private LocalDateTime updatedAt;
    @PrePersist protected void onCreate() { LocalDateTime now = LocalDateTime.now(); this.createdAt = now; this.updatedAt = now; }
    @PreUpdate protected void onUpdate() { this.updatedAt = LocalDateTime.now(); }
}
```

- [ ] **Step 3: Verificar compilación**

Run: `mvn compile -DskipTests`
Expected: BUILD SUCCESS

- [ ] **Step 4: Commit**

```bash
git add src/main/java/com/servidos/v1/tenant/infrastructure/jpa/RestauranteJpaEntity.java src/main/java/com/servidos/v1/tenant/infrastructure/jpa/SuscripcionJpaEntity.java
git commit -m "feat(tenant): add Restaurante and Suscripcion JPA entities"
```

---

### Task 3: Repositories

**Files:**
- Modify: `src/main/java/com/servidos/v1/tenant/infrastructure/jpa/RestauranteJpaRepository.java`
- Modify: `src/main/java/com/servidos/v1/tenant/infrastructure/jpa/SuscripcionJpaRepository.java`
- Modify: `src/main/java/com/servidos/v1/tenant/infrastructure/jpa/PlanJpaRepository.java`

**Interfaces:**
- Consumes: Entities from Task 2
- Produces: `existsBySlug`, `findTopByRestauranteIdOrderByCreatedAtDesc` — usadas por Task 5

- [ ] **Step 1: Actualizar RestauranteJpaRepository.java**

```java
package com.servidos.v1.tenant.infrastructure.jpa;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import java.util.Optional;

@Repository
public interface RestauranteJpaRepository extends JpaRepository<RestauranteJpaEntity, Long> {
    boolean existsBySlug(String slug);
    Optional<RestauranteJpaEntity> findBySlug(String slug);
}
```

- [ ] **Step 2: Actualizar SuscripcionJpaRepository.java**

```java
package com.servidos.v1.tenant.infrastructure.jpa;

import com.servidos.v1.tenant.domain.Suscripcion.EstadoSuscripcion;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import java.util.List;
import java.util.Optional;

@Repository
public interface SuscripcionJpaRepository extends JpaRepository<SuscripcionJpaEntity, Long> {
    Optional<SuscripcionJpaEntity> findTopByRestauranteIdOrderByCreatedAtDesc(Long restauranteId);
    List<SuscripcionJpaEntity> findByRestauranteId(Long restauranteId);
    boolean existsByRestauranteIdAndEstado(Long restauranteId, EstadoSuscripcion estado);
}
```

- [ ] **Step 3: Verificar PlanJpaRepository.java tiene existsById (JpaRepository ya lo trae) — si está vacío, agregar extends JpaRepository<PlanJpaEntity, Long>**

- [ ] **Step 4: Verificar compilación**

Run: `mvn compile -DskipTests`
Expected: BUILD SUCCESS

- [ ] **Step 5: Commit**

```bash
git add src/main/java/com/servidos/v1/tenant/infrastructure/jpa/RestauranteJpaRepository.java src/main/java/com/servidos/v1/tenant/infrastructure/jpa/SuscripcionJpaRepository.java
git commit -m "feat(tenant): add tenant repositories with slug and suscripcion queries"
```

---

### Task 4: Mappers

**Files:**
- Create: `src/main/java/com/servidos/v1/tenant/infrastructure/mapper/RestauranteMapper.java`
- Create: `src/main/java/com/servidos/v1/tenant/infrastructure/mapper/SuscripcionMapper.java`

**Interfaces:**
- Consumes: Domain and Entities from Tasks 1-2
- Produces: toDomain/toEntity/updateEntity — usadas por Task 5

- [ ] **Step 1: Crear RestauranteMapper.java**

```java
package com.servidos.v1.tenant.infrastructure.mapper;

import com.servidos.v1.tenant.domain.Restaurante;
import com.servidos.v1.tenant.infrastructure.jpa.RestauranteJpaEntity;
import org.springframework.stereotype.Component;

@Component
public class RestauranteMapper {
    public Restaurante toDomain(RestauranteJpaEntity e) {
        if (e == null) return null;
        return Restaurante.builder().restaurante_id(e.getRestauranteId()).slug(e.getSlug()).nombre(e.getNombre()).direccion(e.getDireccion()).estado(e.getEstado()).created_at(e.getCreatedAt()).updated_at(e.getUpdatedAt()).build();
    }
    public RestauranteJpaEntity toEntity(Restaurante d) {
        if (d == null) return null;
        RestauranteJpaEntity e = new RestauranteJpaEntity();
        e.setRestauranteId(d.getRestaurante_id()); e.setSlug(d.getSlug()); e.setNombre(d.getNombre()); e.setDireccion(d.getDireccion()); e.setEstado(d.getEstado()); return e;
    }
    public void updateEntity(Restaurante domain, RestauranteJpaEntity entity) {
        if (domain == null || entity == null) return;
        entity.setNombre(domain.getNombre()); entity.setDireccion(domain.getDireccion()); entity.setEstado(domain.getEstado());
    }
}
```

- [ ] **Step 2: Crear SuscripcionMapper.java**

```java
package com.servidos.v1.tenant.infrastructure.mapper;

import com.servidos.v1.tenant.domain.Suscripcion;
import com.servidos.v1.tenant.infrastructure.jpa.SuscripcionJpaEntity;
import org.springframework.stereotype.Component;

@Component
public class SuscripcionMapper {
    public Suscripcion toDomain(SuscripcionJpaEntity e) {
        if (e == null) return null;
        return Suscripcion.builder().suscripcion_id(e.getSuscripcionId()).restaurante_id(e.getRestauranteId()).plan_id(e.getPlanId()).estado(e.getEstado()).fecha_inicio(e.getFechaInicio()).fecha_fin(e.getFechaFin()).created_at(e.getCreatedAt()).updated_at(e.getUpdatedAt()).build();
    }
    public SuscripcionJpaEntity toEntity(Suscripcion d) {
        if (d == null) return null;
        SuscripcionJpaEntity e = new SuscripcionJpaEntity();
        e.setSuscripcionId(d.getSuscripcion_id()); e.setRestauranteId(d.getRestaurante_id()); e.setPlanId(d.getPlan_id()); e.setEstado(d.getEstado()); e.setFechaInicio(d.getFecha_inicio()); e.setFechaFin(d.getFecha_fin()); return e;
    }
}
```

- [ ] **Step 3: Verificar compilación**

Run: `mvn compile -DskipTests`
Expected: BUILD SUCCESS

- [ ] **Step 4: Commit**

```bash
git add src/main/java/com/servidos/v1/tenant/infrastructure/mapper/RestauranteMapper.java src/main/java/com/servidos/v1/tenant/infrastructure/mapper/SuscripcionMapper.java
git commit -m "feat(tenant): add restaurante and suscripcion mappers"
```

---

### Task 5: UseCases CrearRestaurante + GestionarSuscripcion + CambiarPlan

**Files:**
- Modify: `src/main/java/com/servidos/v1/tenant/application/CrearRestauranteUseCase.java`
- Modify: `src/main/java/com/servidos/v1/tenant/application/GestionarSuscripcionUseCase.java`
- Modify: `src/main/java/com/servidos/v1/tenant/application/CambiarPlanUseCase.java`

**Interfaces:**
- Consumes: Domain crear(), Mappers, Repositories, EventPublisher, RestauranteCreadoEvent, PlanCambiadoEvent
- Produces: UseCases consumidos por controllers futuros

- [ ] **Step 1: Implementar CrearRestauranteUseCase.java**

```java
package com.servidos.v1.tenant.application;

import com.servidos.v1.tenant.domain.Restaurante;
import com.servidos.v1.tenant.domain.Suscripcion;
import com.servidos.v1.tenant.domain.event.RestauranteCreadoEvent;
import com.servidos.v1.tenant.infrastructure.jpa.RestauranteJpaRepository;
import com.servidos.v1.tenant.infrastructure.jpa.SuscripcionJpaRepository;
import com.servidos.v1.tenant.infrastructure.jpa.PlanJpaRepository;
import com.servidos.v1.tenant.infrastructure.mapper.RestauranteMapper;
import com.servidos.v1.tenant.infrastructure.mapper.SuscripcionMapper;
import com.servidos.v1.shared.event.EventPublisher;
import com.servidos.v1.shared.exception.BusinessException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.time.LocalDate;

@Service @RequiredArgsConstructor
public class CrearRestauranteUseCase {
    private final RestauranteJpaRepository restauranteRepository;
    private final SuscripcionJpaRepository suscripcionRepository;
    private final PlanJpaRepository planRepository;
    private final RestauranteMapper restauranteMapper;
    private final SuscripcionMapper suscripcionMapper;
    private final EventPublisher eventPublisher;
    public record Command(String slug, String nombre, String direccion, Long planId) {}
    @Transactional
    public Restaurante ejecutar(Command cmd) {
        validar(cmd);
        Restaurante domain = Restaurante.crear(cmd.slug(), cmd.nombre(), cmd.direccion());
        var savedR = restauranteRepository.save(restauranteMapper.toEntity(domain));
        Suscripcion s = Suscripcion.crear(savedR.getRestauranteId(), cmd.planId(), LocalDate.now(), LocalDate.now().plusDays(30));
        suscripcionRepository.save(suscripcionMapper.toEntity(s));
        eventPublisher.publish(new RestauranteCreadoEvent(savedR.getRestauranteId(), savedR.getSlug()));
        return restauranteMapper.toDomain(savedR);
    }
    private void validar(Command cmd) {
        if (cmd.slug() == null || cmd.slug().trim().isEmpty()) throw new BusinessException("El slug es obligatorio");
        if (cmd.nombre() == null || cmd.nombre().trim().isEmpty()) throw new BusinessException("El nombre es obligatorio");
        if (restauranteRepository.existsBySlug(cmd.slug().trim().toLowerCase())) throw new BusinessException("El slug ya existe");
        if (cmd.planId() == null || !planRepository.existsById(cmd.planId())) throw new BusinessException("El plan no existe");
    }
}
```

- [ ] **Step 2: Implementar GestionarSuscripcionUseCase.java (suscribir/cancelar/renovar)**

```java
package com.servidos.v1.tenant.application;

import com.servidos.v1.tenant.domain.Suscripcion;
import com.servidos.v1.tenant.domain.Suscripcion.EstadoSuscripcion;
import com.servidos.v1.tenant.infrastructure.jpa.SuscripcionJpaRepository;
import com.servidos.v1.tenant.infrastructure.jpa.PlanJpaRepository;
import com.servidos.v1.tenant.infrastructure.jpa.RestauranteJpaRepository;
import com.servidos.v1.tenant.infrastructure.mapper.SuscripcionMapper;
import com.servidos.v1.shared.exception.BusinessException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.time.LocalDate;

@Service @RequiredArgsConstructor
public class GestionarSuscripcionUseCase {
    private final SuscripcionJpaRepository suscripcionRepository;
    private final RestauranteJpaRepository restauranteRepository;
    private final PlanJpaRepository planRepository;
    private final SuscripcionMapper suscripcionMapper;
    @Transactional public Suscripcion suscribir(Long restauranteId, Long planId) { /* valida no tiene ACTIVA vigente, crea con hoy/hoy+30 */ }
    @Transactional public void cancelar(Long restauranteId) { /* UPDATE ACTIVA -> CANCELADA */ }
    @Transactional public Suscripcion renovar(Long restauranteId, Integer duracionDias) { /* INSERT nueva ACTIVA con fechaInicio = vieja.fechaFin+1 o hoy */ }
}
```

(Implementar cada método con validaciones null/blank antes de DB y uso de Suscripcion.crear + mapper + repository.save, siguiendo patrón de CrearRestaurante)

- [ ] **Step 3: Implementar CambiarPlanUseCase.java**

```java
package com.servidos.v1.tenant.application;

import com.servidos.v1.tenant.domain.Suscripcion;
import com.servidos.v1.tenant.domain.event.PlanCambiadoEvent;
import com.servidos.v1.tenant.infrastructure.jpa.SuscripcionJpaRepository;
import com.servidos.v1.tenant.infrastructure.jpa.PlanJpaRepository;
import com.servidos.v1.shared.event.EventPublisher;
import com.servidos.v1.shared.exception.BusinessException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.time.LocalDate;

@Service @RequiredArgsConstructor
public class CambiarPlanUseCase {
    private final SuscripcionJpaRepository suscripcionRepository;
    private final PlanJpaRepository planRepository;
    private final EventPublisher eventPublisher;
    public record Command(Long restauranteId, Long nuevoPlanId) {}
    @Transactional public Suscripcion ejecutar(Command cmd) { /* valida, cierra ACTIVA, crea nueva ACTIVA con nuevo plan, publish */ }
}
```

- [ ] **Step 4: Verificar compilación**

Run: `mvn compile -DskipTests`
Expected: BUILD SUCCESS

- [ ] **Step 5: Commit**

```bash
git add src/main/java/com/servidos/v1/tenant/application/CrearRestauranteUseCase.java src/main/java/com/servidos/v1/tenant/application/GestionarSuscripcionUseCase.java src/main/java/com/servidos/v1/tenant/application/CambiarPlanUseCase.java
git commit -m "feat(tenant): implement CrearRestaurante, GestionarSuscripcion and CambiarPlan use cases"
```

---

### Task 6: Verificación final

- [ ] **Step 1: Compilar todo el proyecto**

Run: `mvn compile -DskipTests`
Expected: BUILD SUCCESS

```

