package com.servidos.v1.identity.domain;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "rol_plataforma")
@Data
@NoArgsConstructor
@AllArgsConstructor
public class RolPlataforma {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    Long rol_plataforma_id;

    @Column(name = "nombre", nullable = false, unique = true)
    String nombre;

    @Column(name = "descripcion", nullable = false)
    String descripcion;

    @Column(name = "estado", nullable = false)
    @Enumerated(EnumType.STRING)
    EstadoRol estado;


}
