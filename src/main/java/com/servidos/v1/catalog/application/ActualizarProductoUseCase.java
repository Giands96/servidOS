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

@Service
@RequiredArgsConstructor
public class ActualizarProductoUseCase {

    private final ProductoJpaRepository productoRepository;
    private final CategoriaJpaRepository categoriaRepository;
    private final ProductoMapper productoMapper;

    public record Command(
            Long productoId,
            String nombre,
            String descripcion,
            Long categoriaId,
            String imagenUrl,
            BigDecimal precio,
            Integer tiempoPreparacion,
            EstadoProducto estado
    ) {}

    @Transactional
    public Producto actualizar(Command cmd, Long restauranteId) {
        //* Validar si existe el restaurante
        if (restauranteId == null) {
            throw new BusinessException("Restaurante no identificado");
        }
        validar(cmd, restauranteId);

        ProductoJpaEntity productoEntity = productoRepository.findByProductoIdAndRestauranteId(cmd.productoId(), restauranteId)
                .orElseThrow(() -> new BusinessException("Producto no encontrado"));

        Producto actualizado = Producto.actualizar(
                productoEntity.getProductoId(),
                restauranteId,
                cmd.categoriaId(),
                cmd.nombre(),
                cmd.descripcion(),
                cmd.imagenUrl(),
                cmd.precio(),
                cmd.estado(),
                cmd.tiempoPreparacion()

        );

        productoMapper.updateEntity(actualizado, productoEntity);
        productoRepository.save(productoEntity);

        return productoMapper.toDomain(productoEntity);
    }


    private void validar(Command cmd, Long restauranteId) {
        //* Validar si existe el producto
        if (cmd.categoriaId() != null) {
            if (!categoriaRepository.existsByCategoriaIdAndRestauranteId(cmd.categoriaId(), restauranteId))
                throw new BusinessException("Categoría no encontrada");
        }
    }



}
