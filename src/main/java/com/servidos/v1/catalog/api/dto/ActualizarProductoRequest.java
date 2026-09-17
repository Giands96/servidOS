package com.servidos.v1.catalog.api.dto;

import com.servidos.v1.catalog.domain.EstadoProducto;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import java.math.BigDecimal;

public record ActualizarProductoRequest(
        @NotBlank String nombre,
        String descripcion,
        Long categoriaId,
        String imagenUrl,
        @NotNull @Positive BigDecimal precio,
        @Positive Integer tiempoPreparacion,
        EstadoProducto estado) {
}
