package com.servidos.v1.kitchen.domain;

import com.servidos.v1.ordering.domain.DetallePedido;
import com.servidos.v1.ordering.domain.EstadoPedido;
import com.servidos.v1.ordering.domain.TipoPedido;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PreparacionPedido {
    private Long pedido_id;
    private Long restaurante_id;
    private Long mesa_id;
    private TipoPedido tipoPedido;
    private EstadoPedido estado;
    private String observacion;
    private BigDecimal total;
    private LocalDateTime created_at;
    private List<DetallePedido> items;
}
