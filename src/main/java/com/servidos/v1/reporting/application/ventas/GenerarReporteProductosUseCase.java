package com.servidos.v1.reporting.application.ventas;

import com.servidos.v1.reporting.application.ReporteVentasQuery;
import com.servidos.v1.reporting.domain.Periodo;
import com.servidos.v1.shared.exception.BusinessException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;

/** Ranking de productos vendidos en el período que contiene la fecha, con el resumen de ventas. */
@Service
@RequiredArgsConstructor
public class GenerarReporteProductosUseCase {

    private final ReporteVentasQuery query;

    @Transactional(readOnly = true)
    public ReporteProductos generar(Long restauranteId, Periodo periodo, LocalDate fecha) {
        if (restauranteId == null) throw new BusinessException("Restaurante no identificado");
        if (periodo == null) throw new BusinessException("El período es obligatorio");
        var rango = periodo.rango(fecha);

        var resumen = query.resumen(restauranteId, rango);
        var productos = query.productosVendidos(restauranteId, rango);
        return new ReporteProductos(periodo, rango, resumen, productos);
    }
}
