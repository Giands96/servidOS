package com.servidos.v1.ordering.domain.event;

import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public class PedidoCanceladoEvent {
    private final Long pedidoId;
    private final Long restauranteId;
}
