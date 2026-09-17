package com.servidos.v1.ordering.application.pedido;

import com.servidos.v1.ordering.application.PedidoPagoPort;
import com.servidos.v1.ordering.domain.EstadoPedido;
import com.servidos.v1.ordering.domain.Pedido;
import com.servidos.v1.ordering.domain.TipoPedido;
import com.servidos.v1.ordering.domain.event.PedidoCanceladoEvent;
import com.servidos.v1.ordering.infrastructure.jpa.PedidoJpaEntity;
import com.servidos.v1.ordering.infrastructure.jpa.PedidoJpaRepository;
import com.servidos.v1.ordering.infrastructure.mapper.PedidoMapper;
import com.servidos.v1.shared.event.EventPublisher;
import com.servidos.v1.shared.exception.BusinessException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class CambiarEstadoPedidoUseCase {
    private final PedidoJpaRepository pedidoRepository;
    private final PedidoMapper pedidoMapper;
    private final PedidoPagoPort pagoPort;
    private final EventPublisher eventPublisher;

    @Transactional
    public Pedido ejecutar(Long pedidoId, Long restauranteId, EstadoPedido nuevoEstado) {
        if (pedidoId == null) {
            throw new BusinessException("Pedido no identificado");
        }
        if (restauranteId == null) {
            throw new BusinessException("Restaurante no identificado");
        }
        if (nuevoEstado == null) {
            throw new BusinessException("El estado es obligatorio");
        }
        PedidoJpaEntity pedido = pedidoRepository.findByPedidoIdAndRestauranteId(pedidoId, restauranteId)
                .orElseThrow(() -> new BusinessException("Pedido no encontrado"));
        validarTransicion(pedido, nuevoEstado);
        pedido.setEstado(nuevoEstado);
        PedidoJpaEntity guardado = pedidoRepository.save(pedido);
        if (nuevoEstado == EstadoPedido.CANCELADO) {
            eventPublisher.publish(new PedidoCanceladoEvent(pedidoId, restauranteId));
        }
        return pedidoMapper.toDomain(guardado);
    }

    private void validarTransicion(PedidoJpaEntity pedido, EstadoPedido nuevo) {
        EstadoPedido actual = pedido.getEstado();
        if (actual == nuevo) {
            throw new BusinessException("El pedido ya está en estado " + nuevo);
        }
        if (nuevo == EstadoPedido.CANCELADO
                && pagoPort.estaPagado(pedido.getPedidoId(), pedido.getRestauranteId())) {
            throw new BusinessException("El pedido pagado no puede cancelarse");
        }
        boolean delivery = pedido.getTipoPedido() == TipoPedido.DELIVERY;
        switch (actual) {
            case PENDIENTE -> exigir(
                    nuevo == EstadoPedido.EN_PREPARACION || nuevo == EstadoPedido.CANCELADO,
                    actual, nuevo);
            case EN_PREPARACION -> exigir(
                    nuevo == EstadoPedido.LISTO || nuevo == EstadoPedido.CANCELADO,
                    actual, nuevo);
            case LISTO -> {
                if (delivery) {
                    exigir(nuevo == EstadoPedido.EN_ENTREGA || nuevo == EstadoPedido.CANCELADO,
                            actual, nuevo);
                } else {
                    exigir(nuevo == EstadoPedido.ENTREGADO || nuevo == EstadoPedido.CANCELADO,
                            actual, nuevo);
                }
            }
            case EN_ENTREGA -> exigir(
                    delivery && nuevo == EstadoPedido.ENTREGADO,
                    actual, nuevo);
            default -> throw new BusinessException(
                    "El pedido en estado " + actual + " no admite cambios");
        }
    }

    private void exigir(boolean permitido, EstadoPedido actual, EstadoPedido nuevo) {
        if (!permitido) {
            throw new BusinessException("Transición no permitida de " + actual + " a " + nuevo);
        }
    }
}
