package com.servidos.v1.catalog.application.producto;

import com.servidos.v1.catalog.domain.Producto;
import com.servidos.v1.catalog.infrastructure.jpa.CategoriaJpaRepository;
import com.servidos.v1.catalog.infrastructure.jpa.ProductoJpaEntity;
import com.servidos.v1.catalog.infrastructure.jpa.ProductoJpaRepository;
import com.servidos.v1.catalog.infrastructure.mapper.ProductoMapper;
import com.servidos.v1.shared.exception.BusinessException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

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
     * Ver {@link CrearProductoCommand}.
     */
    @Transactional
    public Producto ejecutar(CrearProductoCommand cmd, Long restauranteId) {
        //* Validar tenant - nunca confiar en el frontend
        if (restauranteId == null) {
            throw new BusinessException("Restaurante no identificado");
        }
        validar(cmd, restauranteId);

        //* Crear el dominio Producto (POJO puro, sin JPA)
        Producto domain = Producto.crear(
                restauranteId,
                cmd.categoriaId(),
                cmd.nombre().trim(),
                cmd.descripcion(),
                cmd.imagenUrl(),
                cmd.precio(),
                cmd.estado(),
                cmd.tiempoPreparacion());


        // 4. Mapear a JPA Entity y persistir
        ProductoJpaEntity entity = productoMapper.toEntity(domain);
        ProductoJpaEntity saved = productoRepository.save(entity);

        // 5. Mapear de vuelta a dominio para retornar (el controller mapeará a DTO)
        return productoMapper.toDomain(saved);
    }

    private void validar(CrearProductoCommand cmd, Long restauranteId) {
        if(cmd.nombre() == null || cmd.nombre().trim().isEmpty()) {
            throw new BusinessException("El nombre del producto es obligatorio");
        }
        if(cmd.estado() == null) {
            throw new BusinessException("El estado del producto es obligatorio");
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
