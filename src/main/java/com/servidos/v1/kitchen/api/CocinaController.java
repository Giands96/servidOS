package com.servidos.v1.kitchen.api;

import com.servidos.v1.kitchen.api.dto.CocinaDetalleResponse;
import com.servidos.v1.kitchen.api.dto.PreparacionPedidoResponse;
import com.servidos.v1.kitchen.application.cocina.GestionarColaCocinaUseCase;
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
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/v1/cocina")
@RequiredArgsConstructor
@Tag(name = "Cocina", description = "Cola de preparación del restaurante actual")
@SecurityRequirement(name = "bearerAuth")
public class CocinaController {

    private final GestionarColaCocinaUseCase colaUseCase;

    @GetMapping("/cola")
    @PreAuthorize("hasAnyRole('ADMINISTRADOR','RECEPCION','COCINERO')")
    @Operation(summary = "Listar pedidos en preparación",
            description = "Recibe pedido de recepción para preparar. Solo EN_PREPARACION del tenant actual.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Cola de cocina"),
            @ApiResponse(responseCode = "401", description = "Sin sesión",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "403", description = "Sin permiso",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class)))})
    public ResponseEntity<List<PreparacionPedidoResponse>> cola() {
        var cola = colaUseCase.listarEnPreparacion(TenantContext.getRestauranteId());
        return ResponseEntity.ok(cola.stream().map(p -> new PreparacionPedidoResponse(
                p.getPedido_id(),
                p.getMesa_id(),
                p.getTipoPedido(),
                p.getEstado(),
                p.getObservacion(),
                p.getTotal(),
                p.getCreated_at(),
                p.getItems() == null ? List.of() : p.getItems().stream()
                        .map(i -> new CocinaDetalleResponse(
                                i.getDetalle_id(),
                                i.getProducto_id(),
                                i.getNombre_producto(),
                                i.getCantidad(),
                                i.getObservacion()))
                        .toList())).toList());
    }

    @PostMapping("/pedidos/{id}/listo")
    @PreAuthorize("hasAnyRole('ADMINISTRADOR','RECEPCION','COCINERO')")
    @Operation(summary = "Notificar pedido listo para mesa",
            description = "Marca EN_PREPARACION → LISTO. Solo del tenant actual.")
    @ApiResponses({
            @ApiResponse(responseCode = "204", description = "Pedido listo"),
            @ApiResponse(responseCode = "400", description = "Inexistente, de otro tenant o no está en preparación",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "401", description = "Sin sesión",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "403", description = "Sin permiso",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class)))})
    public ResponseEntity<Void> listo(
            @Parameter(description = "ID del pedido", example = "1") @PathVariable Long id) {
        colaUseCase.marcarListo(id, TenantContext.getRestauranteId());
        return ResponseEntity.noContent().build();
    }
}
