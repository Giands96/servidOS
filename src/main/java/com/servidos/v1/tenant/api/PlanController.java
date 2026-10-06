package com.servidos.v1.tenant.api;

import com.servidos.v1.tenant.api.dto.PlanResponse;
import com.servidos.v1.tenant.application.plan.ListarPlanesUseCase;
import com.servidos.v1.tenant.domain.Plan;
import com.servidos.v1.shared.exception.ErrorResponse;
import io.swagger.v3.oas.annotations.Operation;
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
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/v1/planes")
@RequiredArgsConstructor
@Tag(name = "Planes", description = "Catálogo de planes")
@SecurityRequirement(name = "bearerAuth")
public class PlanController {

    private final ListarPlanesUseCase listarPlanesUseCase;

    @GetMapping
    @PreAuthorize("isAuthenticated()")
    @Operation(summary = "Listar los planes disponibles",
            description = "Solo planes ACTIVO, ordenados por precio ascendente. Lo necesitan tanto la "
                    + "plataforma (alta de restaurante, que exige un planId) como el tenant (cambio de "
                    + "plan, que exige un nuevoPlanId): antes ambos exigían un id que la API no permitía descubrir.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Catálogo de planes",
                    content = @Content(schema = @Schema(implementation = PlanResponse.class))),
            @ApiResponse(responseCode = "401", description = "Sin sesión",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class)))})
    public ResponseEntity<List<PlanResponse>> listar() {
        return ResponseEntity.ok(listarPlanesUseCase.listar().stream().map(PlanController::toResponse).toList());
    }

    private static PlanResponse toResponse(Plan p) {
        return new PlanResponse(p.getPlan_id(), p.getNombre_plan(), p.getPrecio_plan(),
                p.getDescripcion(), p.getEstado().name(), Plan.MONEDA_PEN);
    }
}