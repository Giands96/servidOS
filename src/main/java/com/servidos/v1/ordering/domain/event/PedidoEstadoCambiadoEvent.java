package com.servidos.v1.ordering.domain.event;

import com.servidos.v1.ordering.domain.EstadoPedido;
import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * Cualquier transición de estado de un pedido hecha por {@code CambiarEstadoPedidoUseCase}.
 * Los eventos específicos (p. ej. {@link PedidoCanceladoEvent}) se siguen publicando aparte.
 */
@Getter
@AllArgsConstructor
public class PedidoEstadoCambiadoEvent {
    private final Long pedidoId;
    private final Long restauranteId;
    private final EstadoPedido estadoAnterior;
    private final EstadoPedido estadoNuevo;
}
