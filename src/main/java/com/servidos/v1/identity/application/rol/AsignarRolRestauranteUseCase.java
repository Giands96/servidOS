package com.servidos.v1.identity.application.rol;

import com.servidos.v1.identity.infrastructure.RolRestauranteJpaRepository;
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
public class AsignarRolRestauranteUseCase {

    private final UsuarioRestauranteJpaRepository usuarioRestauranteRepository;
    private final RolRestauranteJpaRepository rolRepository;

    @Transactional
    public void ejecutar(AsignarRolRestauranteCommand cmd, Long restauranteId) {
        if (cmd == null) {
            throw new BusinessException("El comando es obligatorio");
        }
        if (cmd.usuarioId() == null) {
            throw new BusinessException("El usuario es obligatorio");
        }
        if (cmd.nuevoRolId() == null) {
            throw new BusinessException("El rol es obligatorio");
        }
        if (restauranteId == null) {
            throw new BusinessException("El restaurante_id es obligatorio");
        }
        Long actorId = CurrentUser.getCurrentUser();
        if (actorId == null) {
            throw new UnauthorizedException("Sesión inválida");
        }

        var actorMembresia = usuarioRestauranteRepository.findById(actorId);
        if (actorMembresia.isEmpty()
                || !restauranteId.equals(actorMembresia.get().getRestauranteId())) {
            throw new ForbiddenException("Operación cross-tenant rechazada");
        }
        if (!"ADMINISTRADOR".equalsIgnoreCase(CurrentUser.getRole())) {
            throw new ForbiddenException("Solo un ADMINISTRADOR puede reasignar roles");
        }

        var objetivo = usuarioRestauranteRepository.findById(cmd.usuarioId())
                .orElseThrow(() -> new BusinessException("El usuario no pertenece a este restaurante"));
        if (!restauranteId.equals(objetivo.getRestauranteId())) {
            throw new ForbiddenException("Operación cross-tenant rechazada");
        }

        var rolEntity = rolRepository.findById(cmd.nuevoRolId())
                .orElseThrow(() -> new BusinessException("El rol no existe"));
        if (!"ACTIVO".equalsIgnoreCase(rolEntity.getEstado())) {
            throw new BusinessException("El rol no está activo");
        }
        if ("ADMINISTRADOR".equalsIgnoreCase(rolEntity.getNombre())) {
            throw new ForbiddenException("No puedes otorgar un rol igual o superior al tuyo");
        }

        objetivo.setRolRestauranteId(cmd.nuevoRolId());
        usuarioRestauranteRepository.save(objetivo);
    }
}
