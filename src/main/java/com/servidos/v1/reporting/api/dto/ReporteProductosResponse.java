package com.servidos.v1.reporting.api.dto;

import com.servidos.v1.reporting.domain.Periodo;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

/** Ranking de productos del período, con el resumen de ventas. */
public record ReporteProductosResponse(
        Periodo periodo,
        LocalDate desde,
        LocalDate hasta,
        long cantidadVentas,
        BigDecimal totalVendido,
        List<VentaProductoResponse> productos) {}
