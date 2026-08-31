package com.servidos.v1.tenant.infrastructure.mapper;

import com.servidos.v1.tenant.domain.Suscripcion;
import com.servidos.v1.tenant.infrastructure.jpa.SuscripcionJpaEntity;
import org.springframework.stereotype.Component;

@Component
public class SuscripcionMapper {
    public Suscripcion toDomain(SuscripcionJpaEntity e) {
        if (e == null) return null;
        return Suscripcion.builder().suscripcion_id(e.getSuscripcionId()).restaurante_id(e.getRestauranteId()).plan_id(e.getPlanId()).estado(e.getEstado()).fecha_inicio(e.getFechaInicio()).fecha_fin(e.getFechaFin()).created_at(e.getCreatedAt()).updated_at(e.getUpdatedAt()).build();
    }
    public SuscripcionJpaEntity toEntity(Suscripcion d) {
        if (d == null) return null;
        SuscripcionJpaEntity e = new SuscripcionJpaEntity();
        e.setSuscripcionId(d.getSuscripcion_id()); e.setRestauranteId(d.getRestaurante_id()); e.setPlanId(d.getPlan_id()); e.setEstado(d.getEstado()); e.setFechaInicio(d.getFecha_inicio()); e.setFechaFin(d.getFecha_fin()); return e;
    }
}
