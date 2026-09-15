package com.servidos.v1.ordering.application.pedido;

public record CrearPedidoItem(Long productoId,
                              Integer cantidad,
                              String observacion) {}
