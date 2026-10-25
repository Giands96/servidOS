package com.servidos.v1.kitchen.api.dto;

import com.servidos.v1.ordering.domain.EstadoPedido;
import com.servidos.v1.ordering.domain.TipoPedido;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

/** {@code numeroMesa}: número actual de la mesa, o null sin mesa. */
public record PreparacionPedidoResponse(
        Long pedidoId,
        Long mesaId,
        Integer numeroMesa,
        TipoPedido tipoPedido,
        EstadoPedido estado,
        String observacion,
        BigDecimal total,
        LocalDateTime createdAt,
        LocalDateTime listoAt,
        List<CocinaDetalleResponse> items) {}
