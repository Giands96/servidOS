package com.servidos.v1.ordering.domain.event;

import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public class PedidoCreadoEvent {
    private final Long pedidoId;
    private final Long restauranteId;
}
