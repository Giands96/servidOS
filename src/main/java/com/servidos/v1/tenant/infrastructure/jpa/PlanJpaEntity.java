package com.servidos.v1.tenant.infrastructure.jpa;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Entity @Table(name = "plan")
@Data @NoArgsConstructor @AllArgsConstructor
public class PlanJpaEntity {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) @Column(name = "plan_id") private Long planId;
    @Column(name = "nombre_plan", nullable = false) private String nombrePlan;
    @Column(name = "estado", nullable = false, length = 20) private String estado;
}
