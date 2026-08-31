package com.servidos.v1.tenant.infrastructure.jpa;

import com.servidos.v1.tenant.domain.Suscripcion.EstadoSuscripcion;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import java.util.List;
import java.util.Optional;

@Repository
public interface SuscripcionJpaRepository extends JpaRepository<SuscripcionJpaEntity, Long> {
    Optional<SuscripcionJpaEntity> findTopByRestauranteIdOrderByCreatedAtDesc(Long restauranteId);
    List<SuscripcionJpaEntity> findByRestauranteId(Long restauranteId);
    boolean existsByRestauranteIdAndEstado(Long restauranteId, EstadoSuscripcion estado);
}
