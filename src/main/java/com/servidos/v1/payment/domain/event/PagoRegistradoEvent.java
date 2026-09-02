package com.servidos.v1.payment.domain.event;

import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public class PagoRegistradoEvent {

    private Long pagoId;
    private Long restauranteId;
    private Long pedidoId;
}
