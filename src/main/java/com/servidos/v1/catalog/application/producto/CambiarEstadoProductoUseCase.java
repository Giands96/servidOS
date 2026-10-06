package com.servidos.v1.catalog.application.producto;

import com.servidos.v1.catalog.domain.EstadoProducto;
import com.servidos.v1.catalog.domain.Producto;
import com.servidos.v1.catalog.infrastructure.jpa.ProductoJpaEntity;
import com.servidos.v1.catalog.infrastructure.jpa.ProductoJpaRepository;
import com.servidos.v1.catalog.infrastructure.mapper.ProductoMapper;
import com.servidos.v1.shared.exception.BusinessException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class CambiarEstadoProductoUseCase {

    private final ProductoJpaRepository productoRepository;
    private final ProductoMapper productoMapper;

    @Transactional
    public Producto ejecutar(Long productoId, Long restauranteId, EstadoProducto nuevoEstado) {
        if (productoId == null) {
            throw new BusinessException("Producto no identificado");
        }
        if (restauranteId == null) {
            throw new BusinessException("Restaurante no identificado");
        }
        if (nuevoEstado == null) {
            throw new BusinessException("El estado es obligatorio");
        }
        ProductoJpaEntity entity = productoRepository.findByProductoIdAndRestauranteId(productoId, restauranteId)
                .orElseThrow(() -> new BusinessException("Producto no encontrado"));
        entity.setEstado(nuevoEstado);
        return productoMapper.toDomain(productoRepository.save(entity));
    }
}
