package com.servidos.v1.ordering.infrastructure.jpa;

import com.servidos.v1.ordering.domain.EstadoPedido;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import java.util.List;
import java.util.Optional;

@Repository
public interface PedidoJpaRepository extends JpaRepository<PedidoJpaEntity, Long> {
    Optional<PedidoJpaEntity> findByPedidoIdAndRestauranteId(Long pedidoId, Long restauranteId);
    List<PedidoJpaEntity> findByRestauranteId(Long restauranteId);
    List<PedidoJpaEntity> findByRestauranteIdAndEstado(Long restauranteId, EstadoPedido estado);
}
