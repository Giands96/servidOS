package com.servidos.v1.payment.api.dto;

import com.servidos.v1.ordering.domain.EstadoMesa;
import com.servidos.v1.ordering.domain.EstadoPedido;
import com.servidos.v1.ordering.domain.TipoPedido;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

/** Cuenta de la mesa: pedidos pendientes de cobro con sus ítems y el total a cobrar. */
public record CuentaMesaResponse(
        Long mesaId,
        Integer numero,
        EstadoMesa estado,
        Integer comensales,
        Integer capacidad,
        List<PedidoCuentaResponse> pedidos,
        BigDecimal total) {

    public record PedidoCuentaResponse(
            Long pedidoId,
            TipoPedido tipoPedido,
            EstadoPedido estado,
            LocalDateTime createdAt,
            List<ItemCuentaResponse> items,
            String observacion,
            BigDecimal total) {}

    public record ItemCuentaResponse(String nombre, Integer cantidad, BigDecimal subtotal) {}
}
