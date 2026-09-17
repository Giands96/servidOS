package com.servidos.v1.catalog.api;

import com.servidos.v1.catalog.api.dto.ActualizarProductoRequest;
import com.servidos.v1.catalog.api.dto.CrearProductoRequest;
import com.servidos.v1.catalog.api.dto.ProductoResponse;
import com.servidos.v1.catalog.application.producto.ActualizarProductoCommand;
import com.servidos.v1.catalog.application.producto.ActualizarProductoUseCase;
import com.servidos.v1.catalog.application.producto.CrearProductoCommand;
import com.servidos.v1.catalog.application.producto.CrearProductoUseCase;
import com.servidos.v1.catalog.application.producto.ListarProductosUseCase;
import com.servidos.v1.catalog.domain.Producto;
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
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
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

@RestController
@RequestMapping("/api/v1/productos")
@RequiredArgsConstructor
@Tag(name = "Productos", description = "Catálogo del restaurante actual")
@SecurityRequirement(name = "bearerAuth")
public class ProductoController {

    private final CrearProductoUseCase crearProductoUseCase;
    private final ActualizarProductoUseCase actualizarProductoUseCase;
    private final ListarProductosUseCase listarProductosUseCase;

    @PostMapping
    @PreAuthorize("hasRole('ADMINISTRADOR')")
    @Operation(summary = "Crear producto en el restaurante actual",
            description = "El restauranteId se toma del JWT, nunca del JSON. Nombre único por tenant.")
    @ApiResponses({
            @ApiResponse(responseCode = "201", description = "Producto creado",
                    content = @Content(schema = @Schema(implementation = ProductoResponse.class))),
            @ApiResponse(responseCode = "400", description = "Datos inválidos o categoría ajena",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "401", description = "Sin sesión",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "403", description = "Sin permiso",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "422", description = "Validación Jakarta (@Valid)",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class)))})
    public ResponseEntity<ProductoResponse> crear(@Valid @RequestBody CrearProductoRequest request) {
        var creado = crearProductoUseCase.ejecutar(
                new CrearProductoCommand(
                        request.nombre(), request.descripcion(), request.categoriaId(),
                        request.precio(), request.tiempoPreparacion(), request.estado(),
                        request.imagenUrl()),
                TenantContext.getRestauranteId());
        return ResponseEntity.status(HttpStatus.CREATED).body(toResponse(creado));
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasRole('ADMINISTRADOR')")
    @Operation(summary = "Actualizar producto del restaurante actual",
            description = "Reemplazo completo. Cross-tenant → 400.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Producto actualizado",
                    content = @Content(schema = @Schema(implementation = ProductoResponse.class))),
            @ApiResponse(responseCode = "400", description = "Inexistente o categoría ajena",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "401", description = "Sin sesión",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "403", description = "Sin permiso",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "422", description = "Validación Jakarta (@Valid)",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class)))})
    public ResponseEntity<ProductoResponse> actualizar(
            @Parameter(description = "ID del producto", example = "1") @PathVariable Long id,
            @Valid @RequestBody ActualizarProductoRequest request) {
        var actualizado = actualizarProductoUseCase.actualizar(
                new ActualizarProductoCommand(
                        id, request.nombre(), request.descripcion(), request.categoriaId(),
                        request.imagenUrl(), request.precio(), request.tiempoPreparacion(),
                        request.estado()),
                TenantContext.getRestauranteId());
        return ResponseEntity.ok(toResponse(actualizado));
    }

    @GetMapping
    @PreAuthorize("isAuthenticated()")
    @Operation(summary = "Listar productos del restaurante actual",
            description = "Paginado (`?page=&size=&sort=`, default `size=20`). Filtro opcional por categoría. El menú lo leen todos los roles (cocina/caja).")
    @ApiResponse(responseCode = "200", description = "Página del tenant")
    public ResponseEntity<Page<ProductoResponse>> listar(
            @Parameter(description = "Filtra por categoría") @RequestParam(required = false) Long categoriaId,
            Pageable pageable) {
        var page = listarProductosUseCase.listar(TenantContext.getRestauranteId(), categoriaId, pageable);
        return ResponseEntity.ok(page.map(this::toResponse));
    }

    @GetMapping("/{id}")
    @PreAuthorize("isAuthenticated()")
    @Operation(summary = "Obtener producto del restaurante actual")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Producto del tenant",
                    content = @Content(schema = @Schema(implementation = ProductoResponse.class))),
            @ApiResponse(responseCode = "400", description = "Inexistente o de otro tenant",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class)))})
    public ResponseEntity<ProductoResponse> obtener(
            @Parameter(description = "ID del producto", example = "1") @PathVariable Long id) {
        return ResponseEntity.ok(toResponse(
                listarProductosUseCase.obtener(TenantContext.getRestauranteId(), id)));
    }

    private ProductoResponse toResponse(Producto p) {
        return new ProductoResponse(
                p.getProducto_id(), p.getCategoria_id(), p.getNombre(), p.getDescripcion(),
                p.getImagen_url(), p.getPrecio(), p.getTiempo_preparacion(), p.getEstado());
    }
}
