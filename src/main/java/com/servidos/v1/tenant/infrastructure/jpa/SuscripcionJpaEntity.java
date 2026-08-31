package com.servidos.v1.tenant.infrastructure.jpa;

import com.servidos.v1.tenant.domain.Suscripcion.EstadoSuscripcion;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import java.time.LocalDate;
import java.time.LocalDateTime;

@Entity @Table(name = "suscripcion")
@Data @NoArgsConstructor @AllArgsConstructor
public class SuscripcionJpaEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "suscripcion_id")
    private Long suscripcionId;
    @Column(name = "restaurante_id", nullable = false)
    private Long restauranteId;
    @Column(name = "plan_id", nullable = false)
    private Long planId;
    @Enumerated(EnumType.STRING)
    @Column(name = "estado", nullable = false, length = 20)
    private EstadoSuscripcion estado;
    @Column(name = "fecha_inicio", nullable = false)
    private LocalDate fechaInicio;
    @Column(name = "fecha_fin", nullable = false)
    private LocalDate fechaFin;
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;
    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    @PrePersist
    protected void onCreate() {
        LocalDateTime now = LocalDateTime.now();
        this.createdAt = now; this.updatedAt = now;
    }
    @PreUpdate protected void onUpdate() {
        this.updatedAt = LocalDateTime.now();
    }
}
