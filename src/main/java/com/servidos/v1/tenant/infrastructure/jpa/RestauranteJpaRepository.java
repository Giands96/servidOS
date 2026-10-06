package com.servidos.v1.tenant.infrastructure.jpa;

import com.servidos.v1.tenant.application.restaurante.RestauranteConSuscripcion;
import com.servidos.v1.tenant.domain.EstadoRestaurante;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;
import java.util.Optional;

@Repository
public interface RestauranteJpaRepository extends JpaRepository<RestauranteJpaEntity, Long> {
    boolean existsBySlug(String slug);
    Optional<RestauranteJpaEntity> findBySlug(String slug);

    /**
     * Solo el estado, para el filtro de suscripción que corre en cada escritura y no
     * necesita el resto de la fila. Derived query: evita traer la entity entera.
     */
    boolean existsByRestauranteIdAndEstado(Long restauranteId, EstadoRestaurante estado);

    /**
     * Dashboard de plataforma: una fila por restaurante con su suscripción actual
     * y el nombre del plan, en UNA sola consulta (2 JOIN, sin N+1).
     * <p>
     * "Actual" = suscripción de mayor id (los ids son IDENTITY monótonos, así que
     * es la última creada — mismo resultado que el
     * {@code findTopBy...OrderByCreatedAtDescSuscripcionIdDesc} de {@code ObtenerRestauranteUseCase}).
     * Se usa el id y no {@code MAX(createdAt)} a propósito: el id es único, así el
     * JOIN es garantizado 1:1 por restaurante y la paginación nunca duplica ni
     * saltea filas (un empate en {@code createdAt} sí duplicaría la fila).
     * <p>
     * El {@code countQuery} cuenta solo restaurantes: sin él, Spring derivaría el
     * conteo del SELECT con JOINs y {@code totalElements} saldría mal.
     */
    @Query(value = """
            SELECT new com.servidos.v1.tenant.application.restaurante.RestauranteConSuscripcion(
                r.restauranteId, r.slug, r.nombre, r.direccion, r.estado,
                s.suscripcionId, s.planId, p.nombrePlan, s.estado, s.fechaInicio, s.fechaFin)
            FROM RestauranteJpaEntity r
            LEFT JOIN SuscripcionJpaEntity s ON s.restauranteId = r.restauranteId
                AND s.suscripcionId = (SELECT MAX(s2.suscripcionId) FROM SuscripcionJpaEntity s2
                    WHERE s2.restauranteId = r.restauranteId)
            LEFT JOIN PlanJpaEntity p ON p.planId = s.planId
            """,
            countQuery = "SELECT COUNT(r) FROM RestauranteJpaEntity r")
    Page<RestauranteConSuscripcion> listarConSuscripcionActual(Pageable pageable);
}
