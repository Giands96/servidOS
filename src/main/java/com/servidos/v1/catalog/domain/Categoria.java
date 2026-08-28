package com.servidos.v1.catalog.domain;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class Categoria {

    private Long categoria_id;
    private Long restaurante_id;
    private String nombre;
    private EstadoCategoria estado;
    private LocalDateTime created_at;
    private LocalDateTime updated_at;
}
