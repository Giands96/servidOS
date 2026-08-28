package com.servidos.v1.tenant.domain;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class Plan {

    private Long plan_id;
    private String nombre_plan;
    private BigDecimal precio_plan;
    private String descripcion;
    private EstadoPlan estado;

    enum EstadoPlan {
        ACTIVO,
        INACTIVO
    }
}

