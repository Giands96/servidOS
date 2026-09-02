---
title: ServidOS - Estado Actual
date: 2026-08-27
tags:
  - servidos
  - estado
  - roadmap
aliases:
  - Estado ServidOS
  - Roadmap ServidOS
---

# ServidOS — Estado Actual

Parte de [[ServidOS - MOC]] · Pendientes: [[ServidOS - Pendientes]] · Decisiones: [[ServidOS - Decisiones]]

## Qué está hecho

- [x] Estructura de carpetas backend creada y verificada contra boundaries de módulo — ver [[ServidOS - Arquitectura#Estructura por módulo de negocio]]
- [x] Schema DBML v1.0/MVP cerrado (`restaurant-saas-schema.dbml`) con correcciones + `tiempo_preparacion` — ver [[ServidOS - Modelo de Datos#Correcciones cerradas]] y `BD.sql`
- [x] Discusión `@Entity` en dominio resuelta a nivel principio — ver [[ServidOS - Autenticacion y Seguridad#Sobre Entity]]
- [x] Documentación vault Obsidian inicializada (`notes/` con MOC + 9 notas linkeadas) — ver [[ServidOS - MOC]]
- [x] **Módulos vertical slice (enfoque A) — ver [[ServidOS - Módulos Implementados]]:**
  - [x] `catalog` — `CrearProducto/ActualizarProducto/GestionarCategoria` con `BusinessException` + `Mapper` sin mutar `restaurante_id`
  - [x] `ordering` — `CrearPedido` con `Command.Item` anidado, `estado` variable, `precio` anti-tamper, `TipoPedido.MESA`
  - [x] `tenant` — `CrearRestaurante/GestionarSuscripcion/CambiarPlan` con `LocalDate`, `ACTIVA/CANCELADA` sin `INACTIVA`
  - [x] `identity` — `CrearUsuario/Login` con `BCrypt`, `UsuarioRestaurante` PK `usuario_id`, `JwtService` stub (`HttpOnly` deferido)
  - [x] `payment` — `RegistrarPago` con `vuelto` solo `EFECTIVO`, `referencia_externa` para voucher Izipay, POS desconectado
  - [x] `kitchen` — `PreparacionPedido` proyección sin tabla, `GestionarColaCocina` (WebSocket deferido)
  - `BD.sql` actualizado: `producto.tiempo_preparacion`, `suscripcion` a `LocalDate`

> [!success] Vault
> Grafo: MOC ↔ Contexto ↔ Stack ↔ Arquitectura ↔ Multi-tenancy ↔ Modelo ↔ Auth ↔ Eventos ↔ Decisiones ↔ Pendientes ↔ Estado ↔ Módulos Implementados
> Módulos: ![[ServidOS - Módulos Implementados#Mapa]]

## Próximo trabajo

Próximo trabajo natural (en orden):

1. **Ejecutar `kitchen` plan** — ver `docs/superpowers/plans/2026-08-30-kitchen-module.md`
2. **Reporting listeners** — `GenerarReporteVentas` + `PedidoCreadoListener/PagoRegistradoListener`
3. **Sesiones de enseñanza deferidas:** `HttpOnly + refresh` (`JwtService`) y `WebSockets` (`WebSocketConfig`)

> [!tip] Patrón aplicado
> Ver [[ServidOS - Módulos Implementados#Decisiones transversales]] para `vuelto`, `INACTIVA`, `MESA` vs `LOCAL`.

^proximo-trabajo

## Queries

![[ServidOS - Pendientes#Pendientes importantes]]

## Links

- Fuente: `PROJECT_CONTEXT.md` §10
- MOC: [[ServidOS - MOC]]
