package com.servidos.v1.kitchen.domain;

import com.servidos.v1.ordering.domain.EstadoPedido;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class PreparacionPedido {
    private Long pedido_id;
    private Long restaurante_id;
    private EstadoPedido estado;
    private LocalDateTime created_at;
}
