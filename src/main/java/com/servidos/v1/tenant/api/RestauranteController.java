package com.servidos.v1.tenant.api;

import com.servidos.v1.shared.exception.ErrorResponse;
import com.servidos.v1.shared.security.TenantContext;
import com.servidos.v1.tenant.api.dto.CambiarEstadoRestauranteRequest;
import com.servidos.v1.tenant.api.dto.CambiarPlanRequest;
import com.servidos.v1.tenant.api.dto.CrearRestauranteRequest;
import com.servidos.v1.tenant.api.dto.RenovarSuscripcionRequest;
import com.servidos.v1.tenant.api.dto.RestauranteDashboardResponse;
import com.servidos.v1.tenant.api.dto.RestauranteResponse;
import com.servidos.v1.tenant.api.dto.SuscripcionDashboardResponse;
import com.servidos.v1.tenant.api.dto.SuscripcionResponse;
import com.servidos.v1.tenant.application.restaurante.CrearRestauranteCommand;
import com.servidos.v1.tenant.application.suscripcion.SuscripcionConPlan;
import com.servidos.v1.tenant.application.restaurante.CambiarEstadoRestauranteUseCase;
import com.servidos.v1.tenant.application.restaurante.CrearRestauranteUseCase;
import com.servidos.v1.tenant.application.restaurante.ListarRestaurantesUseCase;
import com.servidos.v1.tenant.application.restaurante.RestauranteConSuscripcion;
import com.servidos.v1.tenant.application.restaurante.ObtenerRestauranteUseCase;
import com.servidos.v1.tenant.application.suscripcion.CambiarPlanCommand;
import com.servidos.v1.tenant.application.suscripcion.CambiarPlanUseCase;
import com.servidos.v1.tenant.application.suscripcion.GestionarSuscripcionUseCase;
import com.servidos.v1.tenant.application.suscripcion.RenovarSuscripcionCommand;
import com.servidos.v1.tenant.application.suscripcion.RenovarSuscripcionUseCase;
import com.servidos.v1.tenant.domain.Restaurante;
import com.servidos.v1.tenant.domain.Suscripcion;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
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
    private final ListarRestaurantesUseCase listarRestaurantesUseCase;
    private final GestionarSuscripcionUseCase gestionarSuscripcionUseCase;
    private final CambiarPlanUseCase cambiarPlanUseCase;
    private final RenovarSuscripcionUseCase renovarSuscripcionUseCase;
    private final CambiarEstadoRestauranteUseCase cambiarEstadoRestauranteUseCase;

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

    @GetMapping
    @PreAuthorize("hasRole('SUPERADMIN')")
    @Operation(summary = "Listar todos los restaurantes (plataforma)",
            description = "Dashboard: paginado (`?page=&size=`, default `size=10`). Cada fila trae la suscripción actual y el nombre del plan en una sola consulta con JOIN (sin N+1).")
    @ApiResponse(responseCode = "200", description = "Página de restaurantes con suscripción")
    public ResponseEntity<Page<RestauranteDashboardResponse>> listarTodos(
            @PageableDefault(size = 10) Pageable pageable) {
        return ResponseEntity.ok(listarRestaurantesUseCase.listar(pageable).map(this::toDashboardResponse));
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

    @PatchMapping("/{id}/estado")
    @PreAuthorize("hasRole('SUPERADMIN')")
    @Operation(summary = "Suspender o reactivar un restaurante (plataforma)",
            description = "Palanca de acceso del tenant. INACTIVO le deja leer sus datos pero frena toda "
                    + "escritura con 402, para que el frontend muestre el aviso de pago. Es un acto de la "
                    + "plataforma porque el cobro se resuelve fuera del sistema y acá no hay forma de "
                    + "verificarlo. No toca la suscripción: suspender no cancela el plan.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Estado actualizado"),
            @ApiResponse(responseCode = "400", description = "Restaurante inexistente o ya está en ese estado",
                    content = @io.swagger.v3.oas.annotations.media.Content(
                            schema = @io.swagger.v3.oas.annotations.media.Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "403", description = "Sin rol de plataforma",
                    content = @io.swagger.v3.oas.annotations.media.Content(
                            schema = @io.swagger.v3.oas.annotations.media.Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "422", description = "Validación Jakarta (@Valid)",
                    content = @io.swagger.v3.oas.annotations.media.Content(
                            schema = @io.swagger.v3.oas.annotations.media.Schema(implementation = ErrorResponse.class)))})
    public ResponseEntity<RestauranteResponse> cambiarEstado(
            @Parameter(description = "ID del restaurante", example = "1") @PathVariable Long id,
            @Valid @RequestBody CambiarEstadoRestauranteRequest request) {
        var actualizado = cambiarEstadoRestauranteUseCase.ejecutar(id, request.estado());
        return ResponseEntity.ok(toResponse(actualizado));
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

    @PatchMapping("/actual/plan")
    @PreAuthorize("hasAnyRole('ADMINISTRADOR','SUPERADMIN')")
    @Operation(summary = "Cambiar el plan del restaurante actual",
            description = "Cierra la suscripción ACTIVA y abre una nueva de 30 días con el plan elegido. Requiere confirmación explícita y la contraseña del usuario en sesión.")
    @ApiResponse(responseCode = "200", description = "Nueva suscripción")
    public ResponseEntity<SuscripcionResponse> cambiarPlan(@Valid @RequestBody CambiarPlanRequest request) {
        var nueva = cambiarPlanUseCase.ejecutar(new CambiarPlanCommand(
                TenantContext.getRestauranteId(), request.nuevoPlanId(),
                request.confirmado(), request.password()));
        return ResponseEntity.ok(toResponse(nueva));
    }

    @PostMapping("/actual/suscripcion/renovar")
    @PreAuthorize("hasAnyRole('ADMINISTRADOR','SUPERADMIN')")
    @Operation(summary = "Renovar la suscripción un mes calendario",
            description = "Exige la contraseña del usuario en sesión: renovar crea una obligación "
                    + "de pago nueva y no puede ser un clic casual que un restaurante vencido use "
                    + "para auto-devolverse el acceso.")
    @ApiResponse(responseCode = "200", description = "Suscripción renovada")
    public ResponseEntity<SuscripcionResponse> renovar(@Valid @RequestBody RenovarSuscripcionRequest request) {
        var nueva = renovarSuscripcionUseCase.ejecutar(
                new RenovarSuscripcionCommand(TenantContext.getRestauranteId(), request.password()));
        return ResponseEntity.ok(toResponse(nueva));
    }

    @PostMapping("/actual/suscripcion/cancelar")
    @PreAuthorize("hasAnyRole('ADMINISTRADOR','SUPERADMIN')")
    @Operation(summary = "Cancelar la suscripción actual")
    @ApiResponse(responseCode = "200", description = "Suscripción cancelada")
    public ResponseEntity<Void> cancelar() {
        gestionarSuscripcionUseCase.cancelar(TenantContext.getRestauranteId());
        return ResponseEntity.ok().build();
    }

    private RestauranteResponse toResponse(Restaurante r) {
        return new RestauranteResponse(r.getRestaurante_id(), r.getSlug(), r.getNombre(),
                r.getDireccion(), r.getEstado());
    }

    private SuscripcionResponse toResponse(SuscripcionConPlan sc) {
        Suscripcion s = sc.suscripcion();
        return new SuscripcionResponse(s.getSuscripcion_id(), s.getRestaurante_id(), s.getPlan_id(),
                sc.nombrePlan(), s.getMonto(), s.getMoneda(), s.getEstado(),
                s.getFecha_inicio(), s.getFecha_fin());
    }

    private RestauranteDashboardResponse toDashboardResponse(RestauranteConSuscripcion r) {
        SuscripcionDashboardResponse s = r.suscripcionId() == null ? null
                : new SuscripcionDashboardResponse(r.suscripcionId(), r.planId(), r.nombrePlan(),
                        r.estadoSuscripcion(), r.fechaInicio(), r.fechaFin());
        return new RestauranteDashboardResponse(r.restauranteId(), r.slug(), r.nombre(),
                r.direccion(), r.estado(), s);
    }
}
