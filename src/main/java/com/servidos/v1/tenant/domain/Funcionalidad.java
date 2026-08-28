package com.servidos.v1.tenant.domain;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class Funcionalidad {

    private Long funcionalidad_id;
    private String nombre_funcionalidad;
    private String descripcion_funcionalidad;
}
