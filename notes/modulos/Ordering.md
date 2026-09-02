---
title: Ordering
date: 2026-08-30
tags:
  - servidos
  - modulo
  - ordering
aliases:
  - Módulo Ordering
---

# Ordering — [[ServidOS - Módulos Implementados#Ordering — Verde]]

> [!info] Item anidado
> `Command` con `record Item(productoId, cantidad, observacion)` — ver [[ServidOS - Módulos Implementados#Ordering — Verde]].

- **Dominio:** `Pedido.crear` (estado variable, default `PENDIENTE`) + `DetallePedido.crear` (`subtotal = precio*cantidad`)
- **Persistencia:** `PedidoJpaEntity` / `DetallePedidoJpaEntity` (`restaurante_id` denormalizado)
- **UseCase:** `CrearPedidoUseCase` con `estado` variable desde front, `precio` anti-tamper desde `Producto`

## Links
- [[ServidOS - Módulos Implementados#Ordering — Verde]] · `BD.sql#pedido` · `BD.sql#detalle_pedido`

