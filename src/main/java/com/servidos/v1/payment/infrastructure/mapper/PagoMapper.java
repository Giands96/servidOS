package com.servidos.v1.payment.infrastructure.mapper;

import com.servidos.v1.payment.domain.Pago;
import com.servidos.v1.payment.infrastructure.jpa.PagoJpaEntity;
import org.springframework.stereotype.Component;

@Component
public class PagoMapper {

    public Pago toDomain(PagoJpaEntity e) {
        if (e == null) return null;
        return Pago.builder()
                .pago_id(e.getPagoId())
                .pedido_id(e.getPedidoId())
                .restaurante_id(e.getRestauranteId())
                .usuario_id(e.getUsuarioId())
                .metodo_pago(e.getMetodoPago())
                .monto(e.getMonto())
                .vuelto(e.getVuelto())
                .estado(e.getEstado())
                .fecha_pago(e.getFechaPago())
                .referenciaExterna(e.getReferenciaExterna())
                .created_at(e.getCreatedAt())
                .updated_at(e.getUpdatedAt())
                .build();
    }

    public PagoJpaEntity toEntity(Pago d) {
        if (d == null) return null;
        PagoJpaEntity e = new PagoJpaEntity();
        e.setPagoId(d.getPago_id());
        e.setPedidoId(d.getPedido_id());
        e.setRestauranteId(d.getRestaurante_id());
        e.setUsuarioId(d.getUsuario_id());
        e.setMetodoPago(d.getMetodo_pago());
        e.setMonto(d.getMonto());
        e.setVuelto(d.getVuelto());
        e.setEstado(d.getEstado());
        e.setFechaPago(d.getFecha_pago());
        e.setReferenciaExterna(d.getReferenciaExterna());
        e.setCreatedAt(d.getCreated_at());
        e.setUpdatedAt(d.getUpdated_at());
        return e;
    }
}
