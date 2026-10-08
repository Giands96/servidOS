package com.servidos.v1.kitchen.api.dto;

import com.servidos.v1.ordering.domain.EstadoPedido;

/**
 * Aviso por WebSocket de que un pedido entró, se movió o salió del tablero de cocina.
 * Liviano a propósito: si el pedido es nuevo en una columna, el front pide el detalle a
 * {@code GET /cocina/cola} o {@code GET /cocina/listos}.
 */
public record CocinaEventoMessage(Long pedidoId, EstadoPedido estadoAnterior, EstadoPedido estadoNuevo) {
}
