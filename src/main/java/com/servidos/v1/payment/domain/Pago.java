package com.servidos.v1.payment.domain;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class Pago {

    private Long pago_id;
    private Long pedido_id;
    private Long restaurante_id;
    private Long usuario_id;
    private MetodoPago metodo_pago;
    private BigDecimal monto;
    private BigDecimal vuelto;
    private EstadoPago estado;
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
