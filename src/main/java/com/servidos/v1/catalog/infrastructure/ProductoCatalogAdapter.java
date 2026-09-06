package com.servidos.v1.catalog.infrastructure;

import com.servidos.v1.catalog.infrastructure.jpa.ProductoJpaEntity;
import com.servidos.v1.ordering.application.ProductCatalogPort;
import com.servidos.v1.catalog.infrastructure.jpa.ProductoJpaRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.Optional;

@Component
@RequiredArgsConstructor
public class ProductoCatalogAdapter implements ProductCatalogPort {
    private final ProductoJpaRepository productoJpaRepository;

    @Override
    public boolean existsByProductoIdAndRestaurante(Long productId, Long restauranteId) {
        return productoJpaRepository.existsByProductoIdAndRestauranteId(productId, restauranteId);
    }

    @Override
    public Optional<BigDecimal> findPrecioByIdAndRestaurante(Long productId, Long restauranteId) {
        return productoJpaRepository.findByProductoIdAndRestauranteId(productId, restauranteId)
                .map(ProductoJpaEntity::getPrecio);
    }
}
