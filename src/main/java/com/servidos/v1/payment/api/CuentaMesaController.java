package com.servidos.v1.payment.api;

import com.servidos.v1.payment.api.dto.CuentaMesaResponse;
import com.servidos.v1.payment.api.dto.PagarCuentaMesaRequest;
import com.servidos.v1.payment.api.dto.PagarCuentaMesaResponse;
import com.servidos.v1.payment.application.cuenta.ConsultarCuentaMesaUseCase;
import com.servidos.v1.payment.application.cuenta.CuentaMesa;
import com.servidos.v1.payment.application.cuenta.CuentaPagada;
import com.servidos.v1.payment.application.cuenta.PagarCuentaMesaCommand;
import com.servidos.v1.payment.application.cuenta.PagarCuentaMesaUseCase;
import com.servidos.v1.shared.security.CurrentUser;
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
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/mesas/{mesaId}/cuenta")
@RequiredArgsConstructor
@Tag(name = "Pagos", description = "Pagos y reembolsos del restaurante actual")
@SecurityRequirement(name = "bearerAuth")
public class CuentaMesaController {

    private final ConsultarCuentaMesaUseCase consultarCuentaMesaUseCase;
    private final PagarCuentaMesaUseCase pagarCuentaMesaUseCase;

    @GetMapping
    @PreAuthorize("hasAnyRole('ADMINISTRADOR','RECEPCION','CAJERO','MESERO')")
    @Operation(summary = "Consultar la cuenta de la mesa",
            description = "Roles: ADMINISTRADOR, RECEPCION, CAJERO y MESERO (el mesero la ve, no la cobra). "
                    + "Pedidos pendientes de cobro de la mesa (rondas de mesa y recojo/delivery vinculados, "
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

    @PostMapping("/pagar")
    @PreAuthorize("hasAnyRole('ADMINISTRADOR','RECEPCION','CAJERO')")
    @Operation(summary = "Cobrar la cuenta de la mesa",
            description = "Cobra en una sola operación atómica todos los pedidos pendientes de la mesa: un pago por "
                    + "pedido, unidos por una cuenta. `pedidoIds` debe coincidir exactamente con los pedidos que "
                    + "devuelve `GET /cuenta`; si la cuenta cambió (ronda nueva, pago individual) responde 400 "
                    + "\"La cuenta cambió, vuelva a cargarla\" y no cobra nada. El monto sale de la base; el vuelto "
                    + "(solo efectivo) se calcula una vez sobre el total. El restauranteId y el cajero se toman del JWT.")
    @ApiResponses({
            @ApiResponse(responseCode = "201", description = "Cuenta cobrada",
                    content = @Content(schema = @Schema(implementation = PagarCuentaMesaResponse.class))),
            @ApiResponse(responseCode = "400", description = "Mesa inexistente, cuenta vacía, la cuenta cambió o monto insuficiente",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "401", description = "Sin sesión",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "403", description = "Sin permiso",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "422", description = "Validación Jakarta (@Valid)",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class)))})
    public ResponseEntity<PagarCuentaMesaResponse> pagar(
            @Parameter(description = "ID de la mesa", example = "1") @PathVariable Long mesaId,
            @Valid @RequestBody PagarCuentaMesaRequest request) {
        var cuenta = pagarCuentaMesaUseCase.ejecutar(
                new PagarCuentaMesaCommand(mesaId, request.metodoPago(), request.montoEntregado(),
                        request.referenciaExterna(), request.pedidoIds()),
                TenantContext.getRestauranteId(), CurrentUser.getCurrentUser());
        return ResponseEntity.status(HttpStatus.CREATED).body(toResponse(cuenta));
    }

    private PagarCuentaMesaResponse toResponse(CuentaPagada c) {
        return new PagarCuentaMesaResponse(
                c.cuentaId(), c.mesaId(), c.numeroMesa(), c.total(), c.metodoPago(), c.vuelto(),
                c.pagos().stream()
                        .map(p -> new PagarCuentaMesaResponse.PagoCuentaResponse(p.pagoId(), p.pedidoId(), p.monto()))
                        .toList());
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
