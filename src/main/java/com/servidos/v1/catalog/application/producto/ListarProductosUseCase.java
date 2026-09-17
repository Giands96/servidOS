package com.servidos.v1.catalog.application.producto;

import com.servidos.v1.catalog.domain.Producto;
import com.servidos.v1.catalog.infrastructure.jpa.ProductoJpaRepository;
import com.servidos.v1.catalog.infrastructure.mapper.ProductoMapper;
import com.servidos.v1.shared.exception.BusinessException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

@Service
@RequiredArgsConstructor
public class ListarProductosUseCase {

    private final ProductoJpaRepository productoRepository;
    private final ProductoMapper productoMapper;

    @Transactional(readOnly = true)
    public Page<Producto> listar(Long restauranteId, Long categoriaId, Pageable pageable) {
        if (restauranteId == null) {
            throw new BusinessException("Restaurante no identificado");
        }
        var page = categoriaId != null
                ? productoRepository.findByRestauranteIdAndCategoriaId(restauranteId, categoriaId, pageable)
                : productoRepository.findByRestauranteId(restauranteId, pageable);
        return page.map(productoMapper::toDomain);
    }

    @Transactional(readOnly = true)
    public Producto obtener(Long restauranteId, Long productoId) {
        if (restauranteId == null) {
            throw new BusinessException("Restaurante no identificado");
        }
        return productoRepository.findByProductoIdAndRestauranteId(productoId, restauranteId)
                .map(productoMapper::toDomain)
                .orElseThrow(() -> new BusinessException("Producto no encontrado"));
    }
}
