package com.servidos.v1.ordering.api;

import com.servidos.v1.ordering.api.dto.CambiarEstadoRequest;
import com.servidos.v1.ordering.api.dto.CrearPedidoRequest;
import com.servidos.v1.ordering.api.dto.PedidoResponse;
import com.servidos.v1.ordering.application.pedido.CambiarEstadoPedidoUseCase;
import com.servidos.v1.ordering.application.pedido.ConfirmarPedidoUseCase;
import com.servidos.v1.ordering.application.pedido.CrearPedidoCommand;
import com.servidos.v1.ordering.application.pedido.CrearPedidoItem;
import com.servidos.v1.ordering.application.pedido.CrearPedidoUseCase;
import com.servidos.v1.ordering.domain.Pedido;
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
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/pedidos")
@RequiredArgsConstructor
@Tag(name = "Pedidos", description = "Pedidos del restaurante actual")
@SecurityRequirement(name = "bearerAuth")
public class PedidoController {

    private final CrearPedidoUseCase crearPedidoUseCase;
    private final ConfirmarPedidoUseCase confirmarPedidoUseCase;
    private final CambiarEstadoPedidoUseCase cambiarEstadoPedidoUseCase;

    @PostMapping
    @PreAuthorize("hasRole('ADMINISTRADOR')")
    @Operation(summary = "Crear pedido en el restaurante actual",
            description = "El restauranteId se toma del JWT, nunca del JSON. Precios anti-tamper desde DB.")
    @ApiResponses({
            @ApiResponse(responseCode = "201", description = "Pedido creado",
                    content = @Content(schema = @Schema(implementation = PedidoResponse.class))),
            @ApiResponse(responseCode = "400", description = "Datos inválidos o producto ajeno",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "401", description = "Sin sesión",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "403", description = "Sin permiso",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "422", description = "Validación Jakarta (@Valid)",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class)))})
    public ResponseEntity<PedidoResponse> crear(@Valid @RequestBody CrearPedidoRequest request) {
        var creado = crearPedidoUseCase.ejecutar(
                new CrearPedidoCommand(
                        request.tipoPedido(), request.mesaId(), request.observacion(),
                        request.repartidorNombre(),
                        request.items().stream()
                                .map(i -> new CrearPedidoItem(i.productoId(), i.cantidad(), i.observacion()))
                                .toList()),
                TenantContext.getRestauranteId());
        return ResponseEntity.status(HttpStatus.CREATED).body(toResponse(creado));
    }

    @PostMapping("/{id}/confirmar")
    @PreAuthorize("hasRole('ADMINISTRADOR')")
    @Operation(summary = "Confirmar pedido (PENDIENTE → EN_PREPARACION)",
            description = "Lo usa RECEPCIÓN para mover delivery. Delega la matriz de transición.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Pedido confirmado",
                    content = @Content(schema = @Schema(implementation = PedidoResponse.class))),
            @ApiResponse(responseCode = "400", description = "Inexistente, de otro tenant o transición inválida",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "401", description = "Sin sesión",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "403", description = "Sin permiso",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class)))})
    public ResponseEntity<PedidoResponse> confirmar(
            @Parameter(description = "ID del pedido", example = "1") @PathVariable Long id) {
        return ResponseEntity.ok(toResponse(
                confirmarPedidoUseCase.ejecutar(id, TenantContext.getRestauranteId())));
    }

    @PatchMapping("/{id}/estado")
    @PreAuthorize("hasRole('ADMINISTRADOR')")
    @Operation(summary = "Cambiar estado del pedido",
            description = "Respeta la matriz por tipo (MESA/DELIVERY). CANCELADO en pedido pagado → 400.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Estado actualizado",
                    content = @Content(schema = @Schema(implementation = PedidoResponse.class))),
            @ApiResponse(responseCode = "400", description = "Inexistente, de otro tenant, transición inválida o pedido pagado",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "401", description = "Sin sesión",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "403", description = "Sin permiso",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "422", description = "Validación Jakarta (@Valid)",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class)))})
    public ResponseEntity<PedidoResponse> cambiarEstado(
            @Parameter(description = "ID del pedido", example = "1") @PathVariable Long id,
            @Valid @RequestBody CambiarEstadoRequest request) {
        return ResponseEntity.ok(toResponse(
                cambiarEstadoPedidoUseCase.ejecutar(id, TenantContext.getRestauranteId(), request.estado())));
    }

    private PedidoResponse toResponse(Pedido p) {
        return new PedidoResponse(
                p.getPedido_id(), p.getTipoPedido(), p.getMesa_id(), p.getEstado(), p.getTotal());
    }
}
