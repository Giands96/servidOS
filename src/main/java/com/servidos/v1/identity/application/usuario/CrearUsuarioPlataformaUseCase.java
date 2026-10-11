package com.servidos.v1.identity.application.usuario;

import com.servidos.v1.identity.domain.EstadoUsuario;
import com.servidos.v1.identity.domain.RolNombrePlataforma;
import com.servidos.v1.identity.domain.Usuario;
import com.servidos.v1.identity.infrastructure.RolPlataformaJpaRepository;
import com.servidos.v1.identity.infrastructure.UsuarioJpaEntity;
import com.servidos.v1.identity.infrastructure.UsuarioJpaRepository;
import com.servidos.v1.identity.infrastructure.UsuarioPlataformaJpaEntity;
import com.servidos.v1.identity.infrastructure.UsuarioPlataformaJpaRepository;
import com.servidos.v1.identity.infrastructure.mapper.UsuarioMapper;
import com.servidos.v1.shared.exception.BusinessException;
import com.servidos.v1.shared.exception.ConflictException;
import com.servidos.v1.shared.exception.ForbiddenException;
import com.servidos.v1.shared.exception.UnauthorizedException;
import com.servidos.v1.shared.security.CurrentUser;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Alta de un usuario de plataforma. SUPERADMIN crea ADMIN o MODERADOR; ADMIN (socio)
 * solo MODERADOR. Nadie crea un SUPERADMIN.
 */
@Service
@RequiredArgsConstructor
public class CrearUsuarioPlataformaUseCase {

    private final UsuarioJpaRepository usuarioRepository;
    private final UsuarioPlataformaJpaRepository usuarioPlataformaRepository;
    private final RolPlataformaJpaRepository rolRepository;
    private final UsuarioMapper usuarioMapper;
    private final PasswordEncoder passwordEncoder;

    @Transactional
    public Usuario ejecutar(CrearUsuarioPlataformaCommand cmd) {
        validar(cmd);
        Long actorId = CurrentUser.getCurrentUser();
        if (actorId == null || CurrentUser.getRole() == null) {
            throw new UnauthorizedException("Sesión inválida");
        }
        if (usuarioPlataformaRepository.findById(actorId).isEmpty()) {
            throw new ForbiddenException("Operación no disponible para sesiones de restaurante");
        }

        var rol = rolRepository.findById(cmd.rolPlataformaId())
                .orElseThrow(() -> new BusinessException("El rol no existe"));
        if (!"ACTIVO".equalsIgnoreCase(rol.getEstado())) {
            throw new BusinessException("El rol no está activo");
        }
        if (!RolNombrePlataforma.puedeGestionar(CurrentUser.getRole(), rol.getNombre())) {
            throw new ForbiddenException("No puedes crear un usuario con un rol igual o superior al tuyo");
        }

        String email = cmd.email().trim().toLowerCase();
        if (usuarioRepository.existsByEmail(email)) {
            throw new ConflictException("El email ya existe");
        }
        if (cmd.password().length() < 8 || cmd.password().length() > 72) {
            throw new BusinessException("El password debe tener entre 8 y 72 caracteres");
        }

        Usuario domain = Usuario.crear(cmd.nombre(), cmd.apellido(), email, passwordEncoder.encode(cmd.password()));
        final UsuarioJpaEntity guardado;
        try {
            guardado = usuarioRepository.saveAndFlush(usuarioMapper.toEntity(domain));
        } catch (DataIntegrityViolationException e) {
            throw new ConflictException("El email ya existe");
        }

        var membresia = new UsuarioPlataformaJpaEntity();
        membresia.setUsuarioId(guardado.getUsuarioId());
        membresia.setRolPlataformaId(rol.getRolPlataformaId());
        membresia.setEstado(EstadoUsuario.ACTIVO);
        usuarioPlataformaRepository.save(membresia);

        Usuario creado = usuarioMapper.toDomain(guardado);
        creado.setPassword_hash(null);
        return creado;
    }

    private void validar(CrearUsuarioPlataformaCommand cmd) {
        if (cmd == null) throw new BusinessException("El comando es obligatorio");
        if (cmd.nombre() == null || cmd.nombre().isBlank()) throw new BusinessException("El nombre es obligatorio");
        if (cmd.apellido() == null || cmd.apellido().isBlank()) throw new BusinessException("El apellido es obligatorio");
        if (cmd.email() == null || cmd.email().isBlank()) throw new BusinessException("El email es obligatorio");
        if (cmd.password() == null || cmd.password().isEmpty()) throw new BusinessException("El password es obligatorio");
        if (cmd.rolPlataformaId() == null) throw new BusinessException("El rol es obligatorio");
    }
}
