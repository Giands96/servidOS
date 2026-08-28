package com.servidos.v1.catalog.application;

import com.servidos.v1.catalog.domain.EstadoProducto;
import com.servidos.v1.catalog.domain.Producto;
import com.servidos.v1.catalog.infrastructure.jpa.CategoriaJpaRepository;
import com.servidos.v1.catalog.infrastructure.jpa.ProductoJpaEntity;
import com.servidos.v1.catalog.infrastructure.jpa.ProductoJpaRepository;
import com.servidos.v1.catalog.infrastructure.mapper.ProductoMapper;
import com.servidos.v1.shared.exception.BusinessException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;

/**
 * Template para todos los UseCases del proyecto.
 * Patrón: Controller -> UseCase (application) -> Mapper -> JpaRepository (infrastructure)
 * El dominio (Producto) nunca toca JPA. Solo POJO puro.
 */
@Service
@RequiredArgsConstructor
public class CrearProductoUseCase {

    private final ProductoJpaRepository productoRepository;
    private final CategoriaJpaRepository categoriaRepository;
    private final ProductoMapper productoMapper;

    /**
     * Command = lo que viene del controller (DTO de entrada).
     * NUNCA incluye restaurante_id -> se obtiene del TenantContext / JWT.
     * Esto evita Broken Access Control (PROJECT_CONTEXT.md:71).
     */
    public record Command(
            String nombre,
            String descripcion,
            Long categoriaId,
            BigDecimal precio,
            Integer tiempoPreparacion,
            EstadoProducto estado,
            String imagenUrl
    ) {}

    @Transactional
    public Producto ejecutar(Command cmd, Long restauranteId) {
        // 1. Validar tenant - nunca confiar en el frontend
        if (restauranteId == null) {
            throw new BusinessException("Restaurante no identificado");
        }

        // 2. Validaciones de negocio (invariantes del dominio)
        validar(cmd, restauranteId);

        // 3. Construir POJO de dominio (sin @Entity, sin JPA)
        Producto domain = new Producto();
        domain.setRestaurante_id(restauranteId);
        domain.setCategoria_id(cmd.categoriaId());
        domain.setNombre(cmd.nombre().trim());
        domain.setDescripcion(cmd.descripcion());
        domain.setImagen_url(cmd.imagenUrl());
        domain.setPrecio(cmd.precio());
        domain.setEstado(cmd.estado() != null ? cmd.estado() : EstadoProducto.DISPONIBLE);
        domain.setTiempo_preparacion(cmd.tiempoPreparacion());

        // 4. Mapear a JPA Entity y persistir
        ProductoJpaEntity entity = productoMapper.toEntity(domain);
        ProductoJpaEntity saved = productoRepository.save(entity);

        // 5. Mapear de vuelta a dominio para retornar (el controller mapeará a DTO)
        return productoMapper.toDomain(saved);
    }

    private void validar(Command cmd, Long restauranteId) {
        if (cmd.nombre() == null || cmd.nombre().isBlank()) {
            throw new BusinessException("El nombre es obligatorio");
        }
        if (cmd.nombre().trim().length() > 150) {
            throw new BusinessException("El nombre no puede exceder 150 caracteres");
        }
        if (cmd.precio() == null || cmd.precio().compareTo(BigDecimal.ZERO) <= 0) {
            throw new BusinessException("El precio debe ser mayor a 0");
        }
        if (cmd.tiempoPreparacion() != null && cmd.tiempoPreparacion() < 0) {
            throw new BusinessException("El tiempo de preparación no puede ser negativo");
        }
        // Unicidad por tenant (aislamiento multi-tenancy)
        if (productoRepository.existsByNombreAndRestauranteId(cmd.nombre().trim(), restauranteId)) {
            throw new BusinessException("Ya existe un producto con ese nombre en este restaurante");
        }
        // Si mandan categoria, debe existir y pertenecer al mismo restaurante (aislamiento tenant)
        if (cmd.categoriaId() != null) {
            boolean categoriaValida = categoriaRepository.existsByCategoriaIdAndRestauranteId(cmd.categoriaId(), restauranteId);
            if (!categoriaValida) {
                throw new BusinessException("La categoría no existe o no pertenece a este restaurante");
            }
        }
    }
}
