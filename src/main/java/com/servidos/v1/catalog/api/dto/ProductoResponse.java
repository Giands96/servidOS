package com.servidos.v1.catalog.api.dto;

import com.servidos.v1.catalog.domain.EstadoProducto;
import java.math.BigDecimal;

public record ProductoResponse(
        Long productoId,
        Long categoriaId,
        String nombre,
        String descripcion,
        String imagenUrl,
        BigDecimal precio,
        Integer tiempoPreparacion,
        EstadoProducto estado) {
}
