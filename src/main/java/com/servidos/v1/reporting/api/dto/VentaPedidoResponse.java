package com.servidos.v1.reporting.api.dto;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/** Fila del reporte de ventas: un pedido cobrado. */
public record VentaPedidoResponse(
        Long pedidoId,
        LocalDateTime fechaPago,
        String tipoPedido,
        String metodoPago,
        BigDecimal total) {}
