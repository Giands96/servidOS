package com.servidos.v1.ordering.domain;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class DetallePedido {
    private Long detalle_id;
    private Long pedido_id;
    private Long restaurante_id;
    private Long producto_id;
    private Integer cantidad;
    private BigDecimal precio_unitario;
    private BigDecimal subtotal;
    private String observacion;
    private LocalDateTime created_at;
    private LocalDateTime updated_at;

    public void onCreate() {
        this.created_at = LocalDateTime.now();
        this.updated_at = LocalDateTime.now(); // Al crear, ambas fechas son iguales
    }

    public void onUpdate() {
        this.updated_at = LocalDateTime.now(); // Al editar, solo cambia esta fecha
    }
}
