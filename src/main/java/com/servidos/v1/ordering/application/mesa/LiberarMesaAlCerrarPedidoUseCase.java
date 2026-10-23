package com.servidos.v1.ordering.application.mesa;

import com.servidos.v1.ordering.domain.EstadoMesa;
import com.servidos.v1.ordering.domain.Mesa;
import com.servidos.v1.ordering.infrastructure.jpa.MesaJpaRepository;
import com.servidos.v1.ordering.infrastructure.jpa.PedidoJpaRepository;
import com.servidos.v1.ordering.infrastructure.mapper.MesaMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Libera la mesa de un pedido que acaba de cerrarse (pagado, entregado o cancelado) si ya no le
 * quedan pedidos abiertos. Corre dentro de la transacción que originó el evento; con la mesa
 * bloqueada, un pedido nuevo de la misma mesa o se ve acá o espera a que la liberación termine.
 * Idempotente y silencioso: pedido sin mesa (delivery/recojo), mesa ya libre o con otros pedidos
 * abiertos no hacen nada, así nunca rompe la operación que lo disparó.
 */
@Service
@RequiredArgsConstructor
public class LiberarMesaAlCerrarPedidoUseCase {

    private final PedidoJpaRepository pedidoRepository;
    private final MesaJpaRepository mesaRepository;
    private final MesaMapper mesaMapper;

    @Transactional
    public void ejecutar(Long pedidoId, Long restauranteId) {
        var pedido = pedidoRepository.findByPedidoIdAndRestauranteId(pedidoId, restauranteId).orElse(null);
        if (pedido == null || pedido.getMesaId() == null) return;

        var entity = mesaRepository.bloquearPorIdYRestaurante(pedido.getMesaId(), restauranteId).orElse(null);
        if (entity == null || entity.getEstado() != EstadoMesa.OCUPADA) return;

        // El cambio de estado del pedido que disparó el evento tiene que verse en el conteo.
        pedidoRepository.flush();
        if (pedidoRepository.contarAbiertosEnMesa(entity.getMesaId(), restauranteId) > 0) return;

        Mesa mesa = mesaMapper.toDomain(entity);
        mesa.liberar();
        mesaMapper.updateEntity(mesa, entity);
        mesaRepository.saveAndFlush(entity);
    }
}
