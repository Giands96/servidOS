package com.servidos.v1.identity.infrastructure;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface RolRestauranteJpaRepository extends JpaRepository<RolRestauranteJpaEntity, Long> {
    Optional<RolRestauranteJpaEntity> findByNombre(String nombre);
}
