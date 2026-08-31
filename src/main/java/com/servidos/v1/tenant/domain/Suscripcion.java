package com.servidos.v1.tenant.domain;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import java.time.LocalDate;
import java.time.LocalDateTime;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class Suscripcion {

    private Long suscripcion_id;
    private Long restaurante_id;
    private Long plan_id;
    private EstadoSuscripcion estado;
    private LocalDate fecha_inicio;
    private LocalDate fecha_fin;
    private LocalDateTime created_at;
    private LocalDateTime updated_at;

    public enum EstadoSuscripcion {
        ACTIVA,
        CANCELADA
    }
}
