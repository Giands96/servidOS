package com.servidos.v1.reporting;

import com.servidos.v1.reporting.application.ReporteVentasQuery;
import com.servidos.v1.reporting.application.export.ExportarReporteProductosUseCase;
import com.servidos.v1.reporting.application.export.ExportarReporteVentasUseCase;
import com.servidos.v1.reporting.application.export.FormatoExport;
import com.servidos.v1.reporting.application.ventas.GenerarReporteProductosUseCase;
import com.servidos.v1.reporting.application.ventas.GenerarReporteVentasUseCase;
import com.servidos.v1.reporting.domain.OrdenVentas;
import com.servidos.v1.reporting.domain.Periodo;
import com.servidos.v1.reporting.domain.VentaPedido;
import com.servidos.v1.reporting.domain.VentaProducto;
import com.servidos.v1.shared.exception.BusinessException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.openpdf.text.pdf.PdfReader;
import org.openpdf.text.pdf.parser.PdfTextExtractor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.io.IOException;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Las consultas de los reportes contra Postgres: qué cuenta como venta, el borde de los períodos,
 * los tres órdenes con desempate, el ranking de productos y el aislamiento por restaurante.
 * Rollback por test.
 */
@SpringBootTest
@ActiveProfiles("test")
@Transactional
class ReportesIntegracionTest {

    /** Viernes; su semana va del lunes 19 al domingo 25. */
    static final LocalDate VIERNES = LocalDate.of(2026, 10, 23);

    @Autowired GenerarReporteVentasUseCase ventasUseCase;
    @Autowired GenerarReporteProductosUseCase productosUseCase;
    @Autowired ExportarReporteVentasUseCase exportarVentas;
    @Autowired ExportarReporteProductosUseCase exportarProductos;
    @Autowired ReporteVentasQuery query;
    @Autowired JdbcTemplate jdbc;

    Long restauranteId;
    Long otroRestauranteId;
    Long usuarioId;

    @BeforeEach
    void setUp() {
        restauranteId = restaurante("Chifa Central");
        otroRestauranteId = restaurante("Otro Local");
        usuarioId = jdbc.queryForObject("""
                INSERT INTO usuario (nombre, apellido, email, password_hash, estado)
                VALUES ('Caja', 'IT', ?, 'x', 'ACTIVO') RETURNING usuario_id""",
                Long.class, "it-reportes-" + System.nanoTime() + "@test.local");
    }

    // ---------- fixtures ----------

    private Long restaurante(String nombre) {
        return jdbc.queryForObject("""
                INSERT INTO restaurante (slug, nombre, estado) VALUES (?, ?, 'ACTIVO')
                RETURNING restaurante_id""", Long.class, "it-rep-" + System.nanoTime(), nombre);
    }

    private Long producto(Long restaurante, String nombre) {
        return jdbc.queryForObject("""
                INSERT INTO producto (restaurante_id, nombre, precio, estado)
                VALUES (?, ?, 10.00, 'ACTIVO') RETURNING producto_id""", Long.class, restaurante, nombre);
    }

    private Long pedido(Long restaurante, String estado, String total) {
        return jdbc.queryForObject("""
                INSERT INTO pedido (restaurante_id, usuario_id, tipo_pedido, estado, total)
                VALUES (?, ?, 'DELIVERY', ?, ?) RETURNING pedido_id""",
                Long.class, restaurante, usuarioId, estado, new BigDecimal(total));
    }

    private void pago(Long pedidoId, Long restaurante, String estado, LocalDateTime fechaPago, String monto) {
        jdbc.update("""
                INSERT INTO pago (pedido_id, restaurante_id, usuario_id, metodo_pago, monto, estado, fecha_pago)
                VALUES (?, ?, ?, 'YAPE', ?, ?, ?)""",
                pedidoId, restaurante, usuarioId, new BigDecimal(monto), estado, fechaPago);
    }

    private void detalle(Long pedidoId, Long restaurante, Long productoId, int cantidad, String precio,
                         String nombreSnapshot) {
        var unitario = new BigDecimal(precio);
        jdbc.update("""
                INSERT INTO detalle_pedido (pedido_id, restaurante_id, producto_id, cantidad, precio_unitario,
                                            subtotal, nombre_producto)
                VALUES (?, ?, ?, ?, ?, ?, ?)""",
                pedidoId, restaurante, productoId, cantidad, unitario,
                unitario.multiply(BigDecimal.valueOf(cantidad)), nombreSnapshot);
    }

