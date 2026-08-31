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
public class Pedido {
    private Long pedido_id;
    private Long restaurante_id;
    private Long mesa_id;
    private Long usuario_id;
    private TipoPedido tipoPedido;
    private String observacion;
    private String repartidor_nombre;
    private EstadoPedido estado;
    private BigDecimal total;
    private LocalDateTime created_at;
    private LocalDateTime updated_at;

    public static Pedido crear(Long restaurante_id,
                               Long usuario_id,
                               Long mesa_id,
                               TipoPedido tipoPedido,
                               String observacion,
                               String repartidor_nombre,
                               EstadoPedido estado,
                               BigDecimal total) {
        if (restaurante_id == null) throw new BusinessException("El restaurante_id es obligatorio");
        if (tipoPedido == null) throw new BusinessException("El tipo de pedido es obligatorio");
        if (tipoPedido == TipoPedido.MESA && mesa_id == null) throw new BusinessException("La mesa es obligatoria para pedidos en mesa");
        if (total != null && total.compareTo(BigDecimal.ZERO) < 0) throw new BusinessException("El total no puede ser negativo");
        return Pedido.builder()
                .restaurante_id(restaurante_id)
                .usuario_id(usuario_id)
                .mesa_id(mesa_id)
                .tipoPedido(tipoPedido)
                .observacion(observacion)
                .repartidor_nombre(repartidor_nombre)
                .estado(estado != null ? estado : EstadoPedido.PENDIENTE)
                .total(total != null ? total : BigDecimal.ZERO)
                .build();
    }
}
