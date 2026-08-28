package com.servidos.v1.tenant.domain;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class Restaurante {

    private Long restaurante_id;
    private String slug;
    private String nombre;
    private String direccion;
    private EstadoRestaurante estado;
    private LocalDateTime created_at;
    private LocalDateTime updated_at;
}
