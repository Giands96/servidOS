package com.servidos.v1.catalog.infrastructure.mapper;

import com.servidos.v1.catalog.domain.Categoria;
import com.servidos.v1.catalog.infrastructure.jpa.CategoriaJpaEntity;
import org.springframework.stereotype.Component;

@Component
public class CategoriaMapper {
    //* MAPEAR ENTIDAD A DOMAIN
    public Categoria toDomain(CategoriaJpaEntity entity) {

        if(entity == null) {
            return null;
        }

        return Categoria.builder()
                .categoria_id(entity.getCategoriaId())
                .restaurante_id(entity.getRestauranteId())
                .nombre(entity.getNombre())
                .estado(entity.getEstado())
                .created_at(entity.getCreatedAt())
                .updated_at(entity.getUpdatedAt())
                .build();
    }

    //* MAPEAR DOMAIN A ENTIDAD
    public CategoriaJpaEntity toEntity(Categoria domain) {
        if(domain == null) { return null; }

        CategoriaJpaEntity categoriaJpaEntity = new CategoriaJpaEntity();
        categoriaJpaEntity.setCategoriaId(domain.getCategoria_id());
        categoriaJpaEntity.setRestauranteId(domain.getRestaurante_id());
        categoriaJpaEntity.setNombre(domain.getNombre());
        categoriaJpaEntity.setEstado(domain.getEstado());
        return categoriaJpaEntity;
    }

    //* ACTUALIZAR ENTIDAD CON DATOS DEL DOMINIO
    public void updateEntity(Categoria domain, CategoriaJpaEntity entity) {
        if(domain == null || entity == null) { return; }
        entity.setNombre(domain.getNombre());
        entity.setEstado(domain.getEstado());
    }


}
