package com.servidos.v1.identity.application.rol;

import com.servidos.v1.identity.domain.RolNombrePlataforma;
import com.servidos.v1.identity.infrastructure.RolPlataformaJpaRepository;
import com.servidos.v1.identity.infrastructure.UsuarioPlataformaJpaRepository;
import com.servidos.v1.shared.exception.BusinessException;
import com.servidos.v1.shared.exception.ForbiddenException;
import com.servidos.v1.shared.exception.UnauthorizedException;
import com.servidos.v1.shared.security.CurrentUser;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class AsignarRolPlataformaUseCase {

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
        if (!RolNombrePlataforma.esValido(rolEntity.getNombre())) {
            throw new BusinessException("Rol no reconocido");
        }
        exigirJerarquia(rolEntity.getNombre(), objetivo.getRolPlataformaId());

        objetivo.setRolPlataformaId(cmd.nuevoRolId());
        usuarioPlataformaRepository.save(objetivo);
    }

    /**
     * Solo SUPERADMIN gestiona usuarios de plataforma (ADMIN es un socio con las mismas
     * operaciones sobre restaurantes, pero no nombra a otros). Nunca se otorga SUPERADMIN
     * ni se le cambia el rol a uno.
     */
    private void exigirJerarquia(String nombreRolObjetivo, Long rolActualObjetivoId) {
        String rolActor = CurrentUser.getRole();
        if (rolActor == null) {
            throw new UnauthorizedException("Sesión inválida");
        }
        if (!RolNombrePlataforma.esSuperadmin(rolActor)) {
            throw new ForbiddenException("Solo un SUPERADMIN puede asignar roles de plataforma");
        }
        if (!RolNombrePlataforma.esOtorgable(nombreRolObjetivo)) {
            throw new ForbiddenException("No se puede otorgar el rol SUPERADMIN");
        }
        boolean objetivoEsSuperadmin = rolRepository.findById(rolActualObjetivoId)
                .map(r -> RolNombrePlataforma.esSuperadmin(r.getNombre()))
                .orElse(false);
        if (objetivoEsSuperadmin) {
            throw new ForbiddenException("No se puede cambiar el rol de un SUPERADMIN");
        }
    }
}
