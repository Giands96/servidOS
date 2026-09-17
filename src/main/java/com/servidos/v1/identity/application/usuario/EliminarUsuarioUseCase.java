package com.servidos.v1.identity.application.usuario;

import com.servidos.v1.identity.application.auth.RefreshTokenService;
import com.servidos.v1.identity.domain.EstadoUsuario;
import com.servidos.v1.identity.infrastructure.UsuarioJpaRepository;
import com.servidos.v1.identity.infrastructure.UsuarioRestauranteJpaRepository;
import com.servidos.v1.shared.exception.BusinessException;
import com.servidos.v1.shared.exception.ForbiddenException;
import com.servidos.v1.shared.exception.UnauthorizedException;
import com.servidos.v1.shared.security.CurrentUser;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class EliminarUsuarioUseCase {

    private final UsuarioJpaRepository usuarioRepository;
    private final UsuarioRestauranteJpaRepository usuarioRestauranteRepository;
    private final RefreshTokenService refreshTokenService;

    @Transactional
    public void ejecutar(EliminarUsuarioCommand cmd, Long restauranteId) {
        if (cmd == null) {
            throw new BusinessException("El comando es obligatorio");
        }
        if (cmd.usuarioId() == null) {
            throw new BusinessException("El usuario es obligatorio");
        }
        if (restauranteId == null) {
            throw new BusinessException("Operación no disponible para sesiones de plataforma");
        }
        Long actorId = CurrentUser.getCurrentUser();
        if (actorId == null) {
            throw new UnauthorizedException("Sesión inválida");
        }
        if (actorId.equals(cmd.usuarioId())) {
            throw new ForbiddenException("No puedes eliminar tu propio usuario");
        }

        var actorMembresia = usuarioRestauranteRepository.findById(actorId);
        if (actorMembresia.isEmpty()
                || !restauranteId.equals(actorMembresia.get().getRestauranteId())) {
            throw new ForbiddenException("Operación cross-tenant rechazada");
        }
        if (!"ADMINISTRADOR".equalsIgnoreCase(CurrentUser.getRole())) {
            throw new ForbiddenException("Solo un ADMINISTRADOR puede eliminar usuarios");
        }

        var objetivo = usuarioRestauranteRepository.findById(cmd.usuarioId())
                .orElseThrow(() -> new BusinessException("El usuario no pertenece a este restaurante"));
        if (!restauranteId.equals(objetivo.getRestauranteId())) {
            throw new ForbiddenException("Operación cross-tenant rechazada");
        }
        if (objetivo.getEstado() != EstadoUsuario.ACTIVO) {
            throw new BusinessException("El usuario ya fue eliminado");
        }

        var usuarioEntity = usuarioRepository.findById(cmd.usuarioId())
                .orElseThrow(() -> new BusinessException("El usuario no existe"));

        // Soft-delete (Decisión #10): nunca DELETE físico. LoginUseCase y
        // RefreshTokenService exigen estado ACTIVO, así que DESHABILITADO
        // bloquea login y refresh sin tocar esos flujos.
        objetivo.setEstado(EstadoUsuario.DESHABILITADO);
        usuarioEntity.setEstado(EstadoUsuario.DESHABILITADO);
        usuarioRestauranteRepository.save(objetivo);
        usuarioRepository.save(usuarioEntity);

        // Matar sesiones activas: sin esto el access JWT seguiría válido hasta expirar.
        refreshTokenService.revokeAll(cmd.usuarioId());
    }
}
