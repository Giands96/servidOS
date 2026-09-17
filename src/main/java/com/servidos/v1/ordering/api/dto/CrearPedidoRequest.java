package com.servidos.v1.ordering.api.dto;

import com.servidos.v1.ordering.domain.TipoPedido;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;

import java.util.List;

public record CrearPedidoRequest(
        @NotNull TipoPedido tipoPedido,
        Long mesaId,
        String observacion,
        String repartidorNombre,
        @NotEmpty @Valid List<CrearPedidoItemRequest> items) {}
