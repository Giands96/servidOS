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

    public void onCreate() {
        this.created_at = LocalDateTime.now();
        this.updated_at = LocalDateTime.now(); // Al crear, ambas fechas son iguales
    }

    public void onUpdate() {
        this.updated_at = LocalDateTime.now(); // Al editar, solo cambia esta fecha
    }

    enum EstadoSuscripcion{
        ACTIVA,
        INACTIVA,
        CANCELADA
    }

}
