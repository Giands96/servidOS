package com.servidos.v1.catalog.api.dto;

import com.servidos.v1.catalog.domain.EstadoProducto;
import jakarta.validation.constraints.NotNull;

public record CambiarEstadoProductoRequest(@NotNull EstadoProducto estado) {
}
