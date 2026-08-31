package com.servidos.v1.tenant.infrastructure.mapper;

import com.servidos.v1.tenant.domain.Restaurante;
import com.servidos.v1.tenant.infrastructure.jpa.RestauranteJpaEntity;
import org.springframework.stereotype.Component;

@Component
public class RestauranteMapper {
    public Restaurante toDomain(RestauranteJpaEntity e) {
        if (e == null) return null;
        return Restaurante.builder().restaurante_id(e.getRestauranteId()).slug(e.getSlug()).nombre(e.getNombre()).direccion(e.getDireccion()).estado(e.getEstado()).created_at(e.getCreatedAt()).updated_at(e.getUpdatedAt()).build();
    }
    public RestauranteJpaEntity toEntity(Restaurante d) {
        if (d == null) return null;
        RestauranteJpaEntity e = new RestauranteJpaEntity();
        e.setRestauranteId(d.getRestaurante_id()); e.setSlug(d.getSlug()); e.setNombre(d.getNombre()); e.setDireccion(d.getDireccion()); e.setEstado(d.getEstado()); return e;
    }
    public void updateEntity(Restaurante domain, RestauranteJpaEntity entity) {
        if (domain == null || entity == null) return;
        entity.setNombre(domain.getNombre()); entity.setDireccion(domain.getDireccion()); entity.setEstado(domain.getEstado());
    }
}