    /** Pedido entregado y cobrado del restaurante de prueba. */
    private Long venta(String total, LocalDateTime fechaPago) {
        return venta(restauranteId, total, fechaPago);
    }

    private Long venta(Long restaurante, String total, LocalDateTime fechaPago) {
        var id = pedido(restaurante, "ENTREGADO", total);
        pago(id, restaurante, "PAGADO", fechaPago, total);
        return id;
    }

    private static LocalDateTime dia(int dia, int hora, int minuto) {
        return LocalDateTime.of(2026, 10, dia, hora, minuto);
    }

    private List<Long> ids(List<VentaPedido> ventas) {
        return ventas.stream().map(VentaPedido::pedidoId).toList();
    }

    private List<Long> idsDeLaSemana(OrdenVentas orden) {
        return ids(ventasUseCase.generar(restauranteId, Periodo.SEMANAL, VIERNES, orden, 0, 100)
                .ventas().getContent());
    }

    private static void assertMonto(String esperado, BigDecimal real) {
        assertEquals(0, new BigDecimal(esperado).compareTo(real), "esperado " + esperado + " pero fue " + real);
    }

    // ---------- qué es una venta ----------

    @Test
    void soloCuentaLoPagadoYNoCanceladoDelRestaurante() {
        var vendido = venta("20.00", dia(21, 12, 0));

        var reembolsado = pedido(restauranteId, "ENTREGADO", "15.00");
        pago(reembolsado, restauranteId, "REEMBOLSADO", dia(21, 12, 5), "15.00");
        pedido(restauranteId, "PENDIENTE", "30.00");
        var canceladoPagado = pedido(restauranteId, "CANCELADO", "40.00");
        pago(canceladoPagado, restauranteId, "PAGADO", dia(21, 12, 10), "40.00");
        venta(otroRestauranteId, "99.00", dia(21, 12, 0));

        var reporte = ventasUseCase.generar(restauranteId, Periodo.SEMANAL, VIERNES, null, 0, 20);

        assertEquals(List.of(vendido), ids(reporte.ventas().getContent()));
        assertEquals(1, reporte.resumen().cantidadVentas());
        assertMonto("20.00", reporte.resumen().totalVendido());
        var fila = reporte.ventas().getContent().get(0);
        assertEquals("DELIVERY", fila.tipoPedido());
        assertEquals("YAPE", fila.metodoPago());
        assertEquals(dia(21, 12, 0), fila.fechaPago());
        assertMonto("20.00", fila.total());
    }

    @Test
    void sinVentasElResumenEsCero() {
        var reporte = ventasUseCase.generar(restauranteId, Periodo.DIARIO, VIERNES, null, 0, 20);

        assertEquals(0, reporte.resumen().cantidadVentas());
        assertMonto("0", reporte.resumen().totalVendido());
        assertEquals(0, reporte.ventas().getTotalElements());
    }

    @Test
    void elResumenSumaElTotalDeLosPedidosDelPeriodo() {
        venta("10.50", dia(19, 9, 0));
        venta("20.00", dia(25, 23, 0));
        venta("5.25", dia(26, 0, 0)); // semana siguiente

        var reporte = ventasUseCase.generar(restauranteId, Periodo.SEMANAL, VIERNES, null, 0, 20);

        assertEquals(2, reporte.resumen().cantidadVentas());
        assertMonto("30.50", reporte.resumen().totalVendido());
    }

    // ---------- bordes del período ----------

    @Test
    void elDiaIncluyeSuPrimerYUltimoSegundoYExcluyeLosVecinos() {
        venta("1.00", LocalDateTime.of(2026, 10, 22, 23, 59, 59));
        var inicio = venta("2.00", LocalDateTime.of(2026, 10, 23, 0, 0, 0));
        var fin = venta("3.00", LocalDateTime.of(2026, 10, 23, 23, 59, 59));
        var manana = venta("4.00", LocalDateTime.of(2026, 10, 24, 0, 0, 0));

        var hoy = ventasUseCase.generar(restauranteId, Periodo.DIARIO, VIERNES, null, 0, 20);
        var sabado = ventasUseCase.generar(restauranteId, Periodo.DIARIO, VIERNES.plusDays(1), null, 0, 20);

        assertEquals(List.of(fin, inicio), ids(hoy.ventas().getContent()));
        assertEquals(List.of(manana), ids(sabado.ventas().getContent()));
        assertMonto("5.00", hoy.resumen().totalVendido());
    }

