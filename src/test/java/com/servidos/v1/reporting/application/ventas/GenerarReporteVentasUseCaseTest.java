package com.servidos.v1.reporting.application.ventas;

import com.servidos.v1.reporting.application.ReporteVentasQuery;
import com.servidos.v1.reporting.domain.OrdenVentas;
import com.servidos.v1.reporting.domain.Periodo;
import com.servidos.v1.reporting.domain.RangoFechas;
import com.servidos.v1.reporting.domain.ResumenVentas;
import com.servidos.v1.reporting.domain.VentaPedido;
import com.servidos.v1.shared.exception.BusinessException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class GenerarReporteVentasUseCaseTest {

    static final LocalDate FECHA = LocalDate.of(2026, 10, 23);

    @Mock ReporteVentasQuery query;
    @InjectMocks GenerarReporteVentasUseCase useCase;

    @Test
    void armaElReporteDelPeriodoConResumenYPagina() {
        var rango = new RangoFechas(LocalDate.of(2026, 10, 19), LocalDate.of(2026, 10, 25));
        var venta = new VentaPedido(7L, LocalDateTime.of(2026, 10, 23, 13, 0), "DELIVERY", "YAPE",
                new BigDecimal("25.00"), null, null);
        var resumen = new ResumenVentas(1, new BigDecimal("25.00"));
        var pageable = PageRequest.of(0, 20);
        when(query.resumen(100L, rango)).thenReturn(resumen);
        when(query.ventas(100L, rango, OrdenVentas.MAYOR_VENTA, pageable))
                .thenReturn(new PageImpl<>(List.of(venta), pageable, 1));

        var reporte = useCase.generar(100L, Periodo.SEMANAL, FECHA, OrdenVentas.MAYOR_VENTA, 0, 20);

        assertEquals(Periodo.SEMANAL, reporte.periodo());
        assertEquals(rango, reporte.rango());
        assertEquals(resumen, reporte.resumen());
        assertEquals(List.of(venta), reporte.ventas().getContent());
    }

    @Test
    void sinOrdenUsaLasMasRecientes() {
        var rango = Periodo.DIARIO.rango(FECHA);
        var pageable = PageRequest.of(2, 10);
        when(query.resumen(100L, rango)).thenReturn(new ResumenVentas(0, BigDecimal.ZERO));
        when(query.ventas(100L, rango, OrdenVentas.MAS_RECIENTES, pageable))
                .thenReturn(new PageImpl<>(List.of(), pageable, 0));

        useCase.generar(100L, Periodo.DIARIO, FECHA, null, 2, 10);

        verify(query).ventas(100L, rango, OrdenVentas.MAS_RECIENTES, pageable);
    }

    @Test
    void exigeRestaurante() {
        var ex = assertThrows(BusinessException.class,
                () -> useCase.generar(null, Periodo.DIARIO, FECHA, null, 0, 20));
        assertEquals("Restaurante no identificado", ex.getMessage());
        verifyNoInteractions(query);
    }

    @Test
    void exigePeriodo() {
        assertThrows(BusinessException.class, () -> useCase.generar(1L, null, FECHA, null, 0, 20));
        verifyNoInteractions(query);
    }

    @Test
    void exigeFecha() {
        assertThrows(BusinessException.class, () -> useCase.generar(1L, Periodo.DIARIO, null, null, 0, 20));
        verifyNoInteractions(query);
    }

    @Test
    void paginaNegativaFalla() {
        assertThrows(BusinessException.class, () -> useCase.generar(1L, Periodo.DIARIO, FECHA, null, -1, 20));
        verifyNoInteractions(query);
    }

    @ParameterizedTest
    @ValueSource(ints = {0, 101})
    void tamanoFueraDeRangoFalla(int tamano) {
        assertThrows(BusinessException.class,
                () -> useCase.generar(1L, Periodo.DIARIO, FECHA, null, 0, tamano));
        verifyNoInteractions(query);
    }
}
