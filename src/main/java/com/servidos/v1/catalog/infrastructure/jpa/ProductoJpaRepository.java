package com.servidos.v1.catalog.infrastructure.jpa;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

@Repository
public interface ProductoJpaRepository extends JpaRepository<ProductoJpaEntity, Long> {

    boolean existsByProductoIdAndRestauranteId(Long productoId, Long restauranteId);

    Optional<ProductoJpaEntity> findByProductoIdAndRestauranteId(Long productoId, Long restauranteId);

    List<ProductoJpaEntity> findByRestauranteId(Long restauranteId);

    List<ProductoJpaEntity> findByRestauranteIdAndCategoriaId(Long restauranteId, Long categoriaId);

    Optional<ProductoJpaEntity> findByProductoIdAndPrecioAndRestauranteId(Long productoId, BigDecimal precio, Long restauranteId);

    boolean existsByNombreAndRestauranteId(String nombre, Long restauranteId);
}
