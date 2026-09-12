package com.servidos.v1.identity.application;

import com.servidos.v1.identity.infrastructure.jpa.RefreshTokenJpaEntity;
import com.servidos.v1.identity.infrastructure.jpa.RefreshTokenJpaRepository;
import com.servidos.v1.identity.infrastructure.security.AuthProperties;
import com.servidos.v1.identity.infrastructure.RolPlataformaJpaRepository;
import com.servidos.v1.identity.infrastructure.RolRestauranteJpaRepository;
import com.servidos.v1.identity.infrastructure.UsuarioPlataformaJpaRepository;
import com.servidos.v1.identity.infrastructure.UsuarioRestauranteJpaRepository;
import com.servidos.v1.identity.domain.EstadoUsuario;
import com.servidos.v1.shared.exception.BusinessException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.LocalDateTime;
import java.util.Base64;
import java.util.HexFormat;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class RefreshTokenService {

    private final RefreshTokenJpaRepository repository;
    private final UsuarioRestauranteJpaRepository usuarioRestauranteRepository;
    private final UsuarioPlataformaJpaRepository usuarioPlataformaRepository;
    private final RolRestauranteJpaRepository rolRestauranteRepository;
    private final RolPlataformaJpaRepository rolPlataformaRepository;
    private final AuthProperties authProperties;

    public record SesionRenovada(String refreshToken, Long usuarioId, Long restauranteId, String rol) {}

    public String create(Long usuarioId, Long restauranteId) {
        String raw = nuevoTokenCrudo();
        RefreshTokenJpaEntity entity = new RefreshTokenJpaEntity();
        entity.setUsuarioId(usuarioId);
        entity.setRestauranteId(restauranteId);
        entity.setFamiliaId(UUID.randomUUID());
        entity.setHashToken(sha256(raw));
        entity.setExpiresAt(LocalDateTime.now().plus(authProperties.refreshAbsoluteTtl()));
        repository.save(entity);
        return raw;
    }

    @Transactional
    public SesionRenovada rotate(String rawToken) {
        RefreshTokenJpaEntity actual = repository.findByHashToken(sha256(rawToken))
                .orElseThrow(() -> new BusinessException("Refresh inválido"));
        if (actual.getReplacedBy() != null) {
            revocarFamilia(actual.getFamiliaId());
            throw new BusinessException("Sesión revocada por seguridad");
        }
        if (actual.getRevokedAt() != null || !actual.getExpiresAt().isAfter(LocalDateTime.now())) {
            throw new BusinessException("Refresh inválido");
        }
        String rol = rolVigente(actual.getUsuarioId(), actual.getRestauranteId());
        String siguiente = nuevoTokenCrudo();
        RefreshTokenJpaEntity sucesor = new RefreshTokenJpaEntity();
        sucesor.setUsuarioId(actual.getUsuarioId());
        sucesor.setRestauranteId(actual.getRestauranteId());
        sucesor.setFamiliaId(actual.getFamiliaId());
        sucesor.setHashToken(sha256(siguiente));
        sucesor.setExpiresAt(actual.getExpiresAt());
        sucesor = repository.save(sucesor);
        actual.setRevokedAt(LocalDateTime.now());
        actual.setReplacedBy(sucesor.getId());
        repository.save(actual);
        return new SesionRenovada(siguiente, actual.getUsuarioId(), actual.getRestauranteId(), rol);
    }

    private String rolVigente(Long usuarioId, Long restauranteId) {
        if (restauranteId != null) {
            var ur = usuarioRestauranteRepository.findById(usuarioId)
                    .orElseThrow(() -> new BusinessException("Refresh inválido"));
            if (ur.getEstado() != EstadoUsuario.ACTIVO) {
                throw new BusinessException("Refresh inválido");
            }
            var rol = rolRestauranteRepository.findById(ur.getRolRestauranteId())
                    .orElseThrow(() -> new BusinessException("Refresh inválido"));
            if (!"ACTIVO".equalsIgnoreCase(rol.getEstado())) {
                throw new BusinessException("Refresh inválido");
            }
            return rol.getNombre();
        }
        var up = usuarioPlataformaRepository.findById(usuarioId)
                .orElseThrow(() -> new BusinessException("Refresh inválido"));
        if (up.getEstado() != EstadoUsuario.ACTIVO) {
            throw new BusinessException("Refresh inválido");
        }
        var rol = rolPlataformaRepository.findById(up.getRolPlataformaId())
                .orElseThrow(() -> new BusinessException("Refresh inválido"));
        if (!"ACTIVO".equalsIgnoreCase(rol.getEstado())) {
            throw new BusinessException("Refresh inválido");
        }
        return rol.getNombre();
    }

    @Transactional
    public void logout(String rawToken) {
        repository.findByHashToken(sha256(rawToken)).ifPresent(entity ->
                revokeAll(entity.getUsuarioId()));
    }

    @Transactional
    public void revokeAll(Long usuarioId) {
        LocalDateTime now = LocalDateTime.now();
        for (RefreshTokenJpaEntity entity : repository.findByUsuarioId(usuarioId)) {
            if (entity.getRevokedAt() == null) {
                entity.setRevokedAt(now);
                repository.save(entity);
            }
        }
    }

    private void revocarFamilia(UUID familiaId) {
        LocalDateTime now = LocalDateTime.now();
        for (RefreshTokenJpaEntity entity : repository.findByFamiliaId(familiaId)) {
            if (entity.getRevokedAt() == null) {
                entity.setRevokedAt(now);
                repository.save(entity);
            }
        }
    }

    private String nuevoTokenCrudo() {
        byte[] bytes = new byte[32];
        new SecureRandom().nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    static String sha256(String raw) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(raw.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 no disponible", e);
        }
    }
}
