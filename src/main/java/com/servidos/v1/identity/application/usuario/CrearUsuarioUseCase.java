package com.servidos.v1.identity.application.usuario;

import com.servidos.v1.identity.domain.Usuario;
import com.servidos.v1.identity.domain.UsuarioRestaurante;
import com.servidos.v1.identity.domain.event.UsuarioCreadoEvent;
import com.servidos.v1.identity.infrastructure.RolRestauranteJpaRepository;
import com.servidos.v1.identity.infrastructure.UsuarioJpaEntity;
import com.servidos.v1.identity.infrastructure.UsuarioJpaRepository;
import com.servidos.v1.identity.infrastructure.UsuarioRestauranteJpaEntity;
import com.servidos.v1.identity.infrastructure.UsuarioRestauranteJpaRepository;
import com.servidos.v1.identity.infrastructure.mapper.UsuarioMapper;
import com.servidos.v1.shared.event.EventPublisher;
import com.servidos.v1.shared.exception.BusinessException;
import com.servidos.v1.shared.exception.ConflictException;
import com.servidos.v1.shared.exception.ForbiddenException;
import com.servidos.v1.shared.exception.UnauthorizedException;
import com.servidos.v1.shared.security.CurrentUser;
import com.servidos.v1.tenant.infrastructure.jpa.RestauranteJpaRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Date;

@Service
@RequiredArgsConstructor
public class CrearUsuarioUseCase {

    private final UsuarioJpaRepository usuarioRepository;
    private final UsuarioRestauranteJpaRepository usuarioRestauranteRepository;
    private final RestauranteJpaRepository restauranteRepository;
    private final RolRestauranteJpaRepository rolRepository;
    private final UsuarioMapper usuarioMapper;
    private final PasswordEncoder passwordEncoder;
    private final EventPublisher eventPublisher;

    @Transactional
    public Usuario ejecutar(CrearUsuarioCommand cmd, Long restauranteId) {
        validar(cmd, restauranteId);
        exigirUsuarioAutenticado();
        exigirMismoTenant(restauranteId);

        String emailLower = cmd.email().trim().toLowerCase();

        if (usuarioRepository.existsByEmail(emailLower)) {
            throw new ConflictException("El email ya existe");
        }

        if (!restauranteRepository.existsById(restauranteId)) {
            throw new BusinessException("El restaurante no existe");
        }

        var rolEntity = rolRepository.findById(cmd.rolRestauranteId())
                .orElseThrow(() -> new BusinessException("El rol no existe"));
        if (!"ACTIVO".equalsIgnoreCase(rolEntity.getEstado())) {
            throw new BusinessException("El rol no está activo");
        }
        exigirJerarquia(rolEntity.getNombre());

        if (cmd.password().length() < 8 || cmd.password().length() > 72) {
            throw new BusinessException("El password debe tener entre 8 y 72 caracteres");
        }

        String hash = passwordEncoder.encode(cmd.password());

        Usuario domain = Usuario.crear(cmd.nombre(), cmd.apellido(), emailLower, hash);
        final UsuarioJpaEntity savedEntity;
        try {
            savedEntity = usuarioRepository.saveAndFlush(usuarioMapper.toEntity(domain));
        } catch (DataIntegrityViolationException e) {
            // Carrera: dos requests con el mismo email a la vez. El UNIQUE de DB manda → 409.
            throw new ConflictException("El email ya existe");
        }
        Usuario saved = usuarioMapper.toDomain(savedEntity);

        UsuarioRestaurante ur = UsuarioRestaurante.crear(saved.getUsuario_id(), restauranteId, cmd.rolRestauranteId());
        usuarioRestauranteRepository.save(mapToEntity(ur));

        eventPublisher.publish(new UsuarioCreadoEvent(saved.getUsuario_id(), restauranteId, cmd.rolRestauranteId(), emailLower, new Date()));

        // La respuesta nunca lleva hash: el controller arma UsuarioResponse sin este campo,
        // pero lo limpiamos acá también por defensa en profundidad.
        saved.setPassword_hash(null);
        return saved;
    }

    private void exigirUsuarioAutenticado() {
        if (CurrentUser.getCurrentUser() == null) {
            throw new UnauthorizedException("Sesión inválida");
        }
    }

    private void exigirMismoTenant(Long restauranteIdDestino) {
        Long actorId = CurrentUser.getCurrentUser();
        if (actorId == null) {
            throw new UnauthorizedException("Sesión inválida");
        }
        var actorMembresia = usuarioRestauranteRepository.findById(actorId);
        if (actorMembresia.isEmpty()
                || !restauranteIdDestino.equals(actorMembresia.get().getRestauranteId())) {
            throw new ForbiddenException("Operación cross-tenant rechazada");
        }
    }

    private void exigirJerarquia(String nombreRolObjetivo) {
        String rolActor = CurrentUser.getRole();
        if (rolActor == null) {
            throw new UnauthorizedException("Sesión inválida");
        }
        // Nadie otorga un rol igual o superior al suyo. Hoy solo ADMINISTRADOR crea
        // (ver @PreAuthorize en UsuarioController), así que le bloqueamos crear
        // otro ADMINISTRADOR; los roles operativos sí puede.
        if ("ADMINISTRADOR".equalsIgnoreCase(rolActor)
                && "ADMINISTRADOR".equalsIgnoreCase(nombreRolObjetivo)) {
            throw new ForbiddenException("No puedes otorgar un rol igual o superior al tuyo");
        }
    }

    private void validar(CrearUsuarioCommand cmd, Long restauranteId) {
        if (cmd == null) {
            throw new BusinessException("El comando es obligatorio");
        }
        if (cmd.nombre() == null || cmd.nombre().trim().isEmpty()) {
            throw new BusinessException("El nombre es obligatorio");
        }

        // Validar que el apellido no sea nulo o vacío
        if(cmd.apellido() == null || cmd.apellido().trim().isEmpty()) {
            throw new BusinessException("El apellido es obligatorio");
        }

        if (cmd.email() == null || cmd.email().trim().isEmpty()) {
            throw new BusinessException("El email es obligatorio");
        }
        if (cmd.password() == null || cmd.password().isEmpty()) {
            throw new BusinessException("El password es obligatorio");
        }
        if (cmd.rolRestauranteId() == null) {
            throw new BusinessException("El rol es obligatorio");
        }
        if (restauranteId == null) {
            throw new BusinessException("El restaurante_id es obligatorio");
        }
    }

    private UsuarioRestauranteJpaEntity mapToEntity(UsuarioRestaurante domain) {
        var entity = new UsuarioRestauranteJpaEntity();
        entity.setUsuarioId(domain.getUsuario_id());
        entity.setRestauranteId(domain.getRestaurante_id());
        entity.setRolRestauranteId(domain.getRol_restaurante_id());
        entity.setEstado(domain.getEstado());
        return entity;
    }
}
