package com.servidos.v1.catalog.api;

import com.servidos.v1.catalog.api.dto.CategoriaResponse;
import com.servidos.v1.catalog.api.dto.CrearCategoriaRequest;
import com.servidos.v1.catalog.application.categoria.GestionarCategoriaCommand;
import com.servidos.v1.catalog.application.categoria.GestionarCategoriaUseCase;
import com.servidos.v1.catalog.application.categoria.ListarCategoriasUseCase;
import com.servidos.v1.catalog.domain.Categoria;
import com.servidos.v1.shared.exception.ErrorResponse;
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
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/categorias")
@RequiredArgsConstructor
@Tag(name = "Categorías", description = "Categorías del restaurante actual")
@SecurityRequirement(name = "bearerAuth")
public class CategoriaController {

    private final GestionarCategoriaUseCase gestionarCategoriaUseCase;
    private final ListarCategoriasUseCase listarCategoriasUseCase;

    @PostMapping
    @PreAuthorize("hasRole('ADMINISTRADOR')")
    @Operation(summary = "Crear categoría en el restaurante actual",
            description = "Nombre único por tenant.")
    @ApiResponses({
            @ApiResponse(responseCode = "201", description = "Categoría creada",
                    content = @Content(schema = @Schema(implementation = CategoriaResponse.class))),
            @ApiResponse(responseCode = "400", description = "Nombre duplicado o datos inválidos",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "401", description = "Sin sesión",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "403", description = "Sin permiso",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "422", description = "Validación Jakarta (@Valid)",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class)))})
    public ResponseEntity<CategoriaResponse> crear(@Valid @RequestBody CrearCategoriaRequest request) {
        var creada = gestionarCategoriaUseCase.ejecutar(
                new GestionarCategoriaCommand(request.nombre(), request.estado()),
                TenantContext.getRestauranteId());
        return ResponseEntity.status(HttpStatus.CREATED).body(toResponse(creada));
    }

    @GetMapping
    @PreAuthorize("isAuthenticated()")
    @Operation(summary = "Listar categorías del restaurante actual")
    @ApiResponse(responseCode = "200", description = "Página del tenant")
    public ResponseEntity<Page<CategoriaResponse>> listar(Pageable pageable) {
        var page = listarCategoriasUseCase.listar(TenantContext.getRestauranteId(), pageable);
        return ResponseEntity.ok(page.map(this::toResponse));
    }

    private CategoriaResponse toResponse(Categoria c) {
        return new CategoriaResponse(c.getCategoria_id(), c.getNombre(), c.getEstado());
    }
}
