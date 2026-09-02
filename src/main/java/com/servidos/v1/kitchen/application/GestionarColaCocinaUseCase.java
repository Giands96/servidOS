package com.servidos.v1.kitchen.application;

import com.servidos.v1.kitchen.domain.PreparacionPedido;
import com.servidos.v1.kitchen.domain.event.PedidoPreparadoEvent;
import com.servidos.v1.ordering.domain.EstadoPedido;
import com.servidos.v1.ordering.infrastructure.jpa.DetallePedidoJpaRepository;
import com.servidos.v1.ordering.infrastructure.jpa.PedidoJpaRepository;
import com.servidos.v1.ordering.infrastructure.mapper.DetallePedidoMapper;
import com.servidos.v1.shared.event.EventPublisher;
import com.servidos.v1.shared.exception.BusinessException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
public class GestionarColaCocinaUseCase {

    private final PedidoJpaRepository pedidoRepository;
    private final DetallePedidoJpaRepository detalleRepository;
    private final DetallePedidoMapper detallePedidoMapper;
    private final EventPublisher eventPublisher;

    /**
     * Lista pedidos en preparación para un restaurante (proyección Kanban).
     * No tiene tabla propia; lee directamente ordering.pedido.
     */
    public List<PreparacionPedido> listarEnPreparacion(Long restauranteId) {
        if (restauranteId == null) {
            throw new BusinessException("Restaurante no identificado");
        }
        var pedidos = pedidoRepository.findByRestauranteIdAndEstado(restauranteId, EstadoPedido.EN_PREPARACION);
        return pedidos.stream().map(pedido -> {
            var items = detalleRepository.findByPedidoIdAndRestauranteId(pedido.getPedidoId(), restauranteId)
                    .stream()
                    .map(detallePedidoMapper::toDomain)
                    .toList();
            return PreparacionPedido.builder()
                    .pedido_id(pedido.getPedidoId())
                    .restaurante_id(pedido.getRestauranteId())
                    .mesa_id(pedido.getMesaId())
                    .tipoPedido(pedido.getTipoPedido())
                    .estado(pedido.getEstado())
                    .observacion(pedido.getObservacion())
                    .total(pedido.getTotal())
                    .created_at(pedido.getCreatedAt())
                    .items(items)
                    .build();
        }).toList();
    }

    /**
     * Marca un pedido como LISTO. Valida que pertenezca al tenant y esté en EN_PREPARACION.
     * Boundary: para MVP hace setEstado directo en ordering.pedido; idealmente delegaría a
     * ordering.CambiarEstadoPedidoUseCase para respetar límite modular (kitchen nunca escribe
     * directo en pedido — ver design spec).
     */
    @Transactional
    public void marcarListo(Long pedidoId, Long restauranteId) {
        if (pedidoId == null) {
            throw new BusinessException("Pedido no identificado");
        }
        if (restauranteId == null) {
            throw new BusinessException("Restaurante no identificado");
        }
        var pedido = pedidoRepository.findByPedidoIdAndRestauranteId(pedidoId, restauranteId)
                .orElseThrow(() -> new BusinessException("Pedido no encontrado"));
        if (pedido.getEstado() != EstadoPedido.EN_PREPARACION) {
            throw new BusinessException("Pedido no está en preparación");
        }
        pedido.setEstado(EstadoPedido.LISTO);
        pedidoRepository.save(pedido);
        eventPublisher.publish(new PedidoPreparadoEvent(pedidoId, restauranteId));
    }
}
