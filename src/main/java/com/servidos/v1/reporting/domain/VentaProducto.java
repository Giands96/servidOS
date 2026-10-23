package com.servidos.v1.reporting.domain;

import java.math.BigDecimal;

/** Lo vendido de un producto en el período: unidades y monto. */
public record VentaProducto(Long productoId, String nombre, long cantidad, BigDecimal monto) {}
