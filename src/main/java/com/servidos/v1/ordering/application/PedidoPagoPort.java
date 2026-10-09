package com.servidos.v1.ordering.application;

import java.util.Collection;
import java.util.Map;

public interface PedidoPagoPort {
    boolean estaPagado(Long pedidoId, Long restauranteId);

    /** Estado del pago (PAGADO, REEMBOLSADO, ...) de cada pedido que tiene uno; los que no, no aparecen. */
    Map<Long, String> estadosDePago(Collection<Long> pedidoIds, Long restauranteId);
}
