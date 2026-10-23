package com.servidos.v1.reporting.api.dto;

import com.servidos.v1.reporting.domain.Periodo;
import org.springframework.data.domain.Page;

import java.math.BigDecimal;
import java.time.LocalDate;

/** Ventas del período: {@code desde}/{@code hasta} (inclusive), resumen y una página de pedidos. */
public record ReporteVentasResponse(
        Periodo periodo,
        LocalDate desde,
        LocalDate hasta,
        long cantidadVentas,
        BigDecimal totalVendido,
        Page<VentaPedidoResponse> ventas) {}
