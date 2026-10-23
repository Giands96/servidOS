package com.servidos.v1.ordering.infrastructure.jpa;

import com.servidos.v1.ordering.domain.EstadoMesa;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface MesaJpaRepository extends JpaRepository<MesaJpaEntity, Long> {

    List<MesaJpaEntity> findByRestauranteIdOrderByNumeroAsc(Long restauranteId);

    List<MesaJpaEntity> findByRestauranteIdAndEstadoOrderByNumeroAsc(Long restauranteId, EstadoMesa estado);

    boolean existsByRestauranteIdAndNumero(Long restauranteId, Integer numero);

    boolean existsByRestauranteIdAndNumeroAndMesaIdNot(Long restauranteId, Integer numero, Long mesaId);

    boolean existsByMesaIdAndRestauranteId(Long mesaId, Long restauranteId);

    /**
     * Trae la mesa del tenant con lock de escritura (SELECT ... FOR UPDATE) hasta el fin de la
     * transacción. Todo lo que cambia el estado de la mesa o decide sobre él (ocupar, liberar,
     * pedidos de mesa, liberación automática) pasa por acá, así una liberación no se intercala
     * con un pedido nuevo de la misma mesa.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT m FROM MesaJpaEntity m WHERE m.mesaId = :mesaId AND m.restauranteId = :restauranteId")
    Optional<MesaJpaEntity> bloquearPorIdYRestaurante(@Param("mesaId") Long mesaId,
                                                      @Param("restauranteId") Long restauranteId);
}
