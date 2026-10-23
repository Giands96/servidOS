package com.servidos.v1.reporting.application.ventas;

import com.servidos.v1.reporting.application.ReporteVentasQuery;
import com.servidos.v1.reporting.domain.OrdenVentas;
import com.servidos.v1.reporting.domain.Periodo;
import com.servidos.v1.shared.exception.BusinessException;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;

/** Ventas pagadas del período que contiene la fecha, con su resumen, paginadas y ordenadas. */
@Service
@RequiredArgsConstructor
public class GenerarReporteVentasUseCase {

    static final int TAMANO_MAXIMO = 100;

    private final ReporteVentasQuery query;

    /** @param orden null equivale a {@link OrdenVentas#POR_DEFECTO} */
    @Transactional(readOnly = true)
    public ReporteVentas generar(Long restauranteId, Periodo periodo, LocalDate fecha, OrdenVentas orden,
                                 int pagina, int tamano) {
        if (restauranteId == null) throw new BusinessException("Restaurante no identificado");
        if (periodo == null) throw new BusinessException("El período es obligatorio");
        if (pagina < 0) throw new BusinessException("La página no puede ser negativa");
        if (tamano < 1 || tamano > TAMANO_MAXIMO) {
            throw new BusinessException("El tamaño de página debe estar entre 1 y " + TAMANO_MAXIMO);
        }
        var rango = periodo.rango(fecha);
        var criterio = orden == null ? OrdenVentas.POR_DEFECTO : orden;

        var resumen = query.resumen(restauranteId, rango);
        var ventas = query.ventas(restauranteId, rango, criterio, PageRequest.of(pagina, tamano));
        return new ReporteVentas(periodo, rango, resumen, ventas);
    }
}
