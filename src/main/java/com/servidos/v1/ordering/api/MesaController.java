package com.servidos.v1.ordering.api;

import com.servidos.v1.ordering.api.dto.ActualizarMesaRequest;
import com.servidos.v1.ordering.api.dto.CrearMesaRequest;
import com.servidos.v1.ordering.api.dto.MesaResponse;
import com.servidos.v1.ordering.api.dto.OcuparMesaRequest;
import com.servidos.v1.ordering.application.mesa.ActualizarMesaCommand;
import com.servidos.v1.ordering.application.mesa.ActualizarMesaUseCase;
import com.servidos.v1.ordering.application.mesa.CrearMesaCommand;
import com.servidos.v1.ordering.application.mesa.CrearMesaUseCase;
import com.servidos.v1.ordering.application.mesa.DesactivarMesaUseCase;
import com.servidos.v1.ordering.application.mesa.LiberarMesaUseCase;
import com.servidos.v1.ordering.application.mesa.ListarMesasUseCase;
import com.servidos.v1.ordering.application.mesa.OcuparMesaCommand;
import com.servidos.v1.ordering.application.mesa.OcuparMesaUseCase;
import com.servidos.v1.ordering.application.mesa.ReactivarMesaUseCase;
import com.servidos.v1.ordering.domain.EstadoMesa;
import com.servidos.v1.ordering.domain.Mesa;
import com.servidos.v1.shared.exception.ErrorResponse;
import com.servidos.v1.shared.security.TenantContext;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.ArraySchema;
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
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/v1/mesas")
@RequiredArgsConstructor
@Tag(name = "Mesas", description = "Mesas del restaurante actual")
@SecurityRequirement(name = "bearerAuth")
public class MesaController {

    private final CrearMesaUseCase crearMesaUseCase;
    private final ListarMesasUseCase listarMesasUseCase;
    private final ActualizarMesaUseCase actualizarMesaUseCase;
    private final OcuparMesaUseCase ocuparMesaUseCase;
    private final LiberarMesaUseCase liberarMesaUseCase;
    private final DesactivarMesaUseCase desactivarMesaUseCase;
    private final ReactivarMesaUseCase reactivarMesaUseCase;

