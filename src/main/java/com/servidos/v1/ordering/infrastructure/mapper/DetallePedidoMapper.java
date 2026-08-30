package com.servidos.v1.ordering.infrastructure.mapper;

import com.servidos.v1.ordering.domain.DetallePedido;
import com.servidos.v1.ordering.infrastructure.jpa.DetallePedidoJpaEntity;
import org.springframework.stereotype.Component;

@Component
public class DetallePedidoMapper {
    public DetallePedido toDomain(DetallePedidoJpaEntity e) {
        if (e == null) return null;
        return DetallePedido.builder()
                .detalle_id(e.getDetalleId())
                .pedido_id(e.getPedidoId())
                .restaurante_id(e.getRestauranteId())
                .producto_id(e.getProductoId())
                .cantidad(e.getCantidad())
                .precio_unitario(e.getPrecioUnitario())
                .subtotal(e.getSubtotal())
                .observacion(e.getObservacion())
                .created_at(e.getCreatedAt())
                .updated_at(e.getUpdatedAt())
                .build();
    }
    public DetallePedidoJpaEntity toEntity(DetallePedido d) {
        if (d == null) return null;
        DetallePedidoJpaEntity e = new DetallePedidoJpaEntity();
        e.setDetalleId(d.getDetalle_id());
        e.setPedidoId(d.getPedido_id());
        e.setRestauranteId(d.getRestaurante_id());
        e.setProductoId(d.getProducto_id());
        e.setCantidad(d.getCantidad());
        e.setPrecioUnitario(d.getPrecio_unitario());
        e.setSubtotal(d.getSubtotal());
        e.setObservacion(d.getObservacion());
        return e;
    }
}
