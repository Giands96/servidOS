package com.servidos.v1.ordering.application.pedido;

import com.servidos.v1.ordering.application.PedidoPagoPort;
import com.servidos.v1.ordering.domain.EstadoPedido;
import com.servidos.v1.ordering.domain.Pedido;
import com.servidos.v1.ordering.domain.TipoPedido;
import com.servidos.v1.ordering.domain.event.PedidoCanceladoEvent;
import com.servidos.v1.ordering.domain.event.PedidoEntregadoEvent;
import com.servidos.v1.ordering.domain.event.PedidoEstadoCambiadoEvent;
import com.servidos.v1.ordering.infrastructure.jpa.PedidoJpaEntity;
import com.servidos.v1.ordering.infrastructure.jpa.PedidoJpaRepository;
import com.servidos.v1.ordering.infrastructure.mapper.PedidoMapper;
import com.servidos.v1.shared.event.EventPublisher;
import com.servidos.v1.shared.exception.BusinessException;
import com.servidos.v1.shared.exception.ForbiddenException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class CambiarEstadoPedidoUseCase {
    private static final String MESERO = "MESERO";

    private final PedidoJpaRepository pedidoRepository;
    private final PedidoMapper pedidoMapper;
    private final PedidoPagoPort pagoPort;
    private final EventPublisher eventPublisher;

    /** Flujos internos (confirmar, cancelar al reembolsar): sin restricción de rol, solo la matriz. */
    @Transactional
    public Pedido ejecutar(Long pedidoId, Long restauranteId, EstadoPedido nuevoEstado) {
        return ejecutar(pedidoId, restauranteId, nuevoEstado, null);
    }

    /**
     * Cambio pedido por un usuario. El rol sale del JWT ({@code CurrentUser}), nunca del JSON. El MESERO
     * solo puede pasar un pedido MESA de LISTO a ENTREGADO; los demás roles usan la matriz completa.
     */
    @Transactional
    public Pedido ejecutar(Long pedidoId, Long restauranteId, EstadoPedido nuevoEstado, String rol) {
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
        if (MESERO.equalsIgnoreCase(rol)) {
            exigirEntregaDeMesa(pedido, nuevoEstado);
        }
        validarTransicion(pedido, nuevoEstado);
        EstadoPedido anterior = pedido.getEstado();
        pedido.setEstado(nuevoEstado);
        PedidoJpaEntity guardado = pedidoRepository.save(pedido);
        eventPublisher.publish(new PedidoEstadoCambiadoEvent(pedidoId, restauranteId, anterior, nuevoEstado));
        if (nuevoEstado == EstadoPedido.CANCELADO) {
            eventPublisher.publish(new PedidoCanceladoEvent(pedidoId, restauranteId));
        }
        if (nuevoEstado == EstadoPedido.ENTREGADO) {
            eventPublisher.publish(new PedidoEntregadoEvent(pedidoId, restauranteId));
        }
        return pedidoMapper.toDomain(guardado);
    }

    private void exigirEntregaDeMesa(PedidoJpaEntity pedido, EstadoPedido nuevo) {
        if (pedido.getTipoPedido() != TipoPedido.MESA
                || pedido.getEstado() != EstadoPedido.LISTO
                || nuevo != EstadoPedido.ENTREGADO) {
            throw new ForbiddenException("El mesero solo puede marcar como entregado un pedido de mesa listo");
        }
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
            // Un delivery fallido (cliente ausente, dirección errónea) se puede cancelar.
            // Si ya estaba pagado, el chequeo de arriba lo frena: primero se reembolsa.
            case EN_ENTREGA -> exigir(
                    delivery && (nuevo == EstadoPedido.ENTREGADO || nuevo == EstadoPedido.CANCELADO),
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
