package com.servidos.v1.ordering.infrastructure.jpa;

import com.servidos.v1.ordering.domain.EstadoPedido;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Repository
public interface PedidoJpaRepository extends JpaRepository<PedidoJpaEntity, Long> {
    Optional<PedidoJpaEntity> findByPedidoIdAndRestauranteId(Long pedidoId, Long restauranteId);
    List<PedidoJpaEntity> findByRestauranteIdAndEstado(Long restauranteId, EstadoPedido estado);

    /**
     * El pedido {@code p} ya se saldó: tiene un pago PAGADO o REEMBOLSADO (un reembolso no se
     * vuelve a cobrar). Es la única definición de "cobrado" para las queries de abajo: la cuenta de
     * la mesa, su liberación y el listado "por cobrar" tienen que coincidir.
     */
    String PAGO_SALDADO = """
            EXISTS (
                SELECT 1 FROM pago pg
                WHERE pg.pedido_id = p.pedido_id AND pg.restaurante_id = p.restaurante_id
                  AND pg.estado IN ('PAGADO', 'REEMBOLSADO'))
            """;

    /**
     * Pedidos "abiertos" de una mesa: los que todavía retienen a sus comensales o su cuenta.
     * <ul>
     *   <li>Pedidos MESA: todo pedido no cancelado salvo el ENTREGADO que ya se saldó (pago PAGADO
     *   o REEMBOLSADO). Un pedido cobrado pero aún sin entregar sigue abierto: el cliente espera
     *   su comida.</li>
     *   <li>Pedidos DELIVERY/RECOJO con ese mesa_id: no ocupan la mesa, pero su cobro forma parte de
     *   la cuenta, así que la retienen mientras no estén cancelados ni cobrados (PAGADO o
     *   REEMBOLSADO), estén entregados o no. Una vez cobrados dejan de contar aunque sigan en
     *   camino: un delivery pagado no debe dejar la mesa ocupada hasta que llegue el repartidor.</li>
     * </ul>
     * Native porque mira la tabla pago sin acoplar ordering a la entidad de payment.
     */
    @Query(value = """
            SELECT count(*) FROM pedido p
            WHERE p.mesa_id = :mesaId AND p.restaurante_id = :restauranteId
              AND p.estado <> 'CANCELADO'
              AND CASE
                    WHEN p.tipo_pedido = 'MESA' THEN NOT (p.estado = 'ENTREGADO' AND\s""" + PAGO_SALDADO + """
                    )
                    ELSE NOT\s""" + PAGO_SALDADO + """
                  END
            """, nativeQuery = true)
    long contarAbiertosEnMesa(@Param("mesaId") Long mesaId, @Param("restauranteId") Long restauranteId);

    /**
     * Pedidos de la mesa pendientes de cobro, del más viejo al más nuevo: de cualquier tipo
     * (MESA, y RECOJO/DELIVERY vinculados), no cancelados y sin un pago PAGADO o REEMBOLSADO.
     * Es la cuenta de la mesa. Native porque mira la tabla pago sin acoplar ordering a la entidad
     * de payment.
     */
    @Query(value = """
            SELECT p.* FROM pedido p
            WHERE p.mesa_id = :mesaId AND p.restaurante_id = :restauranteId
              AND p.estado <> 'CANCELADO'
              AND NOT\s""" + PAGO_SALDADO + """
            ORDER BY p.created_at, p.pedido_id
            """, nativeQuery = true)
    List<PedidoJpaEntity> buscarPendientesDeCobroEnMesa(@Param("mesaId") Long mesaId,
                                                        @Param("restauranteId") Long restauranteId);

    String FILTRO_LISTADO = """
            FROM pedido p
            WHERE p.restaurante_id = :restauranteId
              AND (CAST(:estado AS varchar) IS NULL OR p.estado = CAST(:estado AS varchar))
              AND (CAST(:desde AS timestamp) IS NULL OR p.created_at >= CAST(:desde AS timestamp))
              AND (CAST(:hasta AS timestamp) IS NULL OR p.created_at < CAST(:hasta AS timestamp))
              AND (:porCobrar = false OR (p.estado <> 'CANCELADO' AND NOT\s""" + PAGO_SALDADO + """
              ))
            """;

    /**
     * Listado del restaurante con filtros opcionales (null = sin filtro), del más nuevo al
     * más viejo. Native porque "por cobrar" mira la tabla pago sin acoplar ordering a la
     * entidad de payment. El orden es fijo: el Pageable no debe traer sort.
     */
    @Query(value = "SELECT p.* " + FILTRO_LISTADO + " ORDER BY p.created_at DESC, p.pedido_id DESC",
            countQuery = "SELECT count(*) " + FILTRO_LISTADO,
            nativeQuery = true)
    Page<PedidoJpaEntity> listar(@Param("restauranteId") Long restauranteId,
                                 @Param("estado") String estado,
                                 @Param("desde") LocalDateTime desde,
                                 @Param("hasta") LocalDateTime hasta,
                                 @Param("porCobrar") boolean porCobrar,
                                 Pageable pageable);
}
