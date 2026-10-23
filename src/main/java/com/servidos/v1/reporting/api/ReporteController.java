package com.servidos.v1.reporting.api;

import com.servidos.v1.V1Application;
import com.servidos.v1.reporting.api.dto.ReporteProductosResponse;
import com.servidos.v1.reporting.api.dto.ReporteVentasResponse;
import com.servidos.v1.reporting.api.dto.VentaPedidoResponse;
import com.servidos.v1.reporting.api.dto.VentaProductoResponse;
import com.servidos.v1.reporting.application.ventas.GenerarReporteProductosUseCase;
import com.servidos.v1.reporting.application.ventas.GenerarReporteVentasUseCase;
import com.servidos.v1.reporting.domain.OrdenVentas;
import com.servidos.v1.reporting.domain.Periodo;
import com.servidos.v1.shared.exception.ErrorResponse;
import com.servidos.v1.shared.security.TenantContext;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.time.ZoneId;
import java.util.stream.IntStream;

@RestController
@RequestMapping("/api/v1/reportes")
@RequiredArgsConstructor
@Tag(name = "Reportes", description = "Ventas y productos más vendidos del restaurante actual (solo administrador)")
@SecurityRequirement(name = "bearerAuth")
public class ReporteController {

    private final GenerarReporteVentasUseCase generarReporteVentasUseCase;
    private final GenerarReporteProductosUseCase generarReporteProductosUseCase;

    @GetMapping("/ventas")
    @PreAuthorize("hasRole('ADMINISTRADOR')")
    @Operation(summary = "Ventas del período",
            description = "Pedidos cobrados (pago PAGADO, no cancelados) del período calendario que contiene `fecha` "
                    + "(hoy en Lima si se omite): el día, la semana lunes a domingo o el mes. El período se mide por "
                    + "la fecha de pago. `orden`: MAYOR_VENTA, MAS_RECIENTES (default) o MAS_ANTIGUOS. "
                    + "Paginado (`size` default 20, máximo 100). Trae la cantidad de ventas y el total vendido.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Reporte del período"),
            @ApiResponse(responseCode = "400", description = "Parámetros inválidos",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "401", description = "Sin sesión",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "403", description = "Sin permiso",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class)))})
    public ResponseEntity<ReporteVentasResponse> ventas(
            @Parameter(description = "Tipo de período") @RequestParam(defaultValue = "DIARIO") Periodo periodo,
            @Parameter(description = "Día de referencia (yyyy-MM-dd); por defecto hoy", example = "2026-10-23")
            @RequestParam(required = false) LocalDate fecha,
            @Parameter(description = "Orden de los pedidos") @RequestParam(defaultValue = "MAS_RECIENTES")
            OrdenVentas orden,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        var reporte = generarReporteVentasUseCase.generar(
                TenantContext.getRestauranteId(), periodo, fechaOHoy(fecha), orden, page, size);
        return ResponseEntity.ok(new ReporteVentasResponse(
                reporte.periodo(), reporte.rango().desde(), reporte.rango().hasta(),
                reporte.resumen().cantidadVentas(), reporte.resumen().totalVendido(),
                reporte.ventas().map(v -> new VentaPedidoResponse(
                        v.pedidoId(), v.fechaPago(), v.tipoPedido(), v.metodoPago(), v.total()))));
    }

    @GetMapping("/productos")
    @PreAuthorize("hasRole('ADMINISTRADOR')")
    @Operation(summary = "Productos más vendidos del período",
            description = "Ranking de productos de las ventas del período (mismas reglas que /ventas), "
                    + "de mayor a menor monto vendido. Sin paginar. Trae la cantidad de ventas y el total vendido.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Ranking del período"),
            @ApiResponse(responseCode = "400", description = "Parámetros inválidos",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "401", description = "Sin sesión",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "403", description = "Sin permiso",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class)))})
    public ResponseEntity<ReporteProductosResponse> productos(
            @Parameter(description = "Tipo de período") @RequestParam(defaultValue = "DIARIO") Periodo periodo,
            @Parameter(description = "Día de referencia (yyyy-MM-dd); por defecto hoy", example = "2026-10-23")
            @RequestParam(required = false) LocalDate fecha) {
        var reporte = generarReporteProductosUseCase.generar(
                TenantContext.getRestauranteId(), periodo, fechaOHoy(fecha));
        var productos = reporte.productos();
        return ResponseEntity.ok(new ReporteProductosResponse(
                reporte.periodo(), reporte.rango().desde(), reporte.rango().hasta(),
                reporte.resumen().cantidadVentas(), reporte.resumen().totalVendido(),
                IntStream.range(0, productos.size())
                        .mapToObj(i -> new VentaProductoResponse(i + 1, productos.get(i).productoId(),
                                productos.get(i).nombre(), productos.get(i).cantidad(), productos.get(i).monto()))
                        .toList()));
    }

    private static LocalDate fechaOHoy(LocalDate fecha) {
        return fecha != null ? fecha : LocalDate.now(ZoneId.of(V1Application.ZONA_HORARIA));
    }
}
