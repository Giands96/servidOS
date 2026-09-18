package com.servidos.v1.tenant.api;

import com.servidos.v1.shared.exception.ErrorResponse;
import com.servidos.v1.shared.security.TenantContext;
import com.servidos.v1.tenant.api.dto.CrearRestauranteRequest;
import com.servidos.v1.tenant.api.dto.RestauranteResponse;
import com.servidos.v1.tenant.api.dto.SuscripcionResponse;
import com.servidos.v1.tenant.application.restaurante.CrearRestauranteCommand;
import com.servidos.v1.tenant.application.restaurante.CrearRestauranteUseCase;
import com.servidos.v1.tenant.application.restaurante.ObtenerRestauranteUseCase;
import com.servidos.v1.tenant.application.suscripcion.CambiarPlanUseCase;
import com.servidos.v1.tenant.application.suscripcion.GestionarSuscripcionUseCase;
import com.servidos.v1.tenant.application.suscripcion.RenovarSuscripcionUseCase;
import com.servidos.v1.tenant.domain.Restaurante;
import com.servidos.v1.tenant.domain.Suscripcion;
import io.swagger.v3.oas.annotations.Operation;
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
@RequestMapping("/api/v1/restaurantes")
@RequiredArgsConstructor
@Tag(name = "Restaurantes", description = "Tenant actual y gestión de plataforma")
@SecurityRequirement(name = "bearerAuth")
public class RestauranteController {

    private final CrearRestauranteUseCase crearRestauranteUseCase;
    private final ObtenerRestauranteUseCase obtenerRestauranteUseCase;
    private final GestionarSuscripcionUseCase gestionarSuscripcionUseCase;
    private final CambiarPlanUseCase cambiarPlanUseCase;
    private final RenovarSuscripcionUseCase renovarSuscripcionUseCase;

    @PostMapping
    @PreAuthorize("hasAnyRole('SUPERADMIN','ADMIN')")
    @Operation(summary = "Crear restaurante con plan elegido y demo opcional",
            description = "planId obligatorio. demo=true exige demoDias (1-30); demo=false crea suscripción directa de 30 días.")
    @ApiResponses({
            @ApiResponse(responseCode = "201", description = "Restaurante creado"),
            @ApiResponse(responseCode = "400", description = "Slug duplicado, plan inexistente o demo inválida",
                    content = @io.swagger.v3.oas.annotations.media.Content(
                            schema = @io.swagger.v3.oas.annotations.media.Schema(implementation = ErrorResponse.class)))})
    public ResponseEntity<RestauranteResponse> crear(@Valid @RequestBody CrearRestauranteRequest request) {
        var creado = crearRestauranteUseCase.ejecutar(new CrearRestauranteCommand(
                request.slug(), request.nombre(), request.direccion(), request.planId(),
                request.demo(), request.demoDias()));
        return ResponseEntity.status(HttpStatus.CREATED).body(toResponse(creado));
    }

    @GetMapping("/actual")
    @PreAuthorize("isAuthenticated()")
    @Operation(summary = "Ver el restaurante actual")
    @ApiResponse(responseCode = "200", description = "Restaurante del tenant")
    public ResponseEntity<RestauranteResponse> actual() {
        return ResponseEntity.ok(toResponse(
                obtenerRestauranteUseCase.obtener(TenantContext.getRestauranteId())));
    }

    @GetMapping("/actual/suscripcion")
    @PreAuthorize("isAuthenticated()")
    @Operation(summary = "Ver la suscripción actual")
    @ApiResponse(responseCode = "200", description = "Suscripción del tenant")
    public ResponseEntity<SuscripcionResponse> suscripcionActual() {
        return ResponseEntity.ok(toResponse(
                obtenerRestauranteUseCase.obtenerSuscripcion(TenantContext.getRestauranteId())));
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasRole('SUPERADMIN')")
    @Operation(summary = "Ver un restaurante por id (plataforma)")
    @ApiResponse(responseCode = "200", description = "Restaurante")
    public ResponseEntity<RestauranteResponse> porId(@PathVariable Long id) {
        return ResponseEntity.ok(toResponse(obtenerRestauranteUseCase.obtener(id)));
    }

    @GetMapping("/{id}/suscripcion")
    @PreAuthorize("hasRole('SUPERADMIN')")
    @Operation(summary = "Ver la suscripción de un restaurante (plataforma)")
    @ApiResponse(responseCode = "200", description = "Suscripción")
    public ResponseEntity<SuscripcionResponse> suscripcionPorId(@PathVariable Long id) {
        return ResponseEntity.ok(toResponse(obtenerRestauranteUseCase.obtenerSuscripcion(id)));
    }

    private RestauranteResponse toResponse(Restaurante r) {
        return new RestauranteResponse(r.getRestaurante_id(), r.getSlug(), r.getNombre(),
                r.getDireccion(), r.getEstado());
    }

    private SuscripcionResponse toResponse(Suscripcion s) {
        return new SuscripcionResponse(s.getSuscripcion_id(), s.getRestaurante_id(), s.getPlan_id(),
                s.getEstado(), s.getFecha_inicio(), s.getFecha_fin());
    }
}
