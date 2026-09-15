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

@Service
@RequiredArgsConstructor
public class ActualizarProductoUseCase {

    private final ProductoJpaRepository productoRepository;
    private final CategoriaJpaRepository categoriaRepository;
    private final ProductoMapper productoMapper;

    @Transactional
    public Producto actualizar(ActualizarProductoCommand cmd, Long restauranteId) {
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


    private void validar(ActualizarProductoCommand cmd, Long restauranteId) {
        //* Validar si existe el producto
        if (cmd.categoriaId() != null) {
            if (!categoriaRepository.existsByCategoriaIdAndRestauranteId(cmd.categoriaId(), restauranteId))
                throw new BusinessException("Categoría no encontrada");
        }
    }



}
