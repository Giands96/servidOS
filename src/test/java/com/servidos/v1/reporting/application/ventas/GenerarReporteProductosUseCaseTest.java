package com.servidos.v1.reporting.application.ventas;

import com.servidos.v1.reporting.application.ReporteVentasQuery;
import com.servidos.v1.reporting.domain.Periodo;
import com.servidos.v1.reporting.domain.RangoFechas;
import com.servidos.v1.reporting.domain.ResumenVentas;
import com.servidos.v1.reporting.domain.VentaProducto;
import com.servidos.v1.shared.exception.BusinessException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class GenerarReporteProductosUseCaseTest {

    static final LocalDate FECHA = LocalDate.of(2026, 10, 23);

    @Mock ReporteVentasQuery query;
    @InjectMocks GenerarReporteProductosUseCase useCase;

    @Test
    void armaElRankingDelPeriodoConResumen() {
        var rango = new RangoFechas(LocalDate.of(2026, 10, 1), LocalDate.of(2026, 10, 31));
        var ceviche = new VentaProducto(3L, "Ceviche", 5, new BigDecimal("125.00"));
        var resumen = new ResumenVentas(4, new BigDecimal("200.00"));
        when(query.resumen(100L, rango)).thenReturn(resumen);
        when(query.productosVendidos(100L, rango)).thenReturn(List.of(ceviche));

        var reporte = useCase.generar(100L, Periodo.MENSUAL, FECHA);

        assertEquals(Periodo.MENSUAL, reporte.periodo());
        assertEquals(rango, reporte.rango());
        assertEquals(resumen, reporte.resumen());
        assertEquals(List.of(ceviche), reporte.productos());
    }

    @Test
    void exigeRestaurante() {
        var ex = assertThrows(BusinessException.class, () -> useCase.generar(null, Periodo.DIARIO, FECHA));
        assertEquals("Restaurante no identificado", ex.getMessage());
        verifyNoInteractions(query);
    }

    @Test
    void exigePeriodo() {
        assertThrows(BusinessException.class, () -> useCase.generar(1L, null, FECHA));
        verifyNoInteractions(query);
    }

    @Test
    void exigeFecha() {
        assertThrows(BusinessException.class, () -> useCase.generar(1L, Periodo.DIARIO, null));
        verifyNoInteractions(query);
    }
}
