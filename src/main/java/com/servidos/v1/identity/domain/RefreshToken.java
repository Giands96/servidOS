package com.servidos.v1.identity.domain;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.UUID;

@Data
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class RefreshToken {

    private Long id;
    private Long usuarioId;
    private Long restauranteId;
    private UUID familiaId;
    private String hashToken;
    private LocalDateTime createdAt;
    private LocalDateTime expiresAt;
    private LocalDateTime revokedAt;
    private Long replacedBy;


    public static RefreshToken create(Long usuarioId,
                                      Long restauranteId,
                                      UUID familiaId,
                                      String hashToken,
                                      LocalDateTime expiresAt) {
        return new RefreshToken(null,
                usuarioId,
                restauranteId,
                familiaId,
                hashToken,
                LocalDateTime.now(),
                expiresAt,
                null,
                null);
    }

    public void revoke(Long replacedBy) {
        this.revokedAt = LocalDateTime.now();
        this.replacedBy = replacedBy;
    }

    public boolean estaExpirado(LocalDateTime now) {
        return !expiresAt.isAfter(now);
    }

    public boolean estaRevocado() {
        return revokedAt != null;
    }

    public boolean fueRotado() {
        return replacedBy != null;
    }

}

