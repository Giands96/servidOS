package com.servidos.v1.tenant.infrastructure.jpa;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import java.util.Optional;

@Repository
public interface RestauranteJpaRepository extends JpaRepository<RestauranteJpaEntity, Long> {
    boolean existsBySlug(String slug);
    Optional<RestauranteJpaEntity> findBySlug(String slug);
}
