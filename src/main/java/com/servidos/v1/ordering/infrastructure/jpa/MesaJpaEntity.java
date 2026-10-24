package com.servidos.v1.ordering.infrastructure.jpa;

import com.servidos.v1.ordering.domain.EstadoMesa;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "mesa")
@Data
@NoArgsConstructor
@AllArgsConstructor
public class MesaJpaEntity {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "mesa_id")
    private Long mesaId;
    @Column(name = "restaurante_id", nullable = false)
    private Long restauranteId;
    @Column(name = "numero", nullable = false)
    private Integer numero;
    @Column(name = "capacidad", nullable = false)
    private Integer capacidad;
    @Column(name = "comensales")
    private Integer comensales;
    @Enumerated(EnumType.STRING)
    @Column(name = "estado", nullable = false, length = 20)
    private EstadoMesa estado;
    @Column(name = "activa", nullable = false)
    private boolean activa = true;
}
