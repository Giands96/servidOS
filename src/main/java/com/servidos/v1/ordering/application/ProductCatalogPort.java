package com.servidos.v1.ordering.application;

import java.math.BigDecimal;
import java.util.Optional;

public interface ProductCatalogPort {
    boolean existsByProductoIdAndRestaurante(Long productId, Long restauranteId);

    Optional<BigDecimal> findPrecioByIdAndRestaurante(Long productId, Long restauranteId);
}
