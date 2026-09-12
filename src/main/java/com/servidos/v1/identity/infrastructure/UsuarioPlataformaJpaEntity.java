package com.servidos.v1.identity.infrastructure;

import com.servidos.v1.identity.domain.EstadoUsuario;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Entity
@Table(name = "usuario_plataforma")
@Data
@NoArgsConstructor
@AllArgsConstructor
public class UsuarioPlataformaJpaEntity {

    @Id
    @Column(name = "usuario_id")
    private Long usuarioId;

    @Column(name = "rol_plataforma_id", nullable = false)
    private Long rolPlataformaId;

    @Enumerated(EnumType.STRING)
    @Column(name = "estado", nullable = false, length = 20)
    private EstadoUsuario estado;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    @PrePersist
    protected void onCreate() {
        LocalDateTime now = LocalDateTime.now();
        this.createdAt = now;
        this.updatedAt = now;
    }

    @PreUpdate
    protected void onUpdate() {
        this.updatedAt = LocalDateTime.now();
    }
}
