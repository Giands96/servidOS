package com.servidos.v1.tenant.domain;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import java.time.LocalDateTime;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class Suscripcion {

    private Long suscripcion_id;
    private Long restaurante_id;
    private Long plan_id;
    private EstadoSuscripcion estado;
    private LocalDateTime fecha_inicio;
    private LocalDateTime fecha_fin;
    private LocalDateTime created_at;
    private LocalDateTime updated_at;
enum EstadoSuscripcion{
        ACTIVA,
        INACTIVA,
        CANCELADA
    }
}
