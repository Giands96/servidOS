package com.servidos.v1.payment.domain.event;

import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public class PagoReembolsadoEvent {
    private final Long pagoId;
    private final Long restauranteId;
    private final Long pedidoId;
}