    @PostMapping
    @PreAuthorize("hasRole('ADMINISTRADOR')")
    @Operation(summary = "Crear mesa en el restaurante actual",
            description = "Nace LIBRE. El número es único por restaurante y la capacidad es la cantidad de asientos.")
    @ApiResponses({
            @ApiResponse(responseCode = "201", description = "Mesa creada",
                    content = @Content(schema = @Schema(implementation = MesaResponse.class))),
            @ApiResponse(responseCode = "400", description = "Número duplicado o datos inválidos",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "401", description = "Sin sesión",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "403", description = "Sin permiso",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "422", description = "Validación Jakarta (@Valid)",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class)))})
    public ResponseEntity<MesaResponse> crear(@Valid @RequestBody CrearMesaRequest request) {
        var creada = crearMesaUseCase.ejecutar(
                new CrearMesaCommand(request.numero(), request.capacidad()),
                TenantContext.getRestauranteId());
        return ResponseEntity.status(HttpStatus.CREATED).body(toResponse(creada));
    }

    @GetMapping
    @PreAuthorize("hasAnyRole('ADMINISTRADOR','RECEPCION','CAJERO','MESERO')")
    @Operation(summary = "Listar mesas del restaurante actual",
            description = "Solo mesas activas, ordenadas por número. Filtro opcional `estado` (LIBRE u OCUPADA).")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Mesas del tenant",
                    content = @Content(array = @ArraySchema(schema = @Schema(implementation = MesaResponse.class)))),
            @ApiResponse(responseCode = "401", description = "Sin sesión",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "403", description = "Sin permiso",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class)))})
    public ResponseEntity<List<MesaResponse>> listar(
            @Parameter(description = "Solo mesas en este estado") @RequestParam(required = false) EstadoMesa estado) {
        return ResponseEntity.ok(listarMesasUseCase.listar(TenantContext.getRestauranteId(), estado).stream()
                .map(this::toResponse)
                .toList());
    }

    @GetMapping("/inactivas")
    @PreAuthorize("hasRole('ADMINISTRADOR')")
    @Operation(summary = "Listar mesas dadas de baja del restaurante actual",
            description = "Ordenadas por número. Se pueden reactivar con `POST /{id}/reactivar`.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Mesas inactivas del tenant",
                    content = @Content(array = @ArraySchema(schema = @Schema(implementation = MesaResponse.class)))),
            @ApiResponse(responseCode = "401", description = "Sin sesión",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "403", description = "Sin permiso",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class)))})
    public ResponseEntity<List<MesaResponse>> listarInactivas() {
        return ResponseEntity.ok(listarMesasUseCase.listarInactivas(TenantContext.getRestauranteId()).stream()
                .map(this::toResponse)
                .toList());
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasRole('ADMINISTRADOR')")
    @Operation(summary = "Actualizar número y capacidad de la mesa",
            description = "La capacidad no puede bajar de los comensales sentados. Cross-tenant → 400.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Mesa actualizada",
                    content = @Content(schema = @Schema(implementation = MesaResponse.class))),
            @ApiResponse(responseCode = "400", description = "Inexistente, número duplicado o capacidad menor a los comensales",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "401", description = "Sin sesión",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "403", description = "Sin permiso",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "422", description = "Validación Jakarta (@Valid)",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class)))})
    public ResponseEntity<MesaResponse> actualizar(
            @Parameter(description = "ID de la mesa", example = "1") @PathVariable Long id,
            @Valid @RequestBody ActualizarMesaRequest request) {
        var actualizada = actualizarMesaUseCase.ejecutar(
                new ActualizarMesaCommand(id, request.numero(), request.capacidad()),
                TenantContext.getRestauranteId());
        return ResponseEntity.ok(toResponse(actualizada));
    }

    @PostMapping("/{id}/ocupar")
    @PreAuthorize("hasAnyRole('ADMINISTRADOR','RECEPCION','MESERO')")
    @Operation(summary = "Ocupar mesa (LIBRE → OCUPADA)",
            description = "Registra los comensales (de 1 a la capacidad de la mesa).")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Mesa ocupada",
                    content = @Content(schema = @Schema(implementation = MesaResponse.class))),
            @ApiResponse(responseCode = "400", description = "Inexistente, ya ocupada o comensales fuera de rango",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "401", description = "Sin sesión",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "403", description = "Sin permiso",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "422", description = "Validación Jakarta (@Valid)",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class)))})
    public ResponseEntity<MesaResponse> ocupar(
            @Parameter(description = "ID de la mesa", example = "1") @PathVariable Long id,
            @Valid @RequestBody OcuparMesaRequest request) {
        return ResponseEntity.ok(toResponse(ocuparMesaUseCase.ejecutar(
                new OcuparMesaCommand(id, request.comensales()), TenantContext.getRestauranteId())));
    }

    @PostMapping("/{id}/liberar")
    @PreAuthorize("hasAnyRole('ADMINISTRADOR','RECEPCION','MESERO')")
    @Operation(summary = "Liberar mesa (OCUPADA → LIBRE)",
            description = "Solo sin pedidos abiertos. Con pedidos abiertos la mesa se libera sola al "
                    + "entregar y cobrar el último, o al cancelarlo.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Mesa liberada",
                    content = @Content(schema = @Schema(implementation = MesaResponse.class))),
            @ApiResponse(responseCode = "400", description = "Inexistente, ya libre o con pedidos abiertos",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "401", description = "Sin sesión",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "403", description = "Sin permiso",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class)))})
    public ResponseEntity<MesaResponse> liberar(
            @Parameter(description = "ID de la mesa", example = "1") @PathVariable Long id) {
        return ResponseEntity.ok(toResponse(liberarMesaUseCase.ejecutar(id, TenantContext.getRestauranteId())));
    }

    @PostMapping("/{id}/desactivar")
    @PreAuthorize("hasRole('ADMINISTRADOR')")
    @Operation(summary = "Dar de baja una mesa (baja lógica)",
            description = "Solo una mesa LIBRE y sin pedidos abiertos. No se borra: el historial de pedidos "
                    + "y los reportes se conservan, y el número queda disponible para otra mesa.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Mesa dada de baja",
                    content = @Content(schema = @Schema(implementation = MesaResponse.class))),
            @ApiResponse(responseCode = "400", description = "Inexistente, ya inactiva, ocupada o con pedidos abiertos",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "401", description = "Sin sesión",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "403", description = "Sin permiso",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class)))})
    public ResponseEntity<MesaResponse> desactivar(
            @Parameter(description = "ID de la mesa", example = "1") @PathVariable Long id) {
        return ResponseEntity.ok(toResponse(desactivarMesaUseCase.ejecutar(id, TenantContext.getRestauranteId())));
    }

    @PostMapping("/{id}/reactivar")
    @PreAuthorize("hasRole('ADMINISTRADOR')")
    @Operation(summary = "Reactivar una mesa dada de baja",
            description = "Falla si otra mesa activa ya usa su número.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Mesa reactivada",
                    content = @Content(schema = @Schema(implementation = MesaResponse.class))),
            @ApiResponse(responseCode = "400", description = "Inexistente, ya activa o número usado por otra mesa activa",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "401", description = "Sin sesión",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "403", description = "Sin permiso",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class)))})
    public ResponseEntity<MesaResponse> reactivar(
            @Parameter(description = "ID de la mesa", example = "1") @PathVariable Long id) {
        return ResponseEntity.ok(toResponse(reactivarMesaUseCase.ejecutar(id, TenantContext.getRestauranteId())));
    }

    private MesaResponse toResponse(Mesa m) {
        return new MesaResponse(m.getMesa_id(), m.getNumero(), m.getCapacidad(), m.getComensales(), m.getEstado(),
                m.isActiva());
    }
}
