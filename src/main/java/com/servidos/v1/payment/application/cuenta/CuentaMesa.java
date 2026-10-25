package com.servidos.v1.payment.application.cuenta;

import com.servidos.v1.ordering.domain.EstadoMesa;
import com.servidos.v1.ordering.domain.EstadoPedido;
import com.servidos.v1.ordering.domain.TipoPedido;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

/**
 * Lo que la mesa debe hoy: sus pedidos pendientes de cobro (rondas de mesa y recojo/delivery
 * vinculados) con sus ítems y el total, calculado en el servidor a partir de los totales de la base.
 */
public record CuentaMesa(
        Long mesaId,
        Integer numero,
        EstadoMesa estado,
        Integer comensales,
        Integer capacidad,
        List<Pedido> pedidos,
        BigDecimal total) {

    public record Pedido(
            Long pedidoId,
            TipoPedido tipoPedido,
            EstadoPedido estado,
            LocalDateTime createdAt,
            List<Item> items,
            String observacion,
            BigDecimal total) {}

    public record Item(String nombre, Integer cantidad, BigDecimal subtotal) {}
}
