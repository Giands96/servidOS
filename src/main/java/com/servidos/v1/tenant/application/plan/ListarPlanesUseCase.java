package com.servidos.v1.tenant.application.plan;

import com.servidos.v1.tenant.domain.Plan;
import com.servidos.v1.tenant.infrastructure.jpa.PlanJpaEntity;
import com.servidos.v1.tenant.infrastructure.jpa.PlanJpaRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
public class ListarPlanesUseCase {

    private final PlanJpaRepository planRepository;

    /**
     * Solo planes ACTIVO, ordenados de menor a mayor precio.
     *
     * <p>Filtrar por estado en la consulta y no en memoria evita exponer planes
     * retirados. Es el mismo criterio que aplican las operaciones que cobran
     * ({@code findByPlanIdAndEstado}), así que lo que se lista y lo que se puede
     * contratar no pueden divergir.
     */
    @Transactional(readOnly = true)
    public List<Plan> listar() {
        return planRepository.findByEstadoOrderByPrecioPlanAsc(Plan.EstadoPlan.ACTIVO.name())
                .stream()
                .map(this::toDomain)
                .toList();
    }

    private Plan toDomain(PlanJpaEntity e) {
        return new Plan(e.getPlanId(), e.getNombrePlan(), e.getPrecioPlan(), e.getDescripcion(),
                Plan.EstadoPlan.valueOf(e.getEstado()));
    }
}