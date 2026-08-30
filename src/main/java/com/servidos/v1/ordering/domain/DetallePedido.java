package com.servidos.v1.ordering.domain;

import com.servidos.v1.shared.exception.BusinessException;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@Builder
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

    public static DetallePedido crear(Long restaurante_id, Long pedido_id, Long producto_id, Integer cantidad, BigDecimal precio_unitario, String observacion) {
        if (restaurante_id == null) throw new BusinessException("El restaurante_id es obligatorio");
        if (producto_id == null) throw new BusinessException("El producto_id es obligatorio");
        if (cantidad == null || cantidad <= 0) throw new BusinessException("La cantidad debe ser mayor a 0");
        if (precio_unitario == null || precio_unitario.compareTo(BigDecimal.ZERO) <= 0) throw new BusinessException("El precio unitario debe ser mayor a 0");
        BigDecimal subtotal = precio_unitario.multiply(BigDecimal.valueOf(cantidad));
        return DetallePedido.builder()
                .restaurante_id(restaurante_id)
                .pedido_id(pedido_id)
                .producto_id(producto_id)
                .cantidad(cantidad)
                .precio_unitario(precio_unitario)
                .subtotal(subtotal)
                .observacion(observacion)
                .build();
    }
}
