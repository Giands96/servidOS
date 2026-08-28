package com.servidos.v1.catalog.infrastructure.jpa;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface CategoriaJpaRepository extends JpaRepository<CategoriaJpaEntity, Long> {

    Optional<CategoriaJpaEntity> findByCategoriaIdAndRestauranteId(Long categoriaId, Long restauranteId);

    List<CategoriaJpaEntity> findByRestauranteId(Long restauranteId);

    boolean existsByNombreAndRestauranteId(String nombre, Long restauranteId);

    boolean existsByCategoriaIdAndRestauranteId(Long categoriaId, Long restauranteId);
}
