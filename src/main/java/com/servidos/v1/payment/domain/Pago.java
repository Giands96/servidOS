package com.servidos.v1.payment.domain;

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
public class Pago {

    private Long pago_id;
    private Long pedido_id;
    private Long restaurante_id;
    private Long usuario_id;
    private MetodoPago metodo_pago;
    private BigDecimal monto;
    private BigDecimal vuelto;
    private EstadoPago estado;
    private LocalDateTime fecha_pago;
    private String referenciaExterna;
    private LocalDateTime created_at;
    private LocalDateTime updated_at;

    public static Pago crear(Long pedido_id, Long restaurante_id, Long usuario_id, MetodoPago metodo_pago, BigDecimal monto, BigDecimal vuelto, String referenciaExterna) {
        if (pedido_id == null) throw new BusinessException("El pedido_id es obligatorio");
        if (restaurante_id == null) throw new BusinessException("El restaurante_id es obligatorio");
        if (usuario_id == null) throw new BusinessException("El usuario_id es obligatorio");
        if (metodo_pago == null) throw new BusinessException("El método de pago es obligatorio");
        if (monto == null || monto.compareTo(BigDecimal.ZERO) <= 0) throw new BusinessException("El monto debe ser mayor a 0");
        if (vuelto != null && vuelto.compareTo(BigDecimal.ZERO) < 0) throw new BusinessException("El vuelto no puede ser negativo");
        if (metodo_pago != MetodoPago.EFECTIVO && vuelto != null) throw new BusinessException("El vuelto solo aplica para efectivo");
        return Pago.builder().pedido_id(pedido_id).restaurante_id(restaurante_id).usuario_id(usuario_id).metodo_pago(metodo_pago).monto(monto).vuelto(vuelto).referenciaExterna(referenciaExterna).estado(EstadoPago.PAGADO).fecha_pago(LocalDateTime.now()).build();
    }
}
