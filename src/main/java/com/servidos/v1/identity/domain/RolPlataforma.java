package com.servidos.v1.identity.domain;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class RolPlataforma {

    Long rol_plataforma_id;
    String nombre;
    String descripcion;
    EstadoRol estado;


}
