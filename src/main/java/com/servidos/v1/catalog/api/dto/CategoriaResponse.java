package com.servidos.v1.catalog.api.dto;

import com.servidos.v1.catalog.domain.EstadoCategoria;

public record CategoriaResponse(
        Long categoriaId,
        String nombre,
        EstadoCategoria estado) {
}
