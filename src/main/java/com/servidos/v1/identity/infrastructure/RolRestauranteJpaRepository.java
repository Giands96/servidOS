package com.servidos.v1.identity.infrastructure;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import org.springframework.stereotype.Repository;

@Repository
public interface RolRestauranteJpaRepository extends JpaRepository<RolRestauranteJpaEntity, Long> {
    Optional<RolRestauranteJpaEntity> findByNombre(String nombre);
}
