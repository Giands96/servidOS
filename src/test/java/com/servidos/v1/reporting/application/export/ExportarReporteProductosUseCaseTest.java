package com.servidos.v1.reporting.application.export;

import com.servidos.v1.reporting.application.ReporteVentasQuery;
import com.servidos.v1.reporting.domain.Periodo;
import com.servidos.v1.reporting.domain.ResumenVentas;
import com.servidos.v1.reporting.domain.VentaProducto;
import com.servidos.v1.shared.exception.BusinessException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDate;
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
class ExportarReporteProductosUseCaseTest {

    static final LocalDate VIERNES = LocalDate.of(2026, 10, 23);

    @Mock ReporteVentasQuery query;
    @Mock ExportadorReporte pdf;
    @Mock ExportadorReporte xlsx;

    ExportarReporteProductosUseCase useCase;

    @BeforeEach
    void setUp() {
        lenient().when(pdf.formato()).thenReturn(FormatoExport.PDF);
        lenient().when(xlsx.formato()).thenReturn(FormatoExport.XLSX);
        useCase = new ExportarReporteProductosUseCase(query, List.of(pdf, xlsx));
    }

    private void datosDelMes() {
        var rango = Periodo.MENSUAL.rango(VIERNES);
        when(query.nombreRestaurante(100L)).thenReturn("Chifa Central");
        when(query.resumen(100L, rango)).thenReturn(new ResumenVentas(4, new BigDecimal("200.00")));
        when(query.productosVendidos(100L, rango)).thenReturn(List.of(
                new VentaProducto(3L, "Ceviche", 5, new BigDecimal("125.00")),
                new VentaProducto(4L, "Chicha", 9, new BigDecimal("45.00"))));
    }

    @Test
    void armaElDocumentoConEncabezadoColumnasYElRankingNumerado() {
        datosDelMes();
        when(pdf.exportar(any())).thenReturn(new byte[]{1});

        useCase.exportar(100L, Periodo.MENSUAL, VIERNES, FormatoExport.PDF);

        var captor = ArgumentCaptor.forClass(DocumentoReporte.class);
        verify(pdf).exportar(captor.capture());
        var doc = captor.getValue();
        assertEquals("Reporte de productos", doc.titulo());
        assertEquals(List.of("Chifa Central", "Mensual · 01/10/2026 – 31/10/2026", "Ventas: 4",
                "Total vendido: S/ 200.00"), doc.encabezado());
        assertEquals(List.of(
                new Columna("#", TipoColumna.ENTERO),
                new Columna("Producto", TipoColumna.TEXTO),
                new Columna("Cantidad", TipoColumna.ENTERO),
                new Columna("Monto vendido", TipoColumna.MONEDA)), doc.columnas());
        assertEquals(List.of(
                List.<Object>of(1L, "Ceviche", 5L, new BigDecimal("125.00")),
                List.<Object>of(2L, "Chicha", 9L, new BigDecimal("45.00"))), doc.filas());
    }

    @Test
    void devuelveElArchivoConNombreTipoYContenido() {
        datosDelMes();
        when(xlsx.exportar(any())).thenReturn(new byte[]{7, 8});

        var archivo = useCase.exportar(100L, Periodo.MENSUAL, VIERNES, FormatoExport.XLSX);

        assertEquals("reporte-productos-mensual-2026-10-01.xlsx", archivo.nombreArchivo());
        assertEquals(FormatoExport.XLSX.contentType(), archivo.contentType());
        assertArrayEquals(new byte[]{7, 8}, archivo.contenido());
        verify(pdf, never()).exportar(any());
    }

    @Test
    void exigeRestauranteFormatoYPeriodo() {
        assertThrows(BusinessException.class,
                () -> useCase.exportar(null, Periodo.DIARIO, VIERNES, FormatoExport.PDF));
        assertThrows(BusinessException.class, () -> useCase.exportar(100L, null, VIERNES, FormatoExport.PDF));
        assertThrows(BusinessException.class,
                () -> useCase.exportar(100L, Periodo.DIARIO, null, FormatoExport.PDF));
        assertThrows(BusinessException.class, () -> useCase.exportar(100L, Periodo.DIARIO, VIERNES, null));
        verifyNoInteractions(query, pdf, xlsx);
    }

    @Test
    void formatoSinExportadorFalla() {
        var sinXlsx = new ExportarReporteProductosUseCase(query, List.of(pdf));

        assertThrows(BusinessException.class,
                () -> sinXlsx.exportar(100L, Periodo.DIARIO, VIERNES, FormatoExport.XLSX));
    }
}
