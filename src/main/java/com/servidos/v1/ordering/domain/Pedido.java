package com.servidos.v1.ordering.domain;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class Pedido {
    private Long pedido_id;
    //* ID Restaurante que realiza el pedido */
    private Long restaurante_id;
    //* ID Mesa que realiza el pedido */
    private Long mesa_id;
    //* ID Usuario (Recepcion) que realiza el pedido */
    private Long usuario_id;
    private TipoPedido pedido;
    private String observacion;
    private String repartidor_nombre;
    private EstadoPedido estado;
    private BigDecimal total;
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
