package com.servidos.v1.identity.api;

import com.servidos.v1.identity.api.dto.RegistrarUsuarioRequest;
import com.servidos.v1.identity.api.dto.UsuarioResponse;
import com.servidos.v1.identity.application.usuario.CrearUsuarioCommand;
import com.servidos.v1.identity.application.usuario.CrearUsuarioUseCase;
import com.servidos.v1.shared.exception.BusinessException;
import com.servidos.v1.shared.security.TenantContext;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/usuarios")
@RequiredArgsConstructor
public class UsuarioController {

    private final CrearUsuarioUseCase crearUsuarioUseCase;

    @PostMapping
    @PreAuthorize("hasRole('ADMINISTRADOR')")
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
}
