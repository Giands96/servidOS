package com.servidos.v1.tenant.application;

import com.servidos.v1.shared.event.EventPublisher;
import com.servidos.v1.shared.exception.BusinessException;
import com.servidos.v1.tenant.application.restaurante.CambiarEstadoRestauranteUseCase;
import com.servidos.v1.tenant.domain.EstadoRestaurante;
import com.servidos.v1.tenant.domain.event.RestauranteEstadoCambiadoEvent;
import com.servidos.v1.tenant.infrastructure.jpa.RestauranteJpaEntity;
import com.servidos.v1.tenant.infrastructure.jpa.RestauranteJpaRepository;
import com.servidos.v1.tenant.infrastructure.mapper.RestauranteMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CambiarEstadoRestauranteUseCaseTest {

    @Mock RestauranteJpaRepository restauranteRepository;
    @Mock RestauranteMapper restauranteMapper;
    @Mock EventPublisher eventPublisher;

    @InjectMocks CambiarEstadoRestauranteUseCase useCase;

    private RestauranteJpaEntity restaurante(EstadoRestaurante estado) {
        var e = new RestauranteJpaEntity();
        e.setRestauranteId(1L);
        e.setSlug("demo");
        e.setEstado(estado);
        return e;
    }

    private void stubs(EstadoRestaurante actual) {
        when(restauranteRepository.findById(1L)).thenReturn(Optional.of(restaurante(actual)));
        when(restauranteRepository.save(any())).thenAnswer(i -> i.getArgument(0));
        when(restauranteMapper.toDomain(any())).thenCallRealMethod();
    }

    /**
     * El camino de la reactivación: el SUPERADMIN confirma que el cliente pagó y
     * devuelve el acceso. Antes esto no existía y había que editar la base a mano.
     */
    @Test
    void reactivaUnRestauranteInactivo() {
        stubs(EstadoRestaurante.INACTIVO);

        var r = useCase.ejecutar(1L, EstadoRestaurante.ACTIVO);

        assertEquals(EstadoRestaurante.ACTIVO, r.getEstado());
        var captor = ArgumentCaptor.forClass(RestauranteEstadoCambiadoEvent.class);
        verify(eventPublisher).publish(captor.capture());
        assertEquals(EstadoRestaurante.INACTIVO, captor.getValue().getEstadoAnterior());
        assertEquals(EstadoRestaurante.ACTIVO, captor.getValue().getEstadoNuevo());
    }

    @Test
    void suspendeUnRestauranteActivo() {
        stubs(EstadoRestaurante.ACTIVO);

        var r = useCase.ejecutar(1L, EstadoRestaurante.INACTIVO);

        assertEquals(EstadoRestaurante.INACTIVO, r.getEstado());
    }

    /** Idempotencia operativa: repetir la orden no es un error silencioso. */
    @Test
    void repetirElMismoEstadoFalla() {
        when(restauranteRepository.findById(1L))
                .thenReturn(Optional.of(restaurante(EstadoRestaurante.INACTIVO)));

        var ex = assertThrows(BusinessException.class,
                () -> useCase.ejecutar(1L, EstadoRestaurante.INACTIVO));

        assertEquals("El restaurante ya está INACTIVO", ex.getMessage());
        verify(restauranteRepository, never()).save(any());
        verify(eventPublisher, never()).publish(any());
    }

    @Test
    void restauranteInexistenteFalla() {
        when(restauranteRepository.findById(99L)).thenReturn(Optional.empty());

        var ex = assertThrows(BusinessException.class,
                () -> useCase.ejecutar(99L, EstadoRestaurante.ACTIVO));

        assertEquals("El restaurante no existe", ex.getMessage());
        verify(restauranteRepository, never()).save(any());
    }

    @Test
    void exigeIdYEstado() {
        assertEquals("El restaurante es obligatorio",
                assertThrows(BusinessException.class,
                        () -> useCase.ejecutar(null, EstadoRestaurante.ACTIVO)).getMessage());
        assertEquals("El estado es obligatorio",
                assertThrows(BusinessException.class,
                        () -> useCase.ejecutar(1L, null)).getMessage());
    }

    /**
     * Suspender solo cambia `estado`: no resetea ni pisa el resto del restaurante. La
     * suscripción es una tabla aparte y este use case ni la inyecta, así que suspender
     * no puede cancelar el plan (si lo hiciera, reactivar un cliente que pagó perdería
 * * el historial).
     */
    @Test
    void suspenderSoloCambiaElEstado() {
        stubs(EstadoRestaurante.ACTIVO);

        var r = useCase.ejecutar(1L, EstadoRestaurante.INACTIVO);

        assertEquals(EstadoRestaurante.INACTIVO, r.getEstado());
        assertEquals(1L, r.getRestaurante_id());
        assertEquals("demo", r.getSlug());
        verify(restauranteRepository).save(any());
    }
}