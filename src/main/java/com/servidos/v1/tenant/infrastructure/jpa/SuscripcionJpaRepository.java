package com.servidos.v1.tenant.infrastructure.jpa;

import com.servidos.v1.tenant.application.suscripcion.SuscripcionConPlan;
import com.servidos.v1.tenant.domain.Suscripcion.EstadoSuscripcion;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

/**
 * Hay dos nociones de "suscripción" y no son intercambiables:
 * <ul>
 *   <li><b>Actual</b>: la de mayor id que ya empezó ({@code fecha_inicio <= hoy}). Decide el
 *       acceso y es la que se muestra. Una renovación anticipada queda <i>programada</i>
 *       (empieza en el futuro) y no la reemplaza hasta su fecha.</li>
 *   <li><b>Última</b>: la de mayor id, haya empezado o no. Sirve para encadenar renovaciones.</li>
 * </ul>
 * Los ids son IDENTITY monótonos, así que mayor id = creada después. Se usa el id y no
 * {@code created_at} para que el desempate sea único.
 */
@Repository
public interface SuscripcionJpaRepository extends JpaRepository<SuscripcionJpaEntity, Long> {

    /** Última suscripción creada, haya empezado o no (base para encadenar una renovación). */
    Optional<SuscripcionJpaEntity> findTopByRestauranteIdOrderByCreatedAtDescSuscripcionIdDesc(Long restauranteId);

    /** Suscripción actual: la última que ya empezó. */
    Optional<SuscripcionJpaEntity> findTopByRestauranteIdAndFechaInicioLessThanEqualOrderBySuscripcionIdDesc(
            Long restauranteId, LocalDate hoy);

    /** Renovaciones programadas (todavía no empezaron) en el estado dado. */
    List<SuscripcionJpaEntity> findByRestauranteIdAndEstadoAndFechaInicioAfter(
            Long restauranteId, EstadoSuscripcion estado, LocalDate hoy);

    /**
     * Suscripción actual del restaurante con el nombre del plan, en una sola consulta.
     * Mismo criterio que {@code findTopByRestauranteIdAndFechaInicioLessThanEqual...}:
     * MAX(suscripcionId) entre las que ya empezaron, así el JOIN es 1:1 garantizado.
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
                WHERE s2.restauranteId = :restauranteId
                  AND s2.fechaInicio <= :hoy)
            """)
    List<SuscripcionConPlan> listarActualConNombrePlan(Long restauranteId, LocalDate hoy);
}
