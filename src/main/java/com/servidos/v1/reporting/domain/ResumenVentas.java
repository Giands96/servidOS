package com.servidos.v1.reporting.domain;

import java.math.BigDecimal;

/** Cantidad de ventas del período y el total vendido ({@code 0} si no hubo). */
public record ResumenVentas(long cantidadVentas, BigDecimal totalVendido) {}