    @Test
    void laSemanaVaDeLunesAMedianocheDelDomingo() {
        venta("1.00", LocalDateTime.of(2026, 10, 18, 23, 59, 59)); // domingo anterior
        var lunes = venta("2.00", LocalDateTime.of(2026, 10, 19, 0, 0, 0));
        var domingo = venta("3.00", LocalDateTime.of(2026, 10, 25, 23, 59, 59));
        venta("4.00", LocalDateTime.of(2026, 10, 26, 0, 0, 0)); // lunes siguiente

        assertEquals(List.of(domingo, lunes), idsDeLaSemana(OrdenVentas.MAS_RECIENTES));
    }

    @Test
    void elMesIncluyeSuUltimoSegundo() {
        venta("1.00", LocalDateTime.of(2026, 9, 30, 23, 59, 59));
        var primero = venta("2.00", LocalDateTime.of(2026, 10, 1, 0, 0, 0));
        var ultimo = venta("3.00", LocalDateTime.of(2026, 10, 31, 23, 59, 59));
        venta("4.00", LocalDateTime.of(2026, 11, 1, 0, 0, 0));

        var reporte = ventasUseCase.generar(restauranteId, Periodo.MENSUAL, VIERNES, null, 0, 20);

        assertEquals(List.of(ultimo, primero), ids(reporte.ventas().getContent()));
    }

    // ---------- órdenes ----------

    /** A y E empatan en total y fecha; B y C también; el pedido más nuevo gana en cada desempate. */
    @Test
    void ordenaSegunElCriterioConDesempateDeterminista() {
        var a = venta("30.00", dia(21, 10, 0));
        var b = venta("50.00", dia(21, 11, 0));
        var c = venta("50.00", dia(21, 11, 0));
        var d = venta("30.00", dia(21, 12, 0));
        var e = venta("30.00", dia(21, 10, 0));

        assertEquals(List.of(c, b, d, e, a), idsDeLaSemana(OrdenVentas.MAYOR_VENTA));
        assertEquals(List.of(d, c, b, e, a), idsDeLaSemana(OrdenVentas.MAS_RECIENTES));
        assertEquals(List.of(a, e, b, c, d), idsDeLaSemana(OrdenVentas.MAS_ANTIGUOS));
    }

    @Test
    void sinOrdenUsaLasMasRecientes() {
        var viejo = venta("10.00", dia(20, 9, 0));
        var nuevo = venta("10.00", dia(22, 9, 0));

        var reporte = ventasUseCase.generar(restauranteId, Periodo.SEMANAL, VIERNES, null, 0, 20);

        assertEquals(List.of(nuevo, viejo), ids(reporte.ventas().getContent()));
    }

    @Test
    void paginaRespetandoElOrdenYTotalizandoTodo() {
        venta("30.00", dia(21, 10, 0));
        var b = venta("50.00", dia(21, 11, 0));
        venta("50.00", dia(21, 11, 0));
        venta("30.00", dia(21, 12, 0));
        var e = venta("30.00", dia(21, 10, 0));

        var pagina = ventasUseCase.generar(restauranteId, Periodo.SEMANAL, VIERNES,
                OrdenVentas.MAS_RECIENTES, 1, 2).ventas();

        assertEquals(List.of(b, e), ids(pagina.getContent()));
        assertEquals(5, pagina.getTotalElements());
        assertEquals(3, pagina.getTotalPages());
    }

    @Test
    void todasLasVentasDevuelveElPeriodoCompletoSinPaginar() {
        for (int i = 0; i < 25; i++) venta("1.00", dia(21, 8, i));

        var todas = query.todasLasVentas(restauranteId, Periodo.SEMANAL.rango(VIERNES), OrdenVentas.MAS_ANTIGUOS);

        assertEquals(25, todas.size());
        assertEquals(dia(21, 8, 0), todas.get(0).fechaPago());
        assertEquals(dia(21, 8, 24), todas.get(24).fechaPago());
    }

    // ---------- ranking de productos ----------

