package com.servidos.v1.payment.infrastructure.jpa;

import com.servidos.v1.payment.domain.EstadoPago;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface PagoJpaRepository extends JpaRepository<PagoJpaEntity, Long> {
    boolean existsByPedidoIdAndRestauranteIdAndEstado(Long pedidoId, Long restauranteId, EstadoPago estado);
    Optional<PagoJpaEntity> findByPedidoIdAndRestauranteId(Long pedidoId, Long restauranteId);
    Optional<PagoJpaEntity> findByPagoIdAndRestauranteId(Long pagoId, Long restauranteId);    List<PagoJpaEntity> findByRestauranteId(Long restauranteId);
}
