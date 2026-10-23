package com.servidos.v1.ordering.infrastructure.mapper;

import com.servidos.v1.ordering.domain.Mesa;
import com.servidos.v1.ordering.infrastructure.jpa.MesaJpaEntity;
import org.springframework.stereotype.Component;

@Component
public class MesaMapper {
    public Mesa toDomain(MesaJpaEntity e) {
        if (e == null) return null;
        return Mesa.builder()
                .mesa_id(e.getMesaId())
                .restaurante_id(e.getRestauranteId())
                .numero(e.getNumero())
                .capacidad(e.getCapacidad())
                .comensales(e.getComensales())
                .estado(e.getEstado())
                .build();
    }

    public MesaJpaEntity toEntity(Mesa d) {
        if (d == null) return null;
        MesaJpaEntity e = new MesaJpaEntity();
        e.setMesaId(d.getMesa_id());
        e.setRestauranteId(d.getRestaurante_id());
        updateEntity(d, e);
        return e;
    }

    public void updateEntity(Mesa domain, MesaJpaEntity entity) {
        if (domain == null || entity == null) return;
        entity.setNumero(domain.getNumero());
        entity.setCapacidad(domain.getCapacidad());
        entity.setComensales(domain.getComensales());
        entity.setEstado(domain.getEstado());
    }
}
