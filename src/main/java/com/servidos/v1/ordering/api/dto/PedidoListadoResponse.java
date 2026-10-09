package com.servidos.v1.ordering.api.dto;

import com.servidos.v1.ordering.domain.EstadoPedido;
import com.servidos.v1.ordering.domain.TipoPedido;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/** Fila del listado de pedidos. {@code estadoPago}: PAGADO, REEMBOLSADO o null si no tiene pago. */
public record PedidoListadoResponse(
        Long pedidoId,
        TipoPedido tipoPedido,
        Long mesaId,
        EstadoPedido estado,
        BigDecimal total,
        String observacion,
        LocalDateTime createdAt,
        String estadoPago) {}
