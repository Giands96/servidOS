package com.servidos.v1.ordering.infrastructure.jpa;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import java.util.List;

@Repository
public interface DetallePedidoJpaRepository extends JpaRepository<DetallePedidoJpaEntity, Long> {
    List<DetallePedidoJpaEntity> findByPedidoIdAndRestauranteId(Long pedidoId, Long restauranteId);
}
