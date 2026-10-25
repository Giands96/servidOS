package com.servidos.v1.reporting.domain;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/** Una venta: pedido cobrado y no cancelado. {@code mesaId} y {@code numeroMesa} son null sin mesa. */
public record VentaPedido(Long pedidoId, LocalDateTime fechaPago, String tipoPedido, String metodoPago,
                          BigDecimal total, Long mesaId, Integer numeroMesa) {}
