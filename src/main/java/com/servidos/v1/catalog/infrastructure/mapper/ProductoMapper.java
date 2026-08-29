package com.servidos.v1.catalog.infrastructure.mapper;

import com.servidos.v1.catalog.domain.Producto;
import com.servidos.v1.catalog.infrastructure.jpa.ProductoJpaEntity;
import org.springframework.stereotype.Component;

@Component
public class ProductoMapper {

    //* MAPEAR ENTIDAD A DOMAIN
    public Producto toDomain(ProductoJpaEntity entity) {
        if (entity == null) {
            return null;
        }
        Producto domain = new Producto();
        domain.setProducto_id(entity.getProductoId());
        domain.setRestaurante_id(entity.getRestauranteId());
        domain.setCategoria_id(entity.getCategoriaId());
        domain.setNombre(entity.getNombre());
        domain.setDescripcion(entity.getDescripcion());
        domain.setImagen_url(entity.getImagenUrl());
        domain.setPrecio(entity.getPrecio());
        domain.setEstado(entity.getEstado());
        domain.setTiempo_preparacion(entity.getTiempoPreparacion());
        domain.setCreated_at(entity.getCreatedAt());
        domain.setUpdated_at(entity.getUpdatedAt());
        return domain;
    }

    //* MAPEAR DOMAIN A ENTIDAD
    public ProductoJpaEntity toEntity(Producto domain) {
        if (domain == null) {
            return null;
        }
        ProductoJpaEntity entity = new ProductoJpaEntity();
        entity.setProductoId(domain.getProducto_id());
        entity.setRestauranteId(domain.getRestaurante_id());
        entity.setCategoriaId(domain.getCategoria_id());
        entity.setNombre(domain.getNombre());
        entity.setDescripcion(domain.getDescripcion());
        entity.setImagenUrl(domain.getImagen_url());
        entity.setPrecio(domain.getPrecio());
        entity.setEstado(domain.getEstado());
        entity.setTiempoPreparacion(domain.getTiempo_preparacion());
        // created_at / updated_at los maneja @PrePersist/@PreUpdate en la entity
        // no los seteamos desde el dominio al crear; al leer sí se mapean vía toDomain
        return entity;
    }

    //* ACTUALIZAR ENTIDAD EXISTENTE CON DATOS DEL DOMAIN
    public void updateEntity(Producto domain, ProductoJpaEntity entity) {
        if (domain == null || entity == null) {
            return;
        }
        entity.setCategoriaId(domain.getCategoria_id());
        entity.setNombre(domain.getNombre());
        entity.setDescripcion(domain.getDescripcion());
        entity.setImagenUrl(domain.getImagen_url());
        entity.setPrecio(domain.getPrecio());
        entity.setEstado(domain.getEstado());
        entity.setTiempoPreparacion(domain.getTiempo_preparacion());
    }
}
