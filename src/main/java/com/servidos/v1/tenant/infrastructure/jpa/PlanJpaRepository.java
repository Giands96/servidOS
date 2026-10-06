package com.servidos.v1.tenant.infrastructure.jpa;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface PlanJpaRepository extends JpaRepository<PlanJpaEntity, Long> {

    /**
     * Resuelve el plan solo si está ACTIVO. Existe porque las operaciones que cobran
     * (crear restaurante, cambiar plan, renovar) no pueden operar contra un plan
     * retirado: la validación por {@code existsById} aceptaba planes INACTIVOS y
     * devolvía 200 con la suscripción creada.
     */
    Optional<PlanJpaEntity> findByPlanIdAndEstado(Long planId, String estado);

    List<PlanJpaEntity> findByEstadoOrderByPrecioPlanAsc(String estado);
}