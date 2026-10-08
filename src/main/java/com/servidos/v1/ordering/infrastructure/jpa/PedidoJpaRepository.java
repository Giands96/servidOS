package com.servidos.v1.ordering.infrastructure.jpa;

import com.servidos.v1.ordering.domain.EstadoPedido;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import java.util.List;
import java.util.Optional;

@Repository
public interface PedidoJpaRepository extends JpaRepository<PedidoJpaEntity, Long> {
    Optional<PedidoJpaEntity> findByPedidoIdAndRestauranteId(Long pedidoId, Long restauranteId);
    List<PedidoJpaEntity> findByRestauranteIdAndEstado(Long restauranteId, EstadoPedido estado);

    /**
     * La mesa no tiene entidad JPA: solo hace falta saber si pertenece al tenant.
     * Sin este chequeo un pedido podía apuntar a la mesa de otro restaurante.
     */
    @Query(value = "SELECT EXISTS (SELECT 1 FROM mesa WHERE mesa_id = :mesaId AND restaurante_id = :restauranteId)",
            nativeQuery = true)
    boolean existsMesaEnRestaurante(@Param("mesaId") Long mesaId, @Param("restauranteId") Long restauranteId);
}
