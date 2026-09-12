package com.servidos.v1.identity.domain;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class RolRestaurante {
    private Long rol_restaurante_id;
    private String nombre;
    private String descripcion;
    private EstadoRol estado;
}
