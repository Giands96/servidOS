package com.servidos.v1.tenant.application;

import com.servidos.v1.tenant.application.plan.ListarPlanesUseCase;
import com.servidos.v1.tenant.domain.Plan;
import com.servidos.v1.tenant.infrastructure.jpa.PlanJpaEntity;
import com.servidos.v1.tenant.infrastructure.jpa.PlanJpaRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ListarPlanesUseCaseTest {

    @Mock PlanJpaRepository planRepository;

    @InjectMocks ListarPlanesUseCase useCase;

    private PlanJpaEntity plan(Long id, String nombre, String precio) {
        var p = new PlanJpaEntity();
        p.setPlanId(id);
        p.setNombrePlan(nombre);
        p.setPrecioPlan(new BigDecimal(precio));
        p.setDescripcion("desc " + nombre);
        p.setEstado(Plan.EstadoPlan.ACTIVO.name());
        return p;
    }

    @Test
    void mapeaIdNombrePrecioDescripcionYEstado() {
        when(planRepository.findByEstadoOrderByPrecioPlanAsc(Plan.EstadoPlan.ACTIVO.name()))
                .thenReturn(List.of(plan(1L, "Estándar", "34.90")));

        var planes = useCase.listar();

        assertEquals(1, planes.size());
        var p = planes.get(0);
        assertEquals(1L, p.getPlan_id());
        assertEquals("Estándar", p.getNombre_plan());
        assertEquals(0, new BigDecimal("34.90").compareTo(p.getPrecio_plan()));
        assertEquals("desc Estándar", p.getDescripcion());
        assertEquals(Plan.EstadoPlan.ACTIVO, p.getEstado());
    }

    /**
     * El filtro por estado va en la consulta, no en memoria: si se filtrara después,
     * un plan INACTIVO igual se cargaría desde la base.
     */
    @Test
    void filtraPorActivoEnLaConsulta() {
        when(planRepository.findByEstadoOrderByPrecioPlanAsc(Plan.EstadoPlan.ACTIVO.name()))
                .thenReturn(List.of());

        var planes = useCase.listar();

        assertTrue(planes.isEmpty());
        verify(planRepository).findByEstadoOrderByPrecioPlanAsc(Plan.EstadoPlan.ACTIVO.name());
    }
}