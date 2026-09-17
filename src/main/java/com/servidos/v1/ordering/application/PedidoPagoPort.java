package com.servidos.v1.ordering.application;

public interface PedidoPagoPort {
    boolean estaPagado(Long pedidoId, Long restauranteId);
}
