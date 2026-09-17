package com.servidos.v1.ordering.api.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

public record CrearPedidoItemRequest(
        @NotNull Long productoId,
        @NotNull @Min(1) Integer cantidad,
        String observacion) {}
