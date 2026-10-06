package com.servidos.v1.catalog.infrastructure.jpa;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface ProductoJpaRepository extends JpaRepository<ProductoJpaEntity, Long> {

    boolean existsByProductoIdAndRestauranteId(Long productoId, Long restauranteId);

    Optional<ProductoJpaEntity> findByProductoIdAndRestauranteId(Long productoId, Long restauranteId);

    Page<ProductoJpaEntity> findByRestauranteId(Long restauranteId, Pageable pageable);

    Page<ProductoJpaEntity> findByRestauranteIdAndCategoriaId(Long restauranteId, Long categoriaId, Pageable pageable);

    boolean existsByNombreAndRestauranteId(String nombre, Long restauranteId);
}
