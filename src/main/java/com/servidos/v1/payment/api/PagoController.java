package com.servidos.v1.payment.api;

import com.servidos.v1.payment.api.dto.PagoResponse;
import com.servidos.v1.payment.api.dto.ReembolsoRequest;
import com.servidos.v1.payment.application.pago.RegistrarPagoUseCase;
import com.servidos.v1.payment.application.pago.RegistrarReembolsoUseCase;
import com.servidos.v1.payment.application.pago.ReembolsarPagoCommand;
import com.servidos.v1.payment.domain.Pago;
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
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/pagos")
@RequiredArgsConstructor
@Tag(name = "Pagos", description = "Pagos y reembolsos del restaurante actual")
@SecurityRequirement(name = "bearerAuth")
public class PagoController {

    private final RegistrarPagoUseCase registrarPagoUseCase;
    private final RegistrarReembolsoUseCase registrarReembolsoUseCase;

    @PostMapping("/{id}/reembolso")
    @PreAuthorize("hasRole('ADMINISTRADOR')")
    @Operation(summary = "Reembolsar pago total",
            description = "Solo ADMINISTRADOR con motivo obligatorio. El pedido reembolsado vuelve a ser cancelable.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Pago reembolsado",
                    content = @Content(schema = @Schema(implementation = PagoResponse.class))),
            @ApiResponse(responseCode = "400", description = "Inexistente, de otro tenant, ya reembolsado o sin motivo",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "401", description = "Sin sesión",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "403", description = "Sin permiso",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "422", description = "Validación Jakarta (@Valid)",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class)))})
    public ResponseEntity<PagoResponse> reembolsar(
            @Parameter(description = "ID del pago", example = "5") @PathVariable Long id,
            @Valid @RequestBody ReembolsoRequest request) {
        return ResponseEntity.ok(toResponse(
                registrarReembolsoUseCase.ejecutar(
                        new ReembolsarPagoCommand(id, request.motivo()),
                        TenantContext.getRestauranteId())));
    }

    private PagoResponse toResponse(Pago p) {
        return new PagoResponse(
                p.getPago_id(), p.getPedido_id(), p.getMonto(), p.getEstado());
    }
}
