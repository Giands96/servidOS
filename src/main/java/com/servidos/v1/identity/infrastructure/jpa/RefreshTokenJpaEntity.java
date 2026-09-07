package com.servidos.v1.identity.infrastructure.jpa;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name="refresh_token")
@Data
@AllArgsConstructor
@NoArgsConstructor
public class RefreshTokenJpaEntity {

    @Id
    @GeneratedValue(strategy= GenerationType.IDENTITY)
    @Column(name="id", nullable=false, updatable=false)
    private Long id;

    @Column(name="usuario_id", nullable=false)
    private Long usuarioId;

    @Column(name="familia_id", nullable=false)
    private UUID familiaId;

    @Column(name="hash_token", nullable=false, unique=true, length = 64)
    private String hashToken;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "expires_at", nullable = false)
    private LocalDateTime expiresAt;

    @Column(name = "revoked_at")
    private LocalDateTime revokedAt;

    @Column(name = "replaced_by")
    private Long replacedBy;


    @PrePersist
    protected void onCreate() {
        var now = LocalDateTime.now();
        createdAt = now;
    }


}
