package com.servidos.v1.tenant.application;

import com.servidos.v1.shared.exception.BusinessException;
import com.servidos.v1.tenant.application.restaurante.ObtenerRestauranteUseCase;
import com.servidos.v1.tenant.infrastructure.jpa.RestauranteJpaEntity;
import com.servidos.v1.tenant.infrastructure.jpa.RestauranteJpaRepository;
import com.servidos.v1.tenant.infrastructure.jpa.SuscripcionJpaRepository;
import com.servidos.v1.tenant.infrastructure.mapper.RestauranteMapper;
import com.servidos.v1.tenant.infrastructure.mapper.SuscripcionMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ObtenerRestauranteUseCaseTest {

    @Mock RestauranteJpaRepository restauranteRepository;
    @Mock SuscripcionJpaRepository suscripcionRepository;
    @Mock RestauranteMapper restauranteMapper;
    @Mock SuscripcionMapper suscripcionMapper;

    @InjectMocks ObtenerRestauranteUseCase useCase;

    @Test
    void inexistenteFalla() {
        when(restauranteRepository.findById(99L)).thenReturn(Optional.empty());
        assertThrows(BusinessException.class, () -> useCase.obtener(99L));
    }

    @Test
    void existenteDelegaAlMapper() {
        var entity = new RestauranteJpaEntity();
        entity.setRestauranteId(7L);
        entity.setSlug("demo");
        when(restauranteRepository.findById(7L)).thenReturn(Optional.of(entity));
        when(restauranteMapper.toDomain(entity)).thenCallRealMethod();
        assertEquals("demo", useCase.obtener(7L).getSlug());
    }
}
