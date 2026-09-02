---
title: Reporting
date: 2026-08-30
tags:
  - servidos
  - modulo
  - reporting
aliases:
  - Módulo Reporting
---

# Reporting

> [!todo] Estado: Implementado mínimo — sin tabla propia
> Parte de [[ServidOS - Módulos Implementados]] · `PROJECT_CONTEXT:59`

## Qué es

Módulo de **reportes desacoplado**: no tiene dominio ni tablas propias en MVP. Solo **reacciona a eventos** de otros módulos para generar reportes por rango de fechas. `cierre_caja` explícitamente descartado para v1.0 (`PROJECT_CONTEXT:97`).

## Qué hace

- **Listeners:** `PedidoCreadoListener.on(PedidoCreadoEvent)` y `PagoRegistradoListener.on(PagoRegistradoEvent)` con `@EventListener` y `log.info` — base para agregado de ventas
- **Reporte:** `GenerarReporteVentas.generar(desde, hasta, restauranteId)` stub con `log.info` — TODO: `sum(total)` filtrando `pedido.created_at` / `pago.fecha_pago` entre `desde/hasta` comparando contra **fecha local del restaurante**, no UTC del servidor

## Clases clave

| Clase | Qué es | Nota |
|-------|--------|------|
| `PedidoCreadoListener` | `@Component @EventListener` | `log.info pedidoId/restauranteId` |
| `PagoRegistradoListener` | `@Component @EventListener` | `log.info pagoId/pedidoId/restauranteId` |
| `GenerarReporteVentas` | `@Service` | stub `generar(LocalDate, LocalDate, Long)` |

## Relaciones

- **← `ordering`:** Escucha `PedidoCreadoEvent` (pedidoId, restauranteId)
- **← `payment`:** Escucha `PagoRegistradoEvent` (pagoId, pedidoId, restauranteId)
- **← `tenant`:** `restauranteId` para filtrar reportes por tenant
- **No tiene** `*JpaEntity` ni `*JpaRepository` — cuando necesite persistir agregados, se agregará tabla `reporte_ventas` sin tocar `pedido/pago`

## Decisiones

- Sin `audit_log` en MVP (gateado por plan, `BD.sql:281`)
- Sin `cierre_caja` — reportes por filtro de fecha, no por turno
- Listeners sin tabla, solo `log` en MVP

