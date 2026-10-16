package com.servidos.v1.identity.application.usuario;

import com.servidos.v1.identity.domain.EstadoUsuario;
import com.servidos.v1.identity.domain.RolNombreRestaurante;
import com.servidos.v1.identity.domain.Usuario;
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
import org.springframework.transaction.annotation.Transactional;

import java.util.Date;

/**
 * Crea el dueño (ADMINISTRADOR) de un restaurante recién dado de alta por la plataforma.
 * Corre dentro de la transacción del alta: si algo falla, no queda ni restaurante sin
 * dueño ni usuario sin restaurante.
 * <p>
 * Siempre crea un usuario nuevo, nunca vincula uno existente: el login resuelve primero la
 * membresía de restaurante, así que reusar el email de un usuario de plataforma lo haría
 * entrar como tenant.
 */
@Service
@RequiredArgsConstructor
public class CrearAdministradorRestauranteUseCase {

    private final UsuarioJpaRepository usuarioRepository;
    private final UsuarioRestauranteJpaRepository usuarioRestauranteRepository;
    private final RolRestauranteJpaRepository rolRepository;
    private final UsuarioMapper usuarioMapper;
    private final PasswordEncoder passwordEncoder;
    private final EventPublisher eventPublisher;

    /** Valida los datos y que el email esté libre, sin escribir nada. */
    public void validar(CrearAdministradorCommand cmd) {
        if (cmd == null) throw new BusinessException("El administrador es obligatorio");
        if (cmd.nombre() == null || cmd.nombre().isBlank()) throw new BusinessException("El nombre del administrador es obligatorio");
        if (cmd.apellido() == null || cmd.apellido().isBlank()) throw new BusinessException("El apellido del administrador es obligatorio");
        if (cmd.email() == null || cmd.email().isBlank()) throw new BusinessException("El email del administrador es obligatorio");
        if (cmd.password() == null || cmd.password().length() < 8 || cmd.password().length() > 72) {
            throw new BusinessException("El password debe tener entre 8 y 72 caracteres");
        }
        if (usuarioRepository.existsByEmail(normalizar(cmd.email()))) {
            throw new ConflictException("El email ya existe");
        }
    }

    @Transactional
    public Usuario ejecutar(Long restauranteId, CrearAdministradorCommand cmd) {
        if (restauranteId == null) throw new BusinessException("El restaurante es obligatorio");
        validar(cmd);
        var rol = rolRepository.findByNombre(RolNombreRestaurante.ADMINISTRADOR.name())
                .orElseThrow(() -> new IllegalStateException("Falta el rol ADMINISTRADOR (seed V7)"));

        String email = normalizar(cmd.email());
        Usuario domain = Usuario.crear(cmd.nombre(), cmd.apellido().trim(), email, passwordEncoder.encode(cmd.password()));
        final UsuarioJpaEntity guardado;
        try {
            guardado = usuarioRepository.saveAndFlush(usuarioMapper.toEntity(domain));
        } catch (DataIntegrityViolationException e) {
            // Carrera: otro alta con el mismo email entre la validación y el insert.
            throw new ConflictException("El email ya existe");
        }

        var membresia = new UsuarioRestauranteJpaEntity();
        membresia.setUsuarioId(guardado.getUsuarioId());
        membresia.setRestauranteId(restauranteId);
        membresia.setRolRestauranteId(rol.getRolRestauranteId());
        membresia.setEstado(EstadoUsuario.ACTIVO);
        usuarioRestauranteRepository.save(membresia);

        eventPublisher.publish(new UsuarioCreadoEvent(guardado.getUsuarioId(), restauranteId,
                rol.getRolRestauranteId(), email, new Date()));

        Usuario creado = usuarioMapper.toDomain(guardado);
        creado.setPassword_hash(null);
        return creado;
    }

    private static String normalizar(String email) {
        return email.trim().toLowerCase();
    }
}