    @Test
    void rankingAgregaPorProductoYOrdenaPorMonto() {
        var ceviche = producto(restauranteId, "Ceviche");
        var chicha = producto(restauranteId, "Chicha");
        var lomo = producto(restauranteId, "Lomo Saltado");
        var gaseosa = producto(restauranteId, "Inca Kola");

        var s1 = venta("70.00", dia(20, 12, 0));
        detalle(s1, restauranteId, ceviche, 2, "25.00", "Ceviche Clásico");
        detalle(s1, restauranteId, chicha, 3, "5.00", "Chicha");
        detalle(s1, restauranteId, lomo, 1, "40.00", "Lomo Viejo");
        var s2 = venta("100.00", dia(22, 12, 0));
        detalle(s2, restauranteId, ceviche, 1, "25.00", "Ceviche Nuevo");
        detalle(s2, restauranteId, lomo, 1, "0.00", null);
        detalle(s2, restauranteId, gaseosa, 2, "5.00", null);
        var s3 = venta("50.00", dia(23, 12, 0));
        detalle(s3, restauranteId, chicha, 10, "5.00", "Chicha");

        var reporte = productosUseCase.generar(restauranteId, Periodo.SEMANAL, VIERNES);

        // El nombre es el último snapshot no nulo (Lomo Viejo aunque la venta más reciente no lo tenga);
        // sin snapshot cae al nombre del producto (Inca Kola).
        assertEquals(List.of("Ceviche Nuevo", "Chicha", "Lomo Viejo", "Inca Kola"),
                reporte.productos().stream().map(VentaProducto::nombre).toList());
        assertEquals(List.of(ceviche, chicha, lomo, gaseosa),
                reporte.productos().stream().map(VentaProducto::productoId).toList());
        assertEquals(List.of(3L, 13L, 2L, 2L), reporte.productos().stream().map(VentaProducto::cantidad).toList());
        assertMonto("75.00", reporte.productos().get(0).monto());
        assertMonto("65.00", reporte.productos().get(1).monto());
        assertMonto("40.00", reporte.productos().get(2).monto());
        assertMonto("10.00", reporte.productos().get(3).monto());
        assertEquals(3, reporte.resumen().cantidadVentas());
    }

    @Test
    void sinSnapshotUsaElNombreDelProducto() {
        var gaseosa = producto(restauranteId, "Inca Kola");
        detalle(venta("10.00", dia(21, 12, 0)), restauranteId, gaseosa, 2, "5.00", null);

        var ranking = productosUseCase.generar(restauranteId, Periodo.DIARIO, LocalDate.of(2026, 10, 21))
                .productos();

        assertEquals("Inca Kola", ranking.get(0).nombre());
    }

    @Test
    void elRankingDesempataPorCantidadYLuegoPorNombre() {
        var zeta = producto(restauranteId, "Zeta");
        var alfa = producto(restauranteId, "Alfa");
        var medio = producto(restauranteId, "Medio");
        var s = venta("60.00", dia(21, 12, 0));
        detalle(s, restauranteId, zeta, 2, "10.00", "Zeta");
        detalle(s, restauranteId, alfa, 2, "10.00", "Alfa");
        detalle(s, restauranteId, medio, 4, "5.00", "Medio");

        var ranking = productosUseCase.generar(restauranteId, Periodo.SEMANAL, VIERNES).productos();

        assertEquals(List.of("Medio", "Alfa", "Zeta"), ranking.stream().map(VentaProducto::nombre).toList());
    }

    @Test
    void elRankingIgnoraVentasExcluidasFueraDePeriodoYOtrosRestaurantes() {
        var arroz = producto(restauranteId, "Arroz");
        var ajeno = producto(otroRestauranteId, "Arroz");
        var ok = venta("10.00", dia(21, 12, 0));
        detalle(ok, restauranteId, arroz, 1, "10.00", "Arroz");

        var reembolsado = pedido(restauranteId, "ENTREGADO", "1000.00");
        pago(reembolsado, restauranteId, "REEMBOLSADO", dia(21, 12, 0), "1000.00");
        detalle(reembolsado, restauranteId, arroz, 100, "10.00", "Arroz");
        var cancelado = pedido(restauranteId, "CANCELADO", "500.00");
        pago(cancelado, restauranteId, "PAGADO", dia(21, 12, 0), "500.00");
        detalle(cancelado, restauranteId, arroz, 50, "10.00", "Arroz");
        var sinPago = pedido(restauranteId, "PENDIENTE", "300.00");
        detalle(sinPago, restauranteId, arroz, 30, "10.00", "Arroz");
        var proxima = venta("200.00", dia(26, 0, 0));
        detalle(proxima, restauranteId, arroz, 20, "10.00", "Arroz");
        var deOtro = venta(otroRestauranteId, "400.00", dia(21, 12, 0));
        detalle(deOtro, otroRestauranteId, ajeno, 40, "10.00", "Arroz");

        var ranking = productosUseCase.generar(restauranteId, Periodo.SEMANAL, VIERNES).productos();

        assertEquals(1, ranking.size());
        assertEquals(1L, ranking.get(0).cantidad());
        assertMonto("10.00", ranking.get(0).monto());
    }

