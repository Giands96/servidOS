package com.servidos.v1.kitchen.api.dto;

import com.servidos.v1.ordering.domain.EstadoPedido;
import com.servidos.v1.ordering.domain.TipoPedido;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

public record PreparacionPedidoResponse(
        Long pedidoId,
        Long mesaId,
        TipoPedido tipoPedido,
        EstadoPedido estado,
        String observacion,
        BigDecimal total,
        LocalDateTime createdAt,
        LocalDateTime listoAt,
        List<CocinaDetalleResponse> items) {}
