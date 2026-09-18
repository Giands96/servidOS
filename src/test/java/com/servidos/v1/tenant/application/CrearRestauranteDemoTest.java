package com.servidos.v1.tenant.application;

import com.servidos.v1.shared.event.EventPublisher;
import com.servidos.v1.shared.exception.BusinessException;
import com.servidos.v1.tenant.application.restaurante.CrearRestauranteCommand;
import com.servidos.v1.tenant.application.restaurante.CrearRestauranteUseCase;
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

    @InjectMocks CrearRestauranteUseCase useCase;

    private void baseStubs() {
        when(restauranteRepository.existsBySlug("demo")).thenReturn(false);
        when(planRepository.existsById(9L)).thenReturn(true);
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
        useCase.ejecutar(new CrearRestauranteCommand("demo", "Demo", null, 9L, true, 10));
        var captor = ArgumentCaptor.forClass(SuscripcionJpaEntity.class);
        org.mockito.Mockito.verify(suscripcionRepository).save(captor.capture());
        assertEquals(LocalDate.now().plusDays(10), captor.getValue().getFechaFin());
    }

    @Test
    void sinDemoUsa30Dias() {
        baseStubs();
        useCase.ejecutar(new CrearRestauranteCommand("demo", "Demo", null, 9L, false, null));
        var captor = ArgumentCaptor.forClass(SuscripcionJpaEntity.class);
        org.mockito.Mockito.verify(suscripcionRepository).save(captor.capture());
        assertEquals(LocalDate.now().plusDays(30), captor.getValue().getFechaFin());
    }

    @Test
    void demoSinDiasFalla() {
        baseStubs();
        assertThrows(BusinessException.class, () ->
                useCase.ejecutar(new CrearRestauranteCommand("demo", "Demo", null, 9L, true, null)));
    }

    @Test
    void demoDiasFueraDeRangoFalla() {
        baseStubs();
        assertThrows(BusinessException.class, () ->
                useCase.ejecutar(new CrearRestauranteCommand("demo", "Demo", null, 9L, true, 45)));
    }
}
