package com.servidos.v1.payment.api;

import com.servidos.v1.payment.api.dto.PagoResponse;
import com.servidos.v1.payment.api.dto.RegistrarPagoRequest;
import com.servidos.v1.payment.application.pago.RegistrarPagoCommand;
import com.servidos.v1.payment.application.pago.RegistrarPagoUseCase;
import com.servidos.v1.payment.domain.Pago;
import com.servidos.v1.shared.exception.ErrorResponse;
import com.servidos.v1.shared.security.CurrentUser;
import com.servidos.v1.shared.security.TenantContext;
import io.swagger.v3.oas.annotations.Operation;
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
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/pagos")
@RequiredArgsConstructor
@Tag(name = "Pagos", description = "Pagos del restaurante actual")
@SecurityRequirement(name = "bearerAuth")
public class PagoController {

    private final RegistrarPagoUseCase registrarPagoUseCase;

    @PostMapping
    @PreAuthorize("hasRole('ADMINISTRADOR')")
    @Operation(summary = "Registrar pago del pedido en el restaurante actual",
            description = "El restauranteId y el cajero se toman del JWT, nunca del JSON. Monto anti-tamper desde DB; vuelto solo en efectivo.")
    @ApiResponses({
            @ApiResponse(responseCode = "201", description = "Pago registrado",
                    content = @Content(schema = @Schema(implementation = PagoResponse.class))),
            @ApiResponse(responseCode = "400", description = "Pedido inexistente, ya pagado o monto insuficiente",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "401", description = "Sin sesión",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "403", description = "Sin permiso",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "422", description = "Validación Jakarta (@Valid)",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class)))})
    public ResponseEntity<PagoResponse> registrar(@Valid @RequestBody RegistrarPagoRequest request) {
        var registrado = registrarPagoUseCase.ejecutar(
                new RegistrarPagoCommand(
                        request.pedidoId(), request.metodoPago(),
                        request.montoEntregado(), request.referenciaExterna()),
                TenantContext.getRestauranteId(), CurrentUser.getCurrentUser());
        return ResponseEntity.status(HttpStatus.CREATED).body(toResponse(registrado));
    }

    private PagoResponse toResponse(Pago p) {
        return new PagoResponse(
                p.getPago_id(), p.getPedido_id(), p.getMonto(),
                p.getVuelto(), p.getMetodo_pago(), p.getEstado());
    }
}
