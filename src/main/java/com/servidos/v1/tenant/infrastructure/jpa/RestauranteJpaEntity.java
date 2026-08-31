package com.servidos.v1.tenant.infrastructure.jpa;

import com.servidos.v1.tenant.domain.EstadoRestaurante;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import java.time.LocalDateTime;

@Entity @Table(name = "restaurante", uniqueConstraints = @UniqueConstraint(columnNames = "slug"))
@Data @NoArgsConstructor @AllArgsConstructor
public class RestauranteJpaEntity {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) @Column(name = "restaurante_id") private Long restauranteId;
    @Column(name = "slug", nullable = false, unique = true, length = 100) private String slug;
    @Column(name = "nombre", nullable = false, length = 150) private String nombre;
    @Column(name = "direccion", length = 255) private String direccion;
    @Enumerated(EnumType.STRING) @Column(name = "estado", nullable = false, length = 20) private EstadoRestaurante estado;
    @Column(name = "created_at", nullable = false, updatable = false) private LocalDateTime createdAt;
    @Column(name = "updated_at", nullable = false) private LocalDateTime updatedAt;
    @PrePersist protected void onCreate() { LocalDateTime now = LocalDateTime.now(); this.createdAt = now; this.updatedAt = now; }
    @PreUpdate protected void onUpdate() { this.updatedAt = LocalDateTime.now(); }
}
