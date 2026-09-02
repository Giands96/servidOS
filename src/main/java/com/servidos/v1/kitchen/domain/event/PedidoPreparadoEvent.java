package com.servidos.v1.kitchen.domain.event;

import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public class PedidoPreparadoEvent {
    private final Long pedidoId;
    private final Long restauranteId;
}
