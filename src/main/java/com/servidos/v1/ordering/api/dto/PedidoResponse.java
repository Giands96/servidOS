package com.servidos.v1.ordering.api.dto;

import com.servidos.v1.ordering.domain.EstadoPedido;
import com.servidos.v1.ordering.domain.TipoPedido;

import java.math.BigDecimal;

/** {@code numeroMesa}: número actual de la mesa, o null sin mesa. */
public record PedidoResponse(
        Long pedidoId,
        TipoPedido tipoPedido,
        Long mesaId,
        Integer numeroMesa,
        EstadoPedido estado,
        BigDecimal total) {}
