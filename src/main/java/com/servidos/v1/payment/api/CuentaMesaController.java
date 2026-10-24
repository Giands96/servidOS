package com.servidos.v1.payment.api;

import com.servidos.v1.payment.api.dto.CuentaMesaResponse;
import com.servidos.v1.payment.application.cuenta.ConsultarCuentaMesaUseCase;
import com.servidos.v1.payment.application.cuenta.CuentaMesa;
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
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/mesas/{mesaId}/cuenta")
@RequiredArgsConstructor
@Tag(name = "Pagos", description = "Pagos y reembolsos del restaurante actual")
@SecurityRequirement(name = "bearerAuth")
public class CuentaMesaController {

    private final ConsultarCuentaMesaUseCase consultarCuentaMesaUseCase;

    @GetMapping
    @PreAuthorize("hasAnyRole('ADMINISTRADOR','RECEPCION','CAJERO')")
    @Operation(summary = "Consultar la cuenta de la mesa",
            description = "Pedidos pendientes de cobro de la mesa (rondas de mesa y recojo/delivery vinculados, "
                    + "no cancelados y sin pago) con sus ítems y el total calculado en el servidor. "
                    + "El restauranteId se toma del JWT. Sin pendientes devuelve `pedidos: []` y `total: 0`.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Cuenta de la mesa",
                    content = @Content(schema = @Schema(implementation = CuentaMesaResponse.class))),
            @ApiResponse(responseCode = "400", description = "Mesa inexistente o de otro restaurante",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "401", description = "Sin sesión",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "403", description = "Sin permiso",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class)))})
    public ResponseEntity<CuentaMesaResponse> consultar(
            @Parameter(description = "ID de la mesa", example = "1") @PathVariable Long mesaId) {
        return ResponseEntity.ok(toResponse(
                consultarCuentaMesaUseCase.ejecutar(mesaId, TenantContext.getRestauranteId())));
    }

    private CuentaMesaResponse toResponse(CuentaMesa c) {
        return new CuentaMesaResponse(
                c.mesaId(), c.numero(), c.estado(), c.comensales(), c.capacidad(),
                c.pedidos().stream()
                        .map(p -> new CuentaMesaResponse.PedidoCuentaResponse(
                                p.pedidoId(), p.tipoPedido(), p.estado(), p.createdAt(),
                                p.items().stream()
                                        .map(i -> new CuentaMesaResponse.ItemCuentaResponse(
                                                i.nombre(), i.cantidad(), i.subtotal()))
                                        .toList(),
                                p.observacion(), p.total()))
                        .toList(),
                c.total());
    }
}
