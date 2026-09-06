package com.servidos.v1.identity.application;

import com.servidos.v1.identity.domain.Usuario;
import com.servidos.v1.identity.domain.UsuarioRestaurante;
import com.servidos.v1.identity.domain.event.UsuarioCreadoEvent;
import com.servidos.v1.identity.infrastructure.RolRestauranteJpaRepository;
import com.servidos.v1.identity.infrastructure.UsuarioJpaRepository;
import com.servidos.v1.identity.infrastructure.UsuarioRestauranteJpaEntity;
import com.servidos.v1.identity.infrastructure.UsuarioRestauranteJpaRepository;
import com.servidos.v1.identity.infrastructure.mapper.UsuarioMapper;
import com.servidos.v1.shared.event.EventPublisher;
import com.servidos.v1.shared.exception.BusinessException;
import com.servidos.v1.tenant.infrastructure.jpa.RestauranteJpaRepository;
import lombok.RequiredArgsConstructor;
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

    public record Command(String nombre, String apellido, String email, String password, Long rolRestauranteId) {}

    @Transactional
    public Usuario ejecutar(Command cmd, Long restauranteId) {
        validar(cmd, restauranteId);

        String emailLower = cmd.email().trim().toLowerCase();

        if (usuarioRepository.existsByEmail(emailLower)) {
            throw new BusinessException("El email ya existe");
        }

        if (!restauranteRepository.existsById(restauranteId)) {
            throw new BusinessException("El restaurante no existe");
        }

        if (!rolRepository.existsById(cmd.rolRestauranteId())) {
            throw new BusinessException("El rol no existe");
        }

        String hash = passwordEncoder.encode(cmd.password());

        Usuario domain = Usuario.crear(cmd.nombre(), cmd.apellido(), emailLower, hash);
        var savedEntity = usuarioRepository.saveAndFlush(usuarioMapper.toEntity(domain));
        Usuario saved = usuarioMapper.toDomain(savedEntity);

        UsuarioRestaurante ur = UsuarioRestaurante.crear(saved.getUsuario_id(), restauranteId, cmd.rolRestauranteId());
        usuarioRestauranteRepository.save(mapToEntity(ur));

        eventPublisher.publish(new UsuarioCreadoEvent(saved.getUsuario_id(), restauranteId, cmd.rolRestauranteId(), emailLower, new Date()));

        return saved;
    }

    private void validar(Command cmd, Long restauranteId) {
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
