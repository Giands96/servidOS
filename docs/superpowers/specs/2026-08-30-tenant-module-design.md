# Design Spec — Tenant Module (CrearRestaurante + GestionarSuscripcion + CambiarPlan) — 2026-08-30

## Contexto
Módulo `tenant` es base del multi-tenancy ServidOS. Tras `ordering` (enfoque A vertical slice), se replica patrón hexagonal para `tenant` con 4 flujos MVP: Crear Restaurante + Suscribir, Cancelar, Renovar/Extender y CambiarPlan. BD.sql es fuente de verdad; Suscripcion usa LocalDate y solo ACTIVA/CANCELADA (sin INACTIVA), tiempo_preparacion ya agregado a producto.

## 1. Arquitectura y Límites
```
tenant/
├── domain/ Restaurante + Suscripcion (@Builder, crear()) + Plan/Funcionalidad (solo lectura)
├── domain/event/ RestauranteCreadoEvent + PlanCambiadoEvent
├── infrastructure/jpa/ RestauranteJpaEntity + SuscripcionJpaEntity (@Entity, @PrePersist) + RestauranteJpaRepository + SuscripcionJpaRepository + PlanJpaRepository
├── infrastructure/mapper/ RestauranteMapper + SuscripcionMapper (@Component, nunca muta restaurante_id)
└── application/ CrearRestauranteUseCase + GestionarSuscripcionUseCase (suscribir/cancelar/renovar) + CambiarPlanUseCase
```
- tenant no conoce ordering/identity; expone restaurante_id.
- Plan/Funcionalidad solo lectura en MVP; plan_funcionalidad es pivot no modelada.
- Suscripcion siempre INSERT nueva ACTIVA para renovar/cambiar plan (preserva historial); cancelar es UPDATE ACTIVA->CANCELADA.

## 2. Modelo de Dominio y Validaciones

### Restaurante.crear(slug, nombre, direccion)
- slug != null, 3-100 chars, regex ^[a-z0-9-]+$, toLowerCase trim
- nombre != null 3-150 chars
- estado siempre ACTIVO

### Suscripcion.crear(restauranteId, planId, fechaInicio, fechaFin)
- restauranteId/planId != null, fechaInicio != null, fechaFin > fechaInicio
- estado siempre ACTIVA

### CrearRestauranteUseCase.Command(String slug, String nombre, String direccion, Long planId)
```
@Transactional ejecutar(Command cmd):
  validar: slug/nombre blank -> existsBySlug -> plan exists
  r = Restaurante.crear(slug.trim().toLowerCase(), nombre.trim(), direccion)
  savedR = restauranteRepository.save(mapper.toEntity(r))
  s = Suscripcion.crear(savedR.getRestauranteId(), cmd.planId(), LocalDate.now(), LocalDate.now().plusDays(30))
  suscripcionRepository.save(mapper.toEntity(s))
  publish(RestauranteCreadoEvent)
```

### GestionarSuscripcionUseCase
- suscribir(restauranteId, planId): solo si no tiene ACTIVA vigente
- cancelar(restauranteId): busca ACTIVA más reciente, UPDATE estado=CANCELADA (o fecha_fin=hoy), si no hay -> BusinessException
- renovar(restauranteId, duracionDias): desbloqueado solo si tiene ACTIVA; si vigente, fechaInicio = vieja.fechaFin.plusDays(1), else hoy; INSERT nueva ACTIVA

### CambiarPlanUseCase.Command(Long restauranteId, Long nuevoPlanId)
- valida restaurante existe, plan existe ACTIVO, tiene ACTIVA
- cierra ACTIVA actual (UPDATE CANCELADA)
- crea nueva ACTIVA con nuevoPlanId, fechaInicio=hoy, fechaFin=hoy+duracionNuevoPlan
- publish(PlanCambiadoEvent)

Todos los casos validan null/blank antes de DB y usan BusinessException.

## 3. Persistencia y Mappers

### Entities
- RestauranteJpaEntity -> restaurante (restaurante_id IDENTITY, slug unique, nombre 150, direccion 255, estado ACTIVO/INACTIVO, created_at/updated_at @PrePersist/@PreUpdate)
- SuscripcionJpaEntity -> suscripcion (suscripcion_id IDENTITY, restaurante_id FK, plan_id FK, estado ACTIVA/CANCELADA, fecha_inicio LocalDate, fecha_fin LocalDate, created_at/updated_at)

### Repositories
```
RestauranteJpaRepository: existsBySlug, findBySlug, findByRestauranteId
SuscripcionJpaRepository: findTopByRestauranteIdOrderByCreatedAtDesc, findByRestauranteId, existsByRestauranteIdAndEstado
PlanJpaRepository: existsById
```

### Mappers
- toDomain mapea created_at/updated_at; toEntity usa new+setters; updateEntity nunca toca restaurante_id.

### Transacción
- @Transactional en cada UseCase; Crear guarda Restaurante primero para FK.

## 4. Testing — Deferido
Sin dependencias test en este ciclo, igual que ordering.

## Fuera de Alcance
- Funcionalidad/plan_funcionalidad escritura, mesa ya modelada en ordering, audit_log deferido, vuelto solo efectivo (ordering/payment).

