package com.servidos.v1.ordering.api.dto;

import com.servidos.v1.ordering.domain.EstadoPedido;
import jakarta.validation.constraints.NotNull;

public record CambiarEstadoRequest(
        @NotNull EstadoPedido estado) {}
