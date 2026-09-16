package com.servidos.v1.identity.application.rol;

import com.servidos.v1.identity.infrastructure.RolPlataformaJpaRepository;
import com.servidos.v1.identity.infrastructure.UsuarioPlataformaJpaRepository;
import com.servidos.v1.shared.exception.BusinessException;
import com.servidos.v1.shared.exception.ForbiddenException;
import com.servidos.v1.shared.exception.UnauthorizedException;
import com.servidos.v1.shared.security.CurrentUser;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Map;

@Service
@RequiredArgsConstructor
public class AsignarRolPlataformaUseCase {

    private static final Map<String, Integer> RANGO = Map.of(
            "SUPERADMIN", 3,
            "ADMIN", 2,
            "MODERADOR", 1);

    private final UsuarioPlataformaJpaRepository usuarioPlataformaRepository;
    private final RolPlataformaJpaRepository rolRepository;

    @Transactional
    public void ejecutar(AsignarRolPlataformaCommand cmd) {
        if (cmd == null) {
            throw new BusinessException("El comando es obligatorio");
        }
        if (cmd.usuarioId() == null) {
            throw new BusinessException("El usuario es obligatorio");
        }
        if (cmd.nuevoRolId() == null) {
            throw new BusinessException("El rol es obligatorio");
        }
        Long actorId = CurrentUser.getCurrentUser();
        if (actorId == null) {
            throw new UnauthorizedException("Sesión inválida");
        }

        usuarioPlataformaRepository.findById(actorId)
                .orElseThrow(() -> new ForbiddenException("Operación no disponible para sesiones de restaurante"));

        var objetivo = usuarioPlataformaRepository.findById(cmd.usuarioId())
                .orElseThrow(() -> new BusinessException("El usuario no pertenece a la plataforma"));

        var rolEntity = rolRepository.findById(cmd.nuevoRolId())
                .orElseThrow(() -> new BusinessException("El rol no existe"));
        if (!"ACTIVO".equalsIgnoreCase(rolEntity.getEstado())) {
            throw new BusinessException("El rol no está activo");
        }
        exigirJerarquia(rolEntity.getNombre());

        objetivo.setRolPlataformaId(cmd.nuevoRolId());
        usuarioPlataformaRepository.save(objetivo);
    }

    private void exigirJerarquia(String nombreRolObjetivo) {
        String rolActor = CurrentUser.getRole();
        if (rolActor == null) {
            throw new UnauthorizedException("Sesión inválida");
        }
        int rangoActor = RANGO.getOrDefault(rolActor.trim().toUpperCase(), 0);
        int rangoObjetivo = RANGO.getOrDefault(nombreRolObjetivo.trim().toUpperCase(), 0);
        // Nadie otorga un rol igual o superior al suyo.
        if (rangoActor == 0 || rangoObjetivo == 0 || rangoObjetivo >= rangoActor) {
            throw new ForbiddenException("No puedes otorgar un rol igual o superior al tuyo");
        }
    }
}
