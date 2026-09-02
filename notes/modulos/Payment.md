---
title: Payment
date: 2026-08-30
tags:
  - servidos
  - modulo
  - payment
aliases:
  - Módulo Payment
---

# Payment — [[ServidOS - Módulos Implementados#Payment — Verde]]

> [!note] Izipay POS desconectado
> Registro manual, `vuelto` solo para `EFECTIVO`.

- `Pago.crear` con `vuelto` nullable + `referenciaExterna`
- `RegistrarPagoUseCase` con `monto = pedido.total`
- Spec: `docs/superpowers/specs/2026-08-30-payment-module-design.md`

