package com.servidos.v1.reporting.application.export;

import com.servidos.v1.reporting.application.ReporteVentasQuery;
import com.servidos.v1.reporting.domain.OrdenVentas;
import com.servidos.v1.reporting.domain.Periodo;
import com.servidos.v1.reporting.domain.ResumenVentas;
import com.servidos.v1.reporting.domain.VentaPedido;
import com.servidos.v1.shared.exception.BusinessException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ExportarReporteVentasUseCaseTest {

    static final LocalDate VIERNES = LocalDate.of(2026, 10, 23);

    @Mock ReporteVentasQuery query;
    @Mock ExportadorReporte pdf;
    @Mock ExportadorReporte xlsx;

    ExportarReporteVentasUseCase useCase;

    @BeforeEach
    void setUp() {
        lenient().when(pdf.formato()).thenReturn(FormatoExport.PDF);
        lenient().when(xlsx.formato()).thenReturn(FormatoExport.XLSX);
        useCase = new ExportarReporteVentasUseCase(query, List.of(pdf, xlsx));
    }

    private void datosDeLaSemana(OrdenVentas orden) {
        var rango = Periodo.SEMANAL.rango(VIERNES);
        when(query.nombreRestaurante(100L)).thenReturn("Chifa Central");
        when(query.resumen(100L, rango)).thenReturn(new ResumenVentas(2, new BigDecimal("1284.50")));
        when(query.todasLasVentas(100L, rango, orden)).thenReturn(List.of(
                new VentaPedido(15L, LocalDateTime.of(2026, 10, 23, 13, 5), "DELIVERY", "YAPE",
                        new BigDecimal("1234.50")),
                new VentaPedido(14L, LocalDateTime.of(2026, 10, 22, 20, 45), "RECOJO", "EFECTIVO",
                        new BigDecimal("50.00"))));
    }

    @Test
    void armaElDocumentoConEncabezadoColumnasYFilasEnElOrdenPedido() {
        datosDeLaSemana(OrdenVentas.MAYOR_VENTA);
        when(pdf.exportar(any())).thenReturn(new byte[]{1});

        useCase.exportar(100L, Periodo.SEMANAL, VIERNES, OrdenVentas.MAYOR_VENTA, FormatoExport.PDF);

        var captor = ArgumentCaptor.forClass(DocumentoReporte.class);
        verify(pdf).exportar(captor.capture());
        var doc = captor.getValue();
        assertEquals("Reporte de ventas", doc.titulo());
        assertEquals(List.of("Chifa Central", "Semanal · 19/10/2026 – 25/10/2026", "Ventas: 2",
                "Total vendido: S/ 1,284.50"), doc.encabezado());
        assertEquals(List.of(
                new Columna("N° pedido", TipoColumna.ENTERO),
                new Columna("Fecha de pago", TipoColumna.FECHA_HORA),
                new Columna("Tipo", TipoColumna.TEXTO),
                new Columna("Método de pago", TipoColumna.TEXTO),
                new Columna("Total", TipoColumna.MONEDA)), doc.columnas());
        assertEquals(List.of(
                List.<Object>of(15L, LocalDateTime.of(2026, 10, 23, 13, 5), "DELIVERY", "YAPE",
                        new BigDecimal("1234.50")),
                List.<Object>of(14L, LocalDateTime.of(2026, 10, 22, 20, 45), "RECOJO", "EFECTIVO",
                        new BigDecimal("50.00"))), doc.filas());
    }

    @Test
    void devuelveElArchivoConNombreTipoYContenido() {
        datosDeLaSemana(OrdenVentas.MAS_RECIENTES);
        when(pdf.exportar(any())).thenReturn(new byte[]{1, 2, 3});

        var archivo = useCase.exportar(100L, Periodo.SEMANAL, VIERNES, null, FormatoExport.PDF);

        assertEquals("reporte-ventas-semanal-2026-10-19.pdf", archivo.nombreArchivo());
        assertEquals("application/pdf", archivo.contentType());
        assertArrayEquals(new byte[]{1, 2, 3}, archivo.contenido());
    }

    @Test
    void sinOrdenUsaLasMasRecientes() {
        datosDeLaSemana(OrdenVentas.MAS_RECIENTES);
        when(pdf.exportar(any())).thenReturn(new byte[]{1});

        useCase.exportar(100L, Periodo.SEMANAL, VIERNES, null, FormatoExport.PDF);

        verify(query).todasLasVentas(100L, Periodo.SEMANAL.rango(VIERNES), OrdenVentas.MAS_RECIENTES);
    }

    @Test
    void eligeElExportadorSegunElFormato() {
        datosDeLaSemana(OrdenVentas.MAS_RECIENTES);
        when(xlsx.exportar(any())).thenReturn(new byte[]{9});

        var archivo = useCase.exportar(100L, Periodo.SEMANAL, VIERNES, null, FormatoExport.XLSX);

        verify(xlsx).exportar(any());
        verify(pdf, never()).exportar(any());
        assertEquals("reporte-ventas-semanal-2026-10-19.xlsx", archivo.nombreArchivo());
        assertEquals(FormatoExport.XLSX.contentType(), archivo.contentType());
    }

    @Test
    void nombreDeArchivoSegunElPeriodo() {
        when(query.nombreRestaurante(100L)).thenReturn("Chifa Central");
        when(query.resumen(any(), any())).thenReturn(new ResumenVentas(0, BigDecimal.ZERO));
        when(query.todasLasVentas(any(), any(), any())).thenReturn(List.of());
        when(pdf.exportar(any())).thenReturn(new byte[]{1});

        assertEquals("reporte-ventas-diario-2026-10-23.pdf",
                useCase.exportar(100L, Periodo.DIARIO, VIERNES, null, FormatoExport.PDF).nombreArchivo());
        assertEquals("reporte-ventas-mensual-2026-10-01.pdf",
                useCase.exportar(100L, Periodo.MENSUAL, VIERNES, null, FormatoExport.PDF).nombreArchivo());
    }

    @Test
    void periodoDiarioYMensualEnElEncabezado() {
        when(query.nombreRestaurante(100L)).thenReturn("Chifa Central");
        when(query.resumen(any(), any())).thenReturn(new ResumenVentas(0, BigDecimal.ZERO));
        when(query.todasLasVentas(any(), any(), any())).thenReturn(List.of());
        when(pdf.exportar(any())).thenReturn(new byte[]{1});

        useCase.exportar(100L, Periodo.DIARIO, VIERNES, null, FormatoExport.PDF);
        useCase.exportar(100L, Periodo.MENSUAL, VIERNES, null, FormatoExport.PDF);

        var captor = ArgumentCaptor.forClass(DocumentoReporte.class);
        verify(pdf, org.mockito.Mockito.times(2)).exportar(captor.capture());
        assertEquals("Diario · 23/10/2026", captor.getAllValues().get(0).encabezado().get(1));
        assertEquals("Mensual · 01/10/2026 – 31/10/2026", captor.getAllValues().get(1).encabezado().get(1));
        assertEquals("Total vendido: S/ 0.00", captor.getAllValues().get(0).encabezado().get(3));
    }

    @Test
    void exigeRestauranteFormatoYPeriodo() {
        assertThrows(BusinessException.class,
                () -> useCase.exportar(null, Periodo.DIARIO, VIERNES, null, FormatoExport.PDF));
        assertThrows(BusinessException.class,
                () -> useCase.exportar(100L, null, VIERNES, null, FormatoExport.PDF));
        assertThrows(BusinessException.class,
                () -> useCase.exportar(100L, Periodo.DIARIO, null, null, FormatoExport.PDF));
        assertThrows(BusinessException.class,
                () -> useCase.exportar(100L, Periodo.DIARIO, VIERNES, null, null));
        verifyNoInteractions(query, pdf, xlsx);
    }

    @Test
    void formatoSinExportadorFalla() {
        var sinXlsx = new ExportarReporteVentasUseCase(query, List.of(pdf));

        assertThrows(BusinessException.class,
                () -> sinXlsx.exportar(100L, Periodo.DIARIO, VIERNES, null, FormatoExport.XLSX));
    }
}
