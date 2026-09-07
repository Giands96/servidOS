package com.servidos.v1.identity.infrastructure.jpa;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface RefreshTokenJpaRepository extends JpaRepository<RefreshTokenJpaEntity, Long> {
    Optional<RefreshTokenJpaEntity> findByHashToken(String token);
    Optional<RefreshTokenJpaEntity> findByUsuarioId(Long usuarioId);
    List<RefreshTokenJpaEntity> findByFamiliaId(UUID familiaId);

}
