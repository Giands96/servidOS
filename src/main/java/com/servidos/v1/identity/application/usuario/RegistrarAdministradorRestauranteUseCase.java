package com.servidos.v1.identity.application.usuario;

import com.servidos.v1.identity.domain.RolNombreRestaurante;
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
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.util.Date;

/**
 * Alta del dueño (ADMINISTRADOR) de un restaurante recién creado desde la plataforma.
 * A diferencia de {@link CrearUsuarioUseCase} no mira CurrentUser ni el tenant: el que llama es
 * el endpoint de plataforma, ya protegido por rol. Corre siempre dentro de la transacción de
 * creación del restaurante (MANDATORY) para que un fallo aquí deshaga también el restaurante.
 */
@Service
@RequiredArgsConstructor
@Transactional(propagation = Propagation.MANDATORY)
public class RegistrarAdministradorRestauranteUseCase {

    private final UsuarioJpaRepository usuarioRepository;
    private final UsuarioRestauranteJpaRepository usuarioRestauranteRepository;
    private final RolRestauranteJpaRepository rolRepository;
    private final UsuarioMapper usuarioMapper;
    private final PasswordEncoder passwordEncoder;
    private final EventPublisher eventPublisher;

    /** Chequeo temprano (antes de persistir el restaurante) para fallar barato con 409. */
    public void validarEmailDisponible(String email) {
        if (usuarioRepository.existsByEmail(normalizar(email))) {
            throw new ConflictException("El email ya existe");
        }
    }

    public Usuario ejecutar(RegistrarAdministradorRestauranteCommand cmd, Long restauranteId) {
        validar(cmd, restauranteId);

        String emailLower = normalizar(cmd.email());
        validarEmailDisponible(emailLower);

        var rol = rolRepository.findByNombre(RolNombreRestaurante.ADMINISTRADOR.name())
                .filter(r -> "ACTIVO".equalsIgnoreCase(r.getEstado()))
                .orElseThrow(() -> new BusinessException("El rol ADMINISTRADOR no está disponible"));

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

        UsuarioRestaurante ur = UsuarioRestaurante.crear(saved.getUsuario_id(), restauranteId, rol.getRolRestauranteId());
        usuarioRestauranteRepository.save(mapToEntity(ur));

        eventPublisher.publish(new UsuarioCreadoEvent(saved.getUsuario_id(), restauranteId,
                rol.getRolRestauranteId(), emailLower, new Date()));

        saved.setPassword_hash(null);
        return saved;
    }

    private static String normalizar(String email) {
        return email == null ? null : email.trim().toLowerCase();
    }

    private void validar(RegistrarAdministradorRestauranteCommand cmd, Long restauranteId) {
        if (cmd == null) {
            throw new BusinessException("El administrador es obligatorio");
        }
        if (cmd.nombre() == null || cmd.nombre().trim().isEmpty()) {
            throw new BusinessException("El nombre del administrador es obligatorio");
        }
        if (cmd.apellido() == null || cmd.apellido().trim().isEmpty()) {
            throw new BusinessException("El apellido del administrador es obligatorio");
        }
        if (cmd.email() == null || cmd.email().trim().isEmpty()) {
            throw new BusinessException("El email del administrador es obligatorio");
        }
        if (cmd.password() == null || cmd.password().isEmpty()) {
            throw new BusinessException("El password del administrador es obligatorio");
        }
        if (cmd.password().length() < 8 || cmd.password().length() > 72) {
            throw new BusinessException("El password debe tener entre 8 y 72 caracteres");
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
