package com.servidos.v1.catalog.infrastructure.jpa;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;


@Repository
public interface CategoriaJpaRepository extends JpaRepository<CategoriaJpaEntity, Long> {

    Page<CategoriaJpaEntity> findByRestauranteId(Long restauranteId, Pageable pageable);

    boolean existsByNombreAndRestauranteId(String nombre, Long restauranteId);

    boolean existsByCategoriaIdAndRestauranteId(Long categoriaId, Long restauranteId);
}
