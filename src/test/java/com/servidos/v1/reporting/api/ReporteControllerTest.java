package com.servidos.v1.reporting.api;

import com.servidos.v1.reporting.application.export.ArchivoReporte;
import com.servidos.v1.reporting.application.export.ExportarReporteProductosUseCase;
import com.servidos.v1.reporting.application.export.ExportarReporteVentasUseCase;
import com.servidos.v1.reporting.application.export.FormatoExport;
import com.servidos.v1.reporting.application.ventas.GenerarReporteProductosUseCase;
import com.servidos.v1.reporting.application.ventas.GenerarReporteVentasUseCase;
import com.servidos.v1.reporting.application.ventas.ReporteProductos;
import com.servidos.v1.reporting.application.ventas.ReporteVentas;
import com.servidos.v1.reporting.domain.OrdenVentas;
import com.servidos.v1.reporting.domain.Periodo;
import com.servidos.v1.reporting.domain.RangoFechas;
import com.servidos.v1.reporting.domain.ResumenVentas;
import com.servidos.v1.reporting.domain.VentaPedido;
import com.servidos.v1.reporting.domain.VentaProducto;
import com.servidos.v1.shared.security.TenantContext;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;

import java.lang.reflect.Method;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ReporteControllerTest {

    static final LocalDate VIERNES = LocalDate.of(2026, 10, 23);
    static final RangoFechas SEMANA = new RangoFechas(LocalDate.of(2026, 10, 19), LocalDate.of(2026, 10, 25));
    static final ResumenVentas RESUMEN = new ResumenVentas(2, new BigDecimal("75.00"));

    @Mock GenerarReporteVentasUseCase ventasUseCase;
    @Mock GenerarReporteProductosUseCase productosUseCase;
    @Mock ExportarReporteVentasUseCase exportarVentasUseCase;
    @Mock ExportarReporteProductosUseCase exportarProductosUseCase;

    /** 23:30 del viernes 23 en Lima; en UTC ya es sábado 24: "hoy" tiene que salir de la zona de Lima. */
    static final Clock RELOJ = Clock.fixed(Instant.parse("2026-10-24T04:30:00Z"), ZoneOffset.UTC);

    ReporteController controller;

    @BeforeEach
    void setUp() {
        controller = new ReporteController(ventasUseCase, productosUseCase, exportarVentasUseCase,
                exportarProductosUseCase, RELOJ);
        TenantContext.setRestauranteId(100L);
    }

    @AfterEach
    void limpiar() {
        TenantContext.clear();
    }

    @Test
    void ventasUsaElTenantDelJwtYMapeaLaRespuesta() {
        var venta = new VentaPedido(7L, LocalDateTime.of(2026, 10, 22, 13, 30), "DELIVERY", "YAPE",
                new BigDecimal("50.00"), null, null);
        var pageable = PageRequest.of(1, 5);
        when(ventasUseCase.generar(100L, Periodo.SEMANAL, VIERNES, OrdenVentas.MAYOR_VENTA, 1, 5))
                .thenReturn(new ReporteVentas(Periodo.SEMANAL, SEMANA, RESUMEN,
                        new PageImpl<>(List.of(venta), pageable, 6)));

        var resp = controller.ventas(Periodo.SEMANAL, VIERNES, OrdenVentas.MAYOR_VENTA, 1, 5);

        assertEquals(HttpStatus.OK, resp.getStatusCode());
        var body = resp.getBody();
        assertEquals(Periodo.SEMANAL, body.periodo());
        assertEquals(LocalDate.of(2026, 10, 19), body.desde());
        assertEquals(LocalDate.of(2026, 10, 25), body.hasta());
        assertEquals(2, body.cantidadVentas());
        assertEquals(new BigDecimal("75.00"), body.totalVendido());
        assertEquals(6, body.ventas().getTotalElements());
        var fila = body.ventas().getContent().get(0);
        assertEquals(7L, fila.pedidoId());
        assertEquals(LocalDateTime.of(2026, 10, 22, 13, 30), fila.fechaPago());
        assertEquals("DELIVERY", fila.tipoPedido());
        assertEquals("YAPE", fila.metodoPago());
        assertEquals(new BigDecimal("50.00"), fila.total());
    }

    @Test
    void ventasSinFechaUsaHoyEnLima() {
        var hoy = VIERNES;
        when(ventasUseCase.generar(100L, Periodo.DIARIO, hoy, OrdenVentas.MAS_RECIENTES, 0, 20))
                .thenReturn(new ReporteVentas(Periodo.DIARIO, new RangoFechas(hoy, hoy), RESUMEN,
                        new PageImpl<>(List.of())));

        var resp = controller.ventas(Periodo.DIARIO, null, OrdenVentas.MAS_RECIENTES, 0, 20);

        assertEquals(hoy, resp.getBody().desde());
        verify(ventasUseCase).generar(100L, Periodo.DIARIO, hoy, OrdenVentas.MAS_RECIENTES, 0, 20);
    }

    @Test
    void productosNumeraElRankingDesdeUno() {
        var rango = new RangoFechas(LocalDate.of(2026, 10, 1), LocalDate.of(2026, 10, 31));
        when(productosUseCase.generar(100L, Periodo.MENSUAL, VIERNES)).thenReturn(new ReporteProductos(
                Periodo.MENSUAL, rango, RESUMEN, List.of(
                        new VentaProducto(3L, "Ceviche", 5, new BigDecimal("125.00")),
                        new VentaProducto(4L, "Chicha", 9, new BigDecimal("45.00")))));

        var resp = controller.productos(Periodo.MENSUAL, VIERNES);

        assertEquals(HttpStatus.OK, resp.getStatusCode());
        var body = resp.getBody();
        assertEquals(Periodo.MENSUAL, body.periodo());
        assertEquals(LocalDate.of(2026, 10, 1), body.desde());
        assertEquals(LocalDate.of(2026, 10, 31), body.hasta());
        assertEquals(2, body.cantidadVentas());
        assertEquals(new BigDecimal("75.00"), body.totalVendido());
        assertEquals(2, body.productos().size());
        var primero = body.productos().get(0);
        assertEquals(1, primero.posicion());
        assertEquals(3L, primero.productoId());
        assertEquals("Ceviche", primero.nombre());
        assertEquals(5L, primero.cantidad());
        assertEquals(new BigDecimal("125.00"), primero.monto());
        assertEquals(2, body.productos().get(1).posicion());
    }

    @Test
    void productosSinFechaUsaHoyEnLima() {
        var hoy = VIERNES;
        when(productosUseCase.generar(100L, Periodo.DIARIO, hoy)).thenReturn(
                new ReporteProductos(Periodo.DIARIO, new RangoFechas(hoy, hoy), RESUMEN, List.of()));

        controller.productos(Periodo.DIARIO, null);

        verify(productosUseCase).generar(100L, Periodo.DIARIO, hoy);
    }

    @Test
    void exportarVentasDevuelveElArchivoComoAdjunto() {
        when(exportarVentasUseCase.exportar(100L, Periodo.SEMANAL, VIERNES, OrdenVentas.MAYOR_VENTA,
                FormatoExport.PDF)).thenReturn(new ArchivoReporte("reporte-ventas-semanal-2026-10-19.pdf",
                "application/pdf", new byte[]{1, 2, 3}));

        var resp = controller.exportarVentas(FormatoExport.PDF, Periodo.SEMANAL, VIERNES, OrdenVentas.MAYOR_VENTA);

        assertEquals(HttpStatus.OK, resp.getStatusCode());
        assertEquals("application/pdf", resp.getHeaders().getContentType().toString());
        assertEquals("attachment; filename=\"reporte-ventas-semanal-2026-10-19.pdf\"",
                resp.getHeaders().getFirst(HttpHeaders.CONTENT_DISPOSITION));
        assertArrayEquals(new byte[]{1, 2, 3}, resp.getBody());
    }

    @Test
    void exportarVentasSinFechaUsaHoyEnLima() {
        var hoy = VIERNES;
        when(exportarVentasUseCase.exportar(100L, Periodo.DIARIO, hoy, OrdenVentas.MAS_RECIENTES,
                FormatoExport.XLSX)).thenReturn(new ArchivoReporte("a.xlsx", FormatoExport.XLSX.contentType(),
                new byte[]{1}));

        controller.exportarVentas(FormatoExport.XLSX, Periodo.DIARIO, null, OrdenVentas.MAS_RECIENTES);

        verify(exportarVentasUseCase).exportar(100L, Periodo.DIARIO, hoy, OrdenVentas.MAS_RECIENTES,
                FormatoExport.XLSX);
    }

    @Test
    void exportarProductosDevuelveElArchivoComoAdjunto() {
        when(exportarProductosUseCase.exportar(100L, Periodo.MENSUAL, VIERNES, FormatoExport.XLSX))
                .thenReturn(new ArchivoReporte("reporte-productos-mensual-2026-10-01.xlsx",
                        FormatoExport.XLSX.contentType(), new byte[]{9}));

        var resp = controller.exportarProductos(FormatoExport.XLSX, Periodo.MENSUAL, VIERNES);

        assertEquals(HttpStatus.OK, resp.getStatusCode());
        assertEquals(FormatoExport.XLSX.contentType(), resp.getHeaders().getContentType().toString());
        assertEquals("attachment; filename=\"reporte-productos-mensual-2026-10-01.xlsx\"",
                resp.getHeaders().getFirst(HttpHeaders.CONTENT_DISPOSITION));
        assertArrayEquals(new byte[]{9}, resp.getBody());
    }

    @Test
    void exportarProductosSinFechaUsaHoyEnLima() {
        var hoy = VIERNES;
        when(exportarProductosUseCase.exportar(100L, Periodo.DIARIO, hoy, FormatoExport.PDF))
                .thenReturn(new ArchivoReporte("a.pdf", "application/pdf", new byte[]{1}));

        controller.exportarProductos(FormatoExport.PDF, Periodo.DIARIO, null);

        verify(exportarProductosUseCase).exportar(100L, Periodo.DIARIO, hoy, FormatoExport.PDF);
    }

    /** Todo endpoint del controlador es solo del administrador. */
    @Test
    void todosLosEndpointsSonSoloDelAdministrador() {
        var endpoints = Arrays.stream(ReporteController.class.getDeclaredMethods())
                .filter(m -> java.lang.reflect.Modifier.isPublic(m.getModifiers()))
                .toList();

        assertTrue(endpoints.size() >= 4, "el controlador debe exponer endpoints");
        for (Method m : endpoints) {
            var pre = m.getAnnotation(PreAuthorize.class);
            assertNotNull(pre, m.getName() + " sin @PreAuthorize");
            assertEquals("hasRole('ADMINISTRADOR')", pre.value(), m.getName());
        }
    }
}
