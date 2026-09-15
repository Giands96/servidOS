package com.servidos.v1.ordering.application.pedido;

import com.servidos.v1.ordering.domain.TipoPedido;
import java.util.List;

public record CrearPedidoCommand(TipoPedido tipoPedido,
                                 Long mesaId,
                                 String observacion,
                                 String repartidorNombre,
                                 List<CrearPedidoItem> items) {}
