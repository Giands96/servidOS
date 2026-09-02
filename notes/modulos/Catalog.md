---
title: Catalog
date: 2026-08-30
tags:
  - servidos
  - modulo
  - catalog
aliases:
  - Módulo Catalog
cssclasses:
  - module
---

# Catalog — [[ServidOS - Módulos Implementados#Catalog — Verde]]

> [!success] Estado: Verde ✅
> Patrón hexagonal idéntico a todos los módulos — ver [[ServidOS - Módulos Implementados]].

## Qué implementa
- `Producto.crear/actualizar` + `Categoria.crear` con `@Builder` y `BusinessException`
- `ProductoJpaEntity` / `CategoriaJpaEntity` con `@PrePersist` y `unique(restaurante_id, nombre)`
- `ProductoMapper` / `CategoriaMapper` — `updateEntity` nunca toca `restaurante_id`
- `CrearProductoUseCase`, `ActualizarProductoUseCase`, `GestionarCategoriaUseCase` (`restauranteId` del JWT)

## Specs y BD
- Spec: `docs/superpowers/specs/2026-08-30-ordering-module-design.md` (plantilla)
- BD: `BD.sql#producto` (+ `tiempo_preparacion` extra) y `BD.sql#categoria`

## Links
- [[ServidOS - Módulos Implementados#Catalog — Verde]] · [[ServidOS - Modelo de Datos]] · [[ServidOS - Multi-tenancy]]

