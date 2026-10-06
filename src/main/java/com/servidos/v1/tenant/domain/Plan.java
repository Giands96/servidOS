package com.servidos.v1.tenant.domain;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class Plan {

    /**
     * Moneda por defecto del catálogo. Se copia a suscripcion.moneda como snapshot:
     * la suscripción recuerda en qué moneda se cobró aunque el plan cambie después.
     */
    public static final String MONEDA_PEN = "PEN";

    private Long plan_id;
    private String nombre_plan;
    private BigDecimal precio_plan;
    private String descripcion;
    private EstadoPlan estado;

    public enum EstadoPlan {
        ACTIVO,
        INACTIVO
    }
}

