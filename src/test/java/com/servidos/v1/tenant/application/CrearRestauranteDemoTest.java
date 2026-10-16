package com.servidos.v1.tenant.application;

import com.servidos.v1.identity.application.usuario.CrearAdministradorCommand;
import com.servidos.v1.identity.application.usuario.CrearAdministradorRestauranteUseCase;
import com.servidos.v1.shared.event.EventPublisher;
import com.servidos.v1.shared.exception.BusinessException;
import com.servidos.v1.tenant.application.restaurante.CrearRestauranteCommand;
import com.servidos.v1.tenant.application.restaurante.CrearRestauranteUseCase;
import com.servidos.v1.tenant.domain.Plan;
import com.servidos.v1.tenant.infrastructure.jpa.PlanJpaEntity;
import com.servidos.v1.tenant.infrastructure.jpa.PlanJpaRepository;
import com.servidos.v1.tenant.infrastructure.jpa.RestauranteJpaEntity;
import com.servidos.v1.tenant.infrastructure.jpa.RestauranteJpaRepository;
import com.servidos.v1.tenant.infrastructure.jpa.SuscripcionJpaEntity;
import com.servidos.v1.tenant.infrastructure.jpa.SuscripcionJpaRepository;
import com.servidos.v1.tenant.infrastructure.mapper.RestauranteMapper;
import com.servidos.v1.tenant.infrastructure.mapper.SuscripcionMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.math.BigDecimal;
import java.time.LocalDate;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class CrearRestauranteDemoTest {

    @Mock RestauranteJpaRepository restauranteRepository;
    @Mock SuscripcionJpaRepository suscripcionRepository;
    @Mock PlanJpaRepository planRepository;
    @Mock RestauranteMapper restauranteMapper;
    @Mock SuscripcionMapper suscripcionMapper;
    @Mock EventPublisher eventPublisher;
    @Mock CrearAdministradorRestauranteUseCase crearAdministrador;

    @InjectMocks CrearRestauranteUseCase useCase;

    private static final CrearAdministradorCommand DUENO =
            new CrearAdministradorCommand("Rosa", "Quispe", "rosa@chifa.pe", "secreto123");

    private void baseStubs() {
        when(restauranteRepository.existsBySlug("demo")).thenReturn(false);
        var plan = new PlanJpaEntity();
        plan.setPlanId(9L);
        plan.setNombrePlan("Estándar");
        plan.setPrecioPlan(new BigDecimal("34.90"));
        plan.setEstado(Plan.EstadoPlan.ACTIVO.name());
        when(planRepository.findByPlanIdAndEstado(9L, Plan.EstadoPlan.ACTIVO.name()))
                .thenReturn(java.util.Optional.of(plan));
        var savedR = new RestauranteJpaEntity();
        savedR.setRestauranteId(5L);
        savedR.setSlug("demo");
        when(restauranteRepository.save(any())).thenReturn(savedR);
        when(restauranteMapper.toEntity(any())).thenCallRealMethod();
        when(suscripcionMapper.toEntity(any())).thenCallRealMethod();
        when(suscripcionRepository.save(any())).thenAnswer(i -> i.getArgument(0));
    }

    @Test
    void demoUsaDiasVariables() {
        baseStubs();
        useCase.ejecutar(new CrearRestauranteCommand("demo", "Demo", null, 9L, true, 10, DUENO));
        var captor = ArgumentCaptor.forClass(SuscripcionJpaEntity.class);
        org.mockito.Mockito.verify(suscripcionRepository).save(captor.capture());
        assertEquals(LocalDate.now().plusDays(9), captor.getValue().getFechaFin());
        assertEquals(0, BigDecimal.ZERO.compareTo(captor.getValue().getMonto()), "una demo no se cobra");
    }

    @Test
    void sinDemoUsa30Dias() {
        baseStubs();
        useCase.ejecutar(new CrearRestauranteCommand("demo", "Demo", null, 9L, false, null, DUENO));
        var captor = ArgumentCaptor.forClass(SuscripcionJpaEntity.class);
        org.mockito.Mockito.verify(suscripcionRepository).save(captor.capture());
        assertEquals(LocalDate.now().plusMonths(1).minusDays(1), captor.getValue().getFechaFin());
        assertEquals(0, new BigDecimal("34.90").compareTo(captor.getValue().getMonto()));
    }

    @Test
    void demoSinDiasFalla() {
        baseStubs();
        assertThrows(BusinessException.class, () ->
                useCase.ejecutar(new CrearRestauranteCommand("demo", "Demo", null, 9L, true, null, DUENO)));
    }

    @Test
    void demoDiasFueraDeRangoFalla() {
        baseStubs();
        assertThrows(BusinessException.class, () ->
                useCase.ejecutar(new CrearRestauranteCommand("demo", "Demo", null, 9L, true, 45, DUENO)));
    }

    /** Se crea el dueño del restaurante recién guardado, con los datos del request. */
    @Test
    void creaElDuenoDelRestaurante() {
        baseStubs();
        useCase.ejecutar(new CrearRestauranteCommand("demo", "Demo", null, 9L, false, null, DUENO));
        org.mockito.Mockito.verify(crearAdministrador).validar(DUENO);
        org.mockito.Mockito.verify(crearAdministrador).ejecutar(5L, DUENO);
    }

    /** Con datos inválidos (slug, plan, demo) no se valida ni se crea el dueño. */
    @Test
    void demoInvalidaNoTocaAlDueno() {
        baseStubs();
        assertThrows(BusinessException.class, () ->
                useCase.ejecutar(new CrearRestauranteCommand("demo", "Demo", null, 9L, true, 45, DUENO)));
        org.mockito.Mockito.verifyNoInteractions(crearAdministrador);
        org.mockito.Mockito.verify(restauranteRepository, org.mockito.Mockito.never()).save(any());
    }
}
