package com.servidos.v1.reporting.api.dto;

import java.math.BigDecimal;

/** Fila del ranking de productos; {@code posicion} empieza en 1. */
public record VentaProductoResponse(
        int posicion,
        Long productoId,
        String nombre,
        long cantidad,
        BigDecimal monto) {}
