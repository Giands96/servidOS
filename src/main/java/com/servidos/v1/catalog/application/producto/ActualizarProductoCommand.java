package com.servidos.v1.catalog.application.producto;

import com.servidos.v1.catalog.domain.EstadoProducto;
import java.math.BigDecimal;

public record ActualizarProductoCommand(
        Long productoId,
        String nombre,
        String descripcion,
        Long categoriaId,
        String imagenUrl,
        BigDecimal precio,
        Integer tiempoPreparacion,
        EstadoProducto estado
) {}
