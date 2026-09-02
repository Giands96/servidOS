---
title: ServidOS - Modelo de Datos
date: 2026-08-27
tags:
  - servidos
  - modelo-datos
  - dbml
aliases:
  - Modelo de Datos ServidOS
  - Schema DBML
---

# ServidOS — Modelo de Datos

Parte de [[ServidOS - MOC]] · Fuente: `restaurant-saas-schema.dbml` (DBML v1.0 MVP) · Arquitectura: [[ServidOS - Arquitectura]]

## Tablas base

`Restaurante`, `Suscripcion`, `Plan`, `Funcionalidad`, `Plan_Funcionalidad` (N:M), `Usuario`, `Rol`, `Producto`, `Categoria`, `Mesa` (opcional), `Pedido`, `Detalle_Pedido`, `Pago`.

Ver [[ServidOS - Multi-tenancy]] para `restaurante_id` denormalizado.

## Correcciones cerradas

> [!important] Cambios sobre el diseño inicial — no volver atrás sin revisión

### usuario — identidad pura

Ya **no** tiene `restaurante_id` ni `rol_id` directo.

### usuario_restaurante y usuario_plataforma — joined-table inheritance

- PK = `usuario_id` simple (no compuesta) → fuerza **1:1 hoy** a nivel BD. ^usuario_restaurante
- Upgradable a M:N cambiando PK a compuesta. Ver [[ServidOS - Pendientes#Multi-restaurante]].
- Incluyen `estado` y `updated_at` para revocar sin perder auditoría. Soft delete preferido.

### detalle_pedido

Incluye `restaurante_id` denormalizado — mismo criterio que `pago`. Ver [[ServidOS - Multi-tenancy]].

### pago

Campos de comprobante/facturación **inline**, no tabla aparte (YAGNI). Ver [[ServidOS - Decisiones]].

### suscripcion — simplificada (KISS) + alineada a código 2026-08-30

- Columnas: `fecha_inicio` / `fecha_fin` / `estado (activa | cancelada)` — **sin** `en_gracia` ni `fecha_gracia_hasta`. **En código:** `LocalDate` (no `LocalDateTime`) + `EstadoSuscripcion ACTIVA/CANCELADA` sin `INACTIVA` (vencimiento se calcula, no se persiste).
- Vencimiento calculado en runtime: `fecha_fin - hoy` (puede quedar negativo indefinidamente).
- Acceso solo se corta con `estado = cancelada` manual por Gian.
- Reactivación = **INSERT nueva fila** (nunca UPDATE), preserva historial.
- Duración = `fecha_inicio + N días` (no meses/INTERVAL).
- **Relación con [[modulos/Tenant]]:** `Tenant.Suscripcion.crear()` valida `fechaFin > fechaInicio` y `estado ACTIVA`.

> [!note] Pagos de suscripción
> Restaurante → plataforma: **100% externos/manuales**. Gian confirma fuera del sistema y crea fila en `suscripcion`. Sin tabla en MVP.

### producto — tiempo_preparacion agregado

- **BD.sql:** `producto` ahora con `tiempo_preparacion int [nullable]` (minutos estimados, para gestión de cocina) — no estaba en DBML original.
- **Código:** `Producto.tiempo_preparacion` validado `>0` en `Producto.crear/actualizar`, `ProductoJpaEntity.tiempoPreparacion` nullable — ver [[modulos/Catalog]].
- **Estado:** Se mantiene `EstadoProducto DISPONIBLE/AGOTADO` (alias de `activo/inactivo` de `estado_registro`).

### categoria / usuario / pago — enums alias

- `categoria.estado`, `usuario.estado`, `pago.estado` en BD son `estado_registro / estado_pago` (`activo/inactivo` / `pendiente/pagado/anulado`), pero en código se mantienen `HABILITADO/DESHABILITADO`, `DISPONIBLE/AGOTADO`, `PENDIENTE/PAGADO/CANCELADO` por expresividad — ver [[ServidOS - Decisiones]] y `notes/modulos/*`.

### Fuera del MVP

- Facturación electrónica **SUNAT** (boleta/factura) — sin campos en `pago`.
- Notificación **WhatsApp** vencimiento — stateless, sin tabla.
- `cierre_caja` — **descartada v1.0** por sobreingeniería. Reportes por filtro `pedido.created_at` / `pago.fecha_pago` comparando **fecha local del restaurante, no UTC**. Ver [[ServidOS - Pendientes#cierre_caja]].

### estado_pedido / tipo_pedido — alineados a código

- **BD.sql propone:** `tipo_pedido delivery/recojo/local` y `estado_pedido pendiente_confirmacion/confirmado/cancelado/en_preparacion/listo/entregado` (6).
- **Código mantiene:** `TipoPedido DELIVERY/RECOJO/MESA` (`MESA` en vez de `LOCAL` por gestionabilidad con `mesa_id` nullable) y `EstadoPedido PENDIENTE/PREPARACION/EN_PREPARACION/LISTO/ENTREGA/CANCELADO` (6 con alias, 4 originales + 2 para `kitchen`). Ver [[modulos/Ordering]] y [[modulos/Kitchen]].
- **Decisión:** No se migra a 6 exactos de BD.sql — se mantiene `MESA` y 4 estados base para no romper `ordering`.

### detalle_pedido / pago — detalles finos

- `detalle_pedido` en BD solo `created_at`, en código se mantiene `updated_at` (útil para auditoría) — ver [[modulos/Ordering]].
- `pago` en BD `vuelto` + `referencia_externa` (voucher Izipay) — en código `vuelto` nullable solo para `EFECTIVO`, `referencia_externa` opcional — ver [[modulos/Payment]] y `PAYMENT -> Izipay POS desconectado`.
- `mesa` existe en BD (`estado libre/ocupada/reservada`) pero no tiene entity en MVP — `pedido.mesa_id` es `Long` plano.

### estado_pedido (enum)

Valores derivados del BPMN — **pendiente de confirmación/ajuste por Gian**. Ver [[ServidOS - Pendientes#estado_pedido]].

### Auditoría

Soft deletes + columna `estado` preferidos sobre hard deletes. `audit_log` formal (id, usuario_id, restaurante_id, accion, entidad, entidad_id, fecha) → planes avanzados, **no MVP**. Ver [[ServidOS - Pendientes#audit_log]].

## Módulos y responsabilidades — ver vault `notes/modulos/`

| Módulo | Entidades | Nota |
|--------|-----------|------|
| `tenant` | Restaurante, Suscripción, Plan, Funcionalidad | [[modulos/Tenant]] — `LocalDate`, sin `INACTIVA` |
| `identity` | Usuario, Rol — sin entidad Empleado separada (`Usuario + Rol + Restaurante`) | [[modulos/Identity]] — PK `usuario_id` compartida, `JwtService` stub |
| `catalog` | Producto, Categoría | [[modulos/Catalog]] — `tiempo_preparacion` extra |
| `ordering` | Pedido, DetallePedido | [[modulos/Ordering]] — `restaurante_id` denormalizado, `Item` anidado |
| `payment` | Pago | [[modulos/Payment]] — `vuelto` solo efectivo, `referencia_externa` |
| `kitchen` | Proyección, sin tabla — ver [[ServidOS - Eventos y Flujos]] | [[modulos/Kitchen]] — `PreparacionPedido` proyección |
| `reporting` | Sin tabla propia, solo listeners | [[modulos/Reporting]] — `PedidoCreadoListener` |

> [!info] Vault reordenado
> Detalle por módulo en `notes/modulos/` — ver [[ServidOS - Módulos Implementados]]

^modelo-datos-correcciones
