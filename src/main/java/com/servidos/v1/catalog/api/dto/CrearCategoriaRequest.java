package com.servidos.v1.catalog.api.dto;

import com.servidos.v1.catalog.domain.EstadoCategoria;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public record CrearCategoriaRequest(
        @NotBlank String nombre,
        @NotNull EstadoCategoria estado) {
}
