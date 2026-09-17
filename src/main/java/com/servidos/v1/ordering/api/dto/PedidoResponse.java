package com.servidos.v1.ordering.api.dto;

import com.servidos.v1.ordering.domain.EstadoPedido;
import com.servidos.v1.ordering.domain.TipoPedido;

import java.math.BigDecimal;

public record PedidoResponse(
        Long pedidoId,
        TipoPedido tipoPedido,
        Long mesaId,
        EstadoPedido estado,
        BigDecimal total) {}
