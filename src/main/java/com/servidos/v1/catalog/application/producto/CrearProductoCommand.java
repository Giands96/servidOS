package com.servidos.v1.catalog.application.producto;

import com.servidos.v1.catalog.domain.EstadoProducto;
import java.math.BigDecimal;

public record CrearProductoCommand(
        String nombre,
        String descripcion,
        Long categoriaId,
        BigDecimal precio,
        Integer tiempoPreparacion,
        EstadoProducto estado,
        String imagenUrl
) {}
