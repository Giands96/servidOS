package com.servidos.v1.identity.application;

import com.servidos.v1.identity.domain.EstadoUsuario;
import com.servidos.v1.identity.infrastructure.JwtService;
import com.servidos.v1.identity.infrastructure.UsuarioJpaRepository;
import com.servidos.v1.identity.infrastructure.UsuarioRestauranteJpaRepository;
import com.servidos.v1.shared.exception.BusinessException;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class LoginUseCase {

    private final UsuarioJpaRepository usuarioRepository;
    private final UsuarioRestauranteJpaRepository usuarioRestauranteRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;

    public record Command(String email, String password) {}

    public String ejecutar(Command cmd) {
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

        var ur = usuarioRestauranteRepository.findById(usuarioEntity.getUsuarioId())
                .orElseThrow(() -> new BusinessException("Credenciales inválidas"));

        if (ur.getEstado() != EstadoUsuario.ACTIVO) {
            throw new BusinessException("Credenciales inválidas");
        }

        return jwtService.generate(usuarioEntity.getUsuarioId(), ur.getRestauranteId(), ur.getRolRestauranteId());
    }
}
