package com.servidos.v1.identity.api;

import com.servidos.v1.identity.api.dto.CambiarRolRequest;
import com.servidos.v1.identity.api.dto.RegistrarUsuarioRequest;
import com.servidos.v1.identity.api.dto.UsuarioResponse;
import com.servidos.v1.identity.application.rol.AsignarRolPlataformaCommand;
import com.servidos.v1.identity.application.rol.AsignarRolPlataformaUseCase;
import com.servidos.v1.identity.application.rol.AsignarRolRestauranteCommand;
import com.servidos.v1.identity.application.rol.AsignarRolRestauranteUseCase;
import com.servidos.v1.identity.application.usuario.CrearUsuarioCommand;
import com.servidos.v1.identity.application.usuario.CrearUsuarioUseCase;
import com.servidos.v1.identity.application.usuario.EliminarUsuarioCommand;
import com.servidos.v1.identity.application.usuario.EliminarUsuarioUseCase;
import com.servidos.v1.shared.exception.BusinessException;
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
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/usuarios")
@RequiredArgsConstructor
@Tag(name = "Usuarios", description = "Registro y gestión de roles por tenant")
@SecurityRequirement(name = "bearerAuth")
public class UsuarioController {

    private final CrearUsuarioUseCase crearUsuarioUseCase;
    private final AsignarRolRestauranteUseCase asignarRolRestauranteUseCase;
    private final AsignarRolPlataformaUseCase asignarRolPlataformaUseCase;
    private final EliminarUsuarioUseCase eliminarUsuarioUseCase;

    @PostMapping
    @PreAuthorize("hasRole('ADMINISTRADOR')")
    @Operation(summary = "Registrar usuario en el restaurante actual",
            description = "Solo ADMINISTRADOR del tenant. El restauranteId se toma del JWT, nunca del JSON.")
    @ApiResponses({
            @ApiResponse(responseCode = "201", description = "Usuario creado",
                    content = @Content(schema = @Schema(implementation = UsuarioResponse.class))),
            @ApiResponse(responseCode = "400", description = "Datos inválidos o rol inexistente/inactivo",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "401", description = "Sin sesión",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "403", description = "Sin permiso o sesión de plataforma",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "422", description = "Validación Jakarta (@Valid)",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class)))})
    public ResponseEntity<UsuarioResponse> registrar(@Valid @RequestBody RegistrarUsuarioRequest request) {
        Long restauranteId = TenantContext.getRestauranteId();
        if (restauranteId == null) {
            throw new BusinessException("Operación no disponible para sesiones de plataforma");
        }
        var creado = crearUsuarioUseCase.ejecutar(
                new CrearUsuarioCommand(
                        request.nombre(), request.apellido(), request.email(),
                        request.password(), request.rolRestauranteId()),
                restauranteId);
        return ResponseEntity.status(HttpStatus.CREATED).body(new UsuarioResponse(
                creado.getUsuario_id(), creado.getEmail(), creado.getNombre(),
                restauranteId, request.rolRestauranteId()));
    }

    @PatchMapping("/{id}/rol")
    @PreAuthorize("isAuthenticated()")
    @Operation(summary = "Cambiar rol de un usuario",
            description = "Con sesión de restaurante delega a AsignarRolRestauranteUseCase "
                    + "(solo ADMINISTRADOR, nunca otorga ADMINISTRADOR, cross-tenant 403). "
                    + "Con sesión de plataforma delega a AsignarRolPlataformaUseCase (jerarquía SUPERADMIN > ADMIN > MODERADOR).")
    @ApiResponses({
            @ApiResponse(responseCode = "204", description = "Rol actualizado"),
            @ApiResponse(responseCode = "400", description = "Rol inexistente/inactivo o comando inválido",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "401", description = "Sin sesión",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "403", description = "Sin permiso, cross-tenant o jerarquía insuficiente",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "422", description = "Validación Jakarta (@Valid)",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class)))})
    public ResponseEntity<Void> cambiarRol(
            @Parameter(description = "ID del usuario objetivo", example = "2") @PathVariable Long id,
            @Valid @RequestBody CambiarRolRequest request) {
        Long restauranteId = TenantContext.getRestauranteId();
        if (restauranteId != null) {
            asignarRolRestauranteUseCase.ejecutar(
                    new AsignarRolRestauranteCommand(id, request.nuevoRolId()),
                    restauranteId);
        } else {
            asignarRolPlataformaUseCase.ejecutar(
                    new AsignarRolPlataformaCommand(id, request.nuevoRolId()));
        }
        return ResponseEntity.noContent().build();
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasRole('ADMINISTRADOR')")
    @Operation(summary = "Eliminar usuario del restaurante actual",
            description = "Solo ADMINISTRADOR del tenant. Soft-delete (estado DESHABILITADO, nunca DELETE físico) "
                    + "y revoca sus refresh tokens. El restauranteId se toma del JWT, nunca del JSON.")
    @ApiResponses({
            @ApiResponse(responseCode = "204", description = "Usuario eliminado"),
            @ApiResponse(responseCode = "400", description = "Usuario inexistente, ya eliminado o sesión de plataforma",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "401", description = "Sin sesión",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "403", description = "Sin permiso, cross-tenant o auto-eliminación",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class)))})
    public ResponseEntity<Void> eliminar(
            @Parameter(description = "ID del usuario objetivo", example = "2") @PathVariable Long id) {
        Long restauranteId = TenantContext.getRestauranteId();
        if (restauranteId == null) {
            throw new BusinessException("Operación no disponible para sesiones de plataforma");
        }
        eliminarUsuarioUseCase.ejecutar(new EliminarUsuarioCommand(id), restauranteId);
        return ResponseEntity.noContent().build();
    }
}
