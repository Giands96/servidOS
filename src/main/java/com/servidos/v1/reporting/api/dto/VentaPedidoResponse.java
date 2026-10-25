package com.servidos.v1.reporting.api.dto;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/** Fila del reporte de ventas: un pedido cobrado. {@code mesaId} y {@code numeroMesa} son null sin mesa. */
public record VentaPedidoResponse(
        Long pedidoId,
        LocalDateTime fechaPago,
        String tipoPedido,
        String metodoPago,
        BigDecimal total,
        Long mesaId,
        Integer numeroMesa) {}
