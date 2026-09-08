package com.servidos.v1.identity.application;

import com.servidos.v1.identity.domain.EstadoUsuario;
import com.servidos.v1.identity.infrastructure.security.JwtService;
import com.servidos.v1.identity.infrastructure.RolPlataformaJpaRepository;
import com.servidos.v1.identity.infrastructure.RolRestauranteJpaRepository;
import com.servidos.v1.identity.infrastructure.UsuarioJpaRepository;
import com.servidos.v1.identity.infrastructure.UsuarioPlataformaJpaRepository;
import com.servidos.v1.identity.infrastructure.UsuarioRestauranteJpaRepository;
import com.servidos.v1.shared.exception.BusinessException;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

@Service
@RequiredArgsConstructor
public class LoginUseCase {

    private final UsuarioJpaRepository usuarioRepository;
    private final UsuarioRestauranteJpaRepository usuarioRestauranteRepository;
    private final UsuarioPlataformaJpaRepository usuarioPlataformaRepository;
    private final RolRestauranteJpaRepository rolRestauranteRepository;
    private final RolPlataformaJpaRepository rolPlataformaRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;
    private final RefreshTokenService refreshTokenService;

    public record Command(String email, String password) {}

    public record Session(String accessToken, String refreshToken) {}

    @Transactional
    public Session ejecutar(Command cmd) {
        if (cmd == null || cmd.email() == null || cmd.email().trim().isEmpty()
                || cmd.password() == null || cmd.password().isEmpty()) {
            throw new BusinessException("Credenciales inválidas");
        }

        String emailLower = cmd.email().trim().toLowerCase();

        var usuarioEntity = usuarioRepository.findByEmail(emailLower)
                .orElseThrow(() -> new BusinessException("Credenciales inválidas"));

        if (!passwordEncoder.matches(cmd.password(), usuarioEntity.getPasswordHash())) {
            throw new BusinessException("Credenciales inválidas");
        }

        if (usuarioEntity.getEstado() != EstadoUsuario.ACTIVO) {
            throw new BusinessException("Credenciales inválidas");
        }

        Long restauranteId = null;
        String rol;

        var usuarioRestaurante = usuarioRestauranteRepository.findById(usuarioEntity.getUsuarioId());
        if (usuarioRestaurante.isPresent()) {
            if (usuarioRestaurante.get().getEstado() != EstadoUsuario.ACTIVO) {
                throw new BusinessException("Credenciales inválidas");
            }
            restauranteId = usuarioRestaurante.get().getRestauranteId();
            rol = rolRestauranteRepository.findById(usuarioRestaurante.get().getRolRestauranteId())
                    .orElseThrow(() -> new BusinessException("Credenciales inválidas"))
                    .getNombre();
        } else {
            var usuarioPlataforma = usuarioPlataformaRepository.findById(usuarioEntity.getUsuarioId())
                    .orElseThrow(() -> new BusinessException("Credenciales inválidas"));
            if (usuarioPlataforma.getEstado() != EstadoUsuario.ACTIVO) {
                throw new BusinessException("Credenciales inválidas");
            }
            rol = rolPlataformaRepository.findById(usuarioPlataforma.getRolPlataformaId())
                    .orElseThrow(() -> new BusinessException("Credenciales inválidas"))
                    .getNombre();
        }

        usuarioEntity.setUltimoAcceso(LocalDateTime.now());
        usuarioRepository.save(usuarioEntity);

        String access = jwtService.generate(usuarioEntity.getUsuarioId(), restauranteId, rol);
        String refresh = refreshTokenService.create(usuarioEntity.getUsuarioId(), restauranteId);
        return new Session(access, refresh);
    }
}
