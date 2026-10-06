package com.servidos.v1.tenant.infrastructure.jpa;

import com.servidos.v1.tenant.application.suscripcion.SuscripcionConPlan;
import com.servidos.v1.tenant.domain.Suscripcion;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;
import java.util.List;
import java.util.Optional;

@Repository
public interface SuscripcionJpaRepository extends JpaRepository<SuscripcionJpaEntity, Long> {
    Optional<SuscripcionJpaEntity> findTopByRestauranteIdOrderByCreatedAtDescSuscripcionIdDesc(Long restauranteId);

    /**
     * Suscripción actual del restaurante con el nombre del plan, en una sola consulta.
     * "Actual" = suscripción de mayor id (ids IDENTITY monótonos = la última creada),
     * el mismo criterio que {@code findTopBy...OrderByCreatedAtDescSuscripcionIdDesc}.
     * Se usa MAX(suscripcionId) y no MAX(createdAt) para que el JOIN sea 1:1 garantizado.
     */
    @Query("""
            SELECT new com.servidos.v1.tenant.application.suscripcion.SuscripcionConPlan(
                new com.servidos.v1.tenant.domain.Suscripcion(
                    s.suscripcionId, s.restauranteId, s.planId, s.monto, s.moneda,
                    s.estado, s.fechaInicio, s.fechaFin, s.createdAt, s.updatedAt),
                p.nombrePlan)
            FROM SuscripcionJpaEntity s
            JOIN PlanJpaEntity p ON p.planId = s.planId
            WHERE s.suscripcionId = (SELECT MAX(s2.suscripcionId)
                FROM SuscripcionJpaEntity s2
                WHERE s2.restauranteId = :restauranteId)
            """)
    List<SuscripcionConPlan> listarActualConNombrePlan(Long restauranteId);
}