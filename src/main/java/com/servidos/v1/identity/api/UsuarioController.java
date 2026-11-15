package com.servidos.v1.identity.api;

import com.servidos.v1.identity.api.dto.RegistrarUsuarioPlataformaRequest;
import com.servidos.v1.identity.api.dto.UsuarioPlataformaResponse;
import com.servidos.v1.identity.application.usuario.CrearUsuarioPlataformaCommand;
import com.servidos.v1.identity.application.usuario.CrearUsuarioPlataformaUseCase;
import com.servidos.v1.identity.api.dto.CambiarRolRequest;
import com.servidos.v1.identity.api.dto.ActualizarUsuarioRequest;
import com.servidos.v1.identity.api.dto.RegistrarUsuarioRequest;
import com.servidos.v1.identity.api.dto.UsuarioListadoResponse;
import com.servidos.v1.identity.api.dto.UsuarioResponse;
import com.servidos.v1.identity.application.rol.AsignarRolPlataformaCommand;
import com.servidos.v1.identity.application.rol.AsignarRolPlataformaUseCase;
import com.servidos.v1.identity.application.rol.AsignarRolRestauranteCommand;
import com.servidos.v1.identity.application.rol.AsignarRolRestauranteUseCase;
import com.servidos.v1.identity.application.usuario.CrearUsuarioCommand;
import com.servidos.v1.identity.application.usuario.CrearUsuarioUseCase;
import com.servidos.v1.identity.application.usuario.ActualizarUsuarioCommand;
import com.servidos.v1.identity.application.usuario.ActualizarUsuarioUseCase;
import com.servidos.v1.identity.application.usuario.EliminarUsuarioCommand;
import com.servidos.v1.identity.application.usuario.EliminarUsuarioUseCase;
import com.servidos.v1.identity.application.usuario.ObtenerUsuariosCommand;
import com.servidos.v1.identity.application.usuario.ObtenerUsuariosUseCase;
import com.servidos.v1.identity.application.usuario.UsuarioListItem;
import com.servidos.v1.identity.domain.EstadoUsuario;
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
import org.springframework.data.domain.Page;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
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
    private final CrearUsuarioPlataformaUseCase crearUsuarioPlataformaUseCase;
    private final ObtenerUsuariosUseCase obtenerUsuariosUseCase;
    private final ActualizarUsuarioUseCase actualizarUsuarioUseCase;

    @GetMapping
    @PreAuthorize("hasRole('ADMINISTRADOR')")
    @Operation(summary = "Listar usuarios del restaurante actual",
            description = "Solo ADMINISTRADOR del tenant. Filtros opcionales: `nombre`, `apellido`, `email`, "
                    + "`rolRestauranteId`, `rolNombre` (búsqueda parcial, sin distinguir mayúsculas) y `estado`. "
                    + "Paginado (`?page=&size=`, default `size=20`, máximo 100). El restauranteId se toma del JWT, "
                    + "nunca de un parámetro.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Página de usuarios del tenant"),
            @ApiResponse(responseCode = "400", description = "Página o tamaño inválidos, o sesión de plataforma",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "401", description = "Sin sesión",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "403", description = "Sin permiso o cross-tenant",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class)))})
    public ResponseEntity<Page<UsuarioListadoResponse>> listar(
            @Parameter(description = "Búsqueda parcial por nombre") @RequestParam(required = false) String nombre,
            @Parameter(description = "Búsqueda parcial por apellido") @RequestParam(required = false) String apellido,
            @Parameter(description = "Búsqueda parcial por email") @RequestParam(required = false) String email,
            @Parameter(description = "Solo usuarios con este rol") @RequestParam(required = false) Long rolRestauranteId,
            @Parameter(description = "Búsqueda parcial por nombre del rol", example = "MESE")
            @RequestParam(required = false) String rolNombre,
            @Parameter(description = "Solo usuarios en este estado") @RequestParam(required = false) EstadoUsuario estado,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        var pagina = obtenerUsuariosUseCase.ejecutar(
                new ObtenerUsuariosCommand(nombre, apellido, email, rolRestauranteId, rolNombre, estado),
                TenantContext.getRestauranteId(), page, size);
        return ResponseEntity.ok(pagina.map(this::toResponse));
    }

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

    @PostMapping("/plataforma")
    @PreAuthorize("hasAnyRole('SUPERADMIN','ADMIN')")
    @Operation(summary = "Registrar usuario de plataforma (ADMIN o MODERADOR)",
            description = "SUPERADMIN crea ADMIN o MODERADOR; ADMIN solo MODERADOR. No se puede crear un SUPERADMIN.")
    @ApiResponses({
            @ApiResponse(responseCode = "201", description = "Usuario creado",
                    content = @Content(schema = @Schema(implementation = UsuarioPlataformaResponse.class))),
            @ApiResponse(responseCode = "400", description = "Datos inválidos o rol inexistente/inactivo",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "403", description = "Rol igual o superior al del actor",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "409", description = "El email ya existe",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "422", description = "Validación Jakarta (@Valid)",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class)))})
    public ResponseEntity<UsuarioPlataformaResponse> registrarPlataforma(
            @Valid @RequestBody RegistrarUsuarioPlataformaRequest request) {
        var creado = crearUsuarioPlataformaUseCase.ejecutar(new CrearUsuarioPlataformaCommand(
                request.nombre(), request.apellido(), request.email(), request.password(), request.rolPlataformaId()));
        return ResponseEntity.status(HttpStatus.CREATED).body(new UsuarioPlataformaResponse(
                creado.getUsuario_id(), creado.getEmail(), creado.getNombre(), request.rolPlataformaId()));
    }

    @PatchMapping("/{id}/rol")
    // En plataforma cada rol gestiona solo roles menores al suyo; MODERADOR no gestiona a nadie.
    @PreAuthorize("hasAnyRole('ADMINISTRADOR','SUPERADMIN','ADMIN')")
    @Operation(summary = "Cambiar rol de un usuario",
            description = "Con sesión de restaurante delega a AsignarRolRestauranteUseCase "
                    + "(solo ADMINISTRADOR, nunca otorga ADMINISTRADOR, cross-tenant 403). "
                    + "Con sesión de plataforma delega a AsignarRolPlataformaUseCase: SUPERADMIN otorga ADMIN o MODERADOR; ADMIN solo MODERADOR.")
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

    @PatchMapping("/{id}")
    @PreAuthorize("hasRole('ADMINISTRADOR')")
    @Operation(summary = "Corregir nombre y apellido de un usuario del restaurante actual",
            description = "Solo ADMINISTRADOR del tenant. Solo datos personales: email y contraseña no son "
                    + "editables, y el rol tiene su propio endpoint (`PATCH /{id}/rol`). El restauranteId se "
                    + "toma del JWT y el objetivo se busca con el tenant en la query.")
    @ApiResponses({
            @ApiResponse(responseCode = "204", description = "Datos actualizados"),
            @ApiResponse(responseCode = "400", description = "Datos inválidos, usuario inexistente o de otro tenant, "
                    + "o sesión de plataforma",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "401", description = "Sin sesión",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "403", description = "Sin permiso o cross-tenant",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "422", description = "Validación Jakarta (@Valid)",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class)))})
    public ResponseEntity<Void> actualizar(
            @Parameter(description = "ID del usuario objetivo", example = "2") @PathVariable Long id,
            @Valid @RequestBody ActualizarUsuarioRequest request) {
        actualizarUsuarioUseCase.ejecutar(
                new ActualizarUsuarioCommand(id, request.nombre(), request.apellido()),
                TenantContext.getRestauranteId());
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

    private UsuarioListadoResponse toResponse(UsuarioListItem item) {
        return new UsuarioListadoResponse(
                item.usuarioId(), item.nombre(), item.apellido(), item.email(),
                item.rolRestauranteId(), item.rolNombre(), item.estado(), item.ultimoAcceso());
    }
}
