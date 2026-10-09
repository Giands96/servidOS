package com.servidos.v1.tenant.application;

import com.servidos.v1.shared.event.EventPublisher;
import com.servidos.v1.shared.exception.BusinessException;
import com.servidos.v1.tenant.application.suscripcion.CambiarPlanCommand;
import com.servidos.v1.tenant.application.suscripcion.CambiarPlanUseCase;
import com.servidos.v1.tenant.domain.Plan;
import com.servidos.v1.tenant.domain.Suscripcion.EstadoSuscripcion;
import com.servidos.v1.tenant.infrastructure.jpa.PlanJpaEntity;
import com.servidos.v1.tenant.infrastructure.jpa.PlanJpaRepository;
import com.servidos.v1.tenant.infrastructure.jpa.RestauranteJpaRepository;
import com.servidos.v1.tenant.infrastructure.jpa.SuscripcionJpaEntity;
import com.servidos.v1.tenant.infrastructure.jpa.SuscripcionJpaRepository;
import com.servidos.v1.tenant.infrastructure.mapper.SuscripcionMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** Cambio de plan: operación de plataforma (SUPERADMIN), sin contraseña del tenant. */
@ExtendWith(MockitoExtension.class)
class CambiarPlanHardenTest {

    @Mock SuscripcionJpaRepository suscripcionRepository;
    @Mock PlanJpaRepository planRepository;
    @Mock RestauranteJpaRepository restauranteRepository;
    @Mock SuscripcionMapper suscripcionMapper;
    @Mock EventPublisher eventPublisher;

    @InjectMocks CambiarPlanUseCase useCase;

    private void dadoPlanActivo() {
        when(restauranteRepository.existsById(7L)).thenReturn(true);
        var nuevoPlan = new PlanJpaEntity();
        nuevoPlan.setPlanId(4L);
        nuevoPlan.setNombrePlan("Estándar");
        nuevoPlan.setPrecioPlan(new BigDecimal("34.90"));
        nuevoPlan.setEstado(Plan.EstadoPlan.ACTIVO.name());
        when(planRepository.findByPlanIdAndEstado(4L, Plan.EstadoPlan.ACTIVO.name()))
                .thenReturn(Optional.of(nuevoPlan));
    }

    @Test
    void creaNuevaDeUnMesAlPrecioDelPlan() {
        dadoPlanActivo();
        var actual = new SuscripcionJpaEntity();
        actual.setEstado(EstadoSuscripcion.ACTIVA);
        actual.setPlanId(3L);
        actual.setFechaFin(LocalDate.now().plusDays(5));
        when(suscripcionRepository.findTopByRestauranteIdAndFechaInicioLessThanEqualOrderBySuscripcionIdDesc(eq(7L), any()))
                .thenReturn(Optional.of(actual));
        when(suscripcionRepository.save(any())).thenAnswer(i -> i.getArgument(0));
        when(suscripcionMapper.toEntity(any())).thenCallRealMethod();
        when(suscripcionMapper.toDomain(any())).thenCallRealMethod();

        var nueva = useCase.ejecutar(new CambiarPlanCommand(7L, 4L));

        assertEquals(4L, nueva.suscripcion().getPlan_id());
        assertEquals(LocalDate.now(), nueva.suscripcion().getFecha_inicio());
        assertEquals(LocalDate.now().plusMonths(1), nueva.suscripcion().getFecha_fin());
        assertEquals("Estándar", nueva.nombrePlan());
        assertEquals(0, new BigDecimal("34.90").compareTo(nueva.suscripcion().getMonto()));
        assertEquals(EstadoSuscripcion.CANCELADA, actual.getEstado());
    }

    @Test
    void sinPlanFalla() {
        assertThrows(BusinessException.class, () -> useCase.ejecutar(new CambiarPlanCommand(7L, null)));
        verify(suscripcionRepository, never()).save(any());
    }

    /** Un plan retirado no se puede contratar: antes existsById lo aceptaba. */
    @Test
    void planInactivoNoSePuedeContratar() {
        when(restauranteRepository.existsById(7L)).thenReturn(true);
        when(planRepository.findByPlanIdAndEstado(4L, Plan.EstadoPlan.ACTIVO.name()))
                .thenReturn(Optional.empty());

        var ex = assertThrows(BusinessException.class, () -> useCase.ejecutar(new CambiarPlanCommand(7L, 4L)));
        assertEquals("El plan no existe o no está activo", ex.getMessage());
        verify(suscripcionRepository, never()).save(any());
    }
}