    // ---------- exportación ----------

    private static String textoDelPdf(byte[] pdf) throws IOException {
        var reader = new PdfReader(pdf);
        try {
            return new PdfTextExtractor(reader).getTextFromPage(1);
        } finally {
            reader.close();
        }
    }

    private void ventaConCeviche() {
        var ceviche = producto(restauranteId, "Ceviche");
        var id = venta("1234.50", dia(21, 12, 0));
        detalle(id, restauranteId, ceviche, 2, "617.25", "Ceviche Clásico");
    }

    @Test
    void exportaLasVentasAPdfConElEncabezadoYElTotal() throws IOException {
        ventaConCeviche();

        var archivo = exportarVentas.exportar(restauranteId, Periodo.SEMANAL, VIERNES, OrdenVentas.MAYOR_VENTA,
                FormatoExport.PDF);

        assertEquals("reporte-ventas-semanal-2026-10-19.pdf", archivo.nombreArchivo());
        assertEquals("application/pdf", archivo.contentType());
        var texto = textoDelPdf(archivo.contenido());
        assertTrue(texto.contains("Reporte de ventas"), texto);
        assertTrue(texto.contains("Chifa Central"), texto);
        assertTrue(texto.contains("Semanal · 19/10/2026 – 25/10/2026"), texto);
        assertTrue(texto.contains("Ventas: 1"), texto);
        assertTrue(texto.contains("Total vendido: S/ 1,234.50"), texto);
        assertTrue(texto.contains("21/10/2026 12:00"), texto);
    }

    @Test
    void exportaLasVentasAXlsx() {
        ventaConCeviche();

        var archivo = exportarVentas.exportar(restauranteId, Periodo.SEMANAL, VIERNES, null, FormatoExport.XLSX);

        assertEquals("reporte-ventas-semanal-2026-10-19.xlsx", archivo.nombreArchivo());
        assertEquals(FormatoExport.XLSX.contentType(), archivo.contentType());
        assertEquals('P', archivo.contenido()[0]);
        assertEquals('K', archivo.contenido()[1]);
    }

    @Test
    void exportaElRankingDeProductosAPdfConElNombreYElMonto() throws IOException {
        ventaConCeviche();

        var archivo = exportarProductos.exportar(restauranteId, Periodo.MENSUAL, VIERNES, FormatoExport.PDF);

        assertEquals("reporte-productos-mensual-2026-10-01.pdf", archivo.nombreArchivo());
        var texto = textoDelPdf(archivo.contenido());
        assertTrue(texto.contains("Reporte de productos"), texto);
        assertTrue(texto.contains("Mensual · 01/10/2026 – 31/10/2026"), texto);
        assertTrue(texto.contains("Ceviche Clásico"), texto);
        assertTrue(texto.contains("S/ 1,234.50"), texto);
    }

    @Test
    void exportaElRankingDeProductosAXlsx() {
        ventaConCeviche();

        var archivo = exportarProductos.exportar(restauranteId, Periodo.MENSUAL, VIERNES, FormatoExport.XLSX);

        assertEquals("reporte-productos-mensual-2026-10-01.xlsx", archivo.nombreArchivo());
        assertEquals('P', archivo.contenido()[0]);
        assertEquals('K', archivo.contenido()[1]);
    }

    // ---------- restaurante ----------

    @Test
    void nombreDelRestaurante() {
        assertEquals("Chifa Central", query.nombreRestaurante(restauranteId));
    }

    @Test
    void restauranteInexistenteFalla() {
        assertThrows(BusinessException.class, () -> query.nombreRestaurante(-1L));
    }
}
