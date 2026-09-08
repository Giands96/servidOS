package com.servidos.v1.identity.domain;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class RolPlataforma {

    private Long rol_plataforma_id;
    private String nombre;
    private String descripcion;
    private EstadoRol estado;


}
