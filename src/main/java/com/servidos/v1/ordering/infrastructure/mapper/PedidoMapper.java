package com.servidos.v1.ordering.infrastructure.mapper;

import com.servidos.v1.ordering.domain.Pedido;
import com.servidos.v1.ordering.infrastructure.jpa.PedidoJpaEntity;
import org.springframework.stereotype.Component;

@Component
public class PedidoMapper {
    public Pedido toDomain(PedidoJpaEntity e) {
        if (e == null) return null;
        return Pedido.builder()
                .pedido_id(e.getPedidoId())
                .restaurante_id(e.getRestauranteId())
                .usuario_id(e.getUsuarioId())
                .mesa_id(e.getMesaId())
                .tipoPedido(e.getTipoPedido())
                .observacion(e.getObservacion())
                .repartidor_nombre(e.getRepartidorNombre())
                .estado(e.getEstado())
                .total(e.getTotal())
                .created_at(e.getCreatedAt())
                .updated_at(e.getUpdatedAt())
                .build();
    }
    public PedidoJpaEntity toEntity(Pedido d) {
        if (d == null) return null;
        PedidoJpaEntity e = new PedidoJpaEntity();
        e.setPedidoId(d.getPedido_id());
        e.setRestauranteId(d.getRestaurante_id());
        e.setUsuarioId(d.getUsuario_id());
        e.setMesaId(d.getMesa_id());
        e.setTipoPedido(d.getTipoPedido());
        e.setObservacion(d.getObservacion());
        e.setRepartidorNombre(d.getRepartidor_nombre());
        e.setEstado(d.getEstado());
        e.setTotal(d.getTotal());
        return e;
    }
    public void updateEntity(Pedido domain, PedidoJpaEntity entity) {
        if (domain == null || entity == null) return;
        entity.setMesaId(domain.getMesa_id());
        entity.setTipoPedido(domain.getTipoPedido());
        entity.setObservacion(domain.getObservacion());
        entity.setRepartidorNombre(domain.getRepartidor_nombre());
        entity.setEstado(domain.getEstado());
        entity.setTotal(domain.getTotal());
    }
}
