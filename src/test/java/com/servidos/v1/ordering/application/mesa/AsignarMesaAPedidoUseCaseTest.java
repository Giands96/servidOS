package com.servidos.v1.ordering.application.mesa;

import com.servidos.v1.ordering.domain.EstadoMesa;
import com.servidos.v1.ordering.domain.TipoPedido;
import com.servidos.v1.ordering.infrastructure.jpa.MesaJpaEntity;
import com.servidos.v1.ordering.infrastructure.jpa.MesaJpaRepository;
import com.servidos.v1.ordering.infrastructure.mapper.MesaMapper;
import com.servidos.v1.shared.exception.BusinessException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AsignarMesaAPedidoUseCaseTest {

    @Mock
    MesaJpaRepository mesaRepository;

    AsignarMesaAPedidoUseCase useCase;

    @BeforeEach
    void setUp() {
        useCase = new AsignarMesaAPedidoUseCase(mesaRepository, new MesaMapper());
    }

    private static MesaJpaEntity entidad(EstadoMesa estado, Integer comensales) {
        var e = new MesaJpaEntity();
        e.setMesaId(3L);
        e.setRestauranteId(10L);
        e.setNumero(5);
        e.setCapacidad(4);
        e.setComensales(comensales);
        e.setEstado(estado);
        return e;
    }

    @Test
    void pedidoDeMesaOcupaLaMesaLibreSinComensales() {
        var e = entidad(EstadoMesa.LIBRE, null);
        when(mesaRepository.bloquearPorIdYRestaurante(3L, 10L)).thenReturn(Optional.of(e));

        useCase.ejecutar(3L, TipoPedido.MESA, 10L);

        assertEquals(EstadoMesa.OCUPADA, e.getEstado());
        assertNull(e.getComensales());
        verify(mesaRepository).saveAndFlush(e);
    }

    @Test
    void pedidoDeMesaConLaMesaOcupadaLaDejaComoEsta() {
        var e = entidad(EstadoMesa.OCUPADA, 3);
        when(mesaRepository.bloquearPorIdYRestaurante(3L, 10L)).thenReturn(Optional.of(e));

        useCase.ejecutar(3L, TipoPedido.MESA, 10L);

        assertEquals(EstadoMesa.OCUPADA, e.getEstado());
        assertEquals(3, e.getComensales());
    }

    @Test
    void pedidoDeMesaConMesaInactivaDa400YNoGuarda() {
        var e = entidad(EstadoMesa.LIBRE, null);
        e.setActiva(false);
        when(mesaRepository.bloquearPorIdYRestaurante(3L, 10L)).thenReturn(Optional.of(e));

        var ex = assertThrows(BusinessException.class, () -> useCase.ejecutar(3L, TipoPedido.MESA, 10L));

        assertEquals("La mesa está inactiva", ex.getMessage());
        assertEquals(EstadoMesa.LIBRE, e.getEstado());
        verify(mesaRepository, never()).saveAndFlush(any());
    }

    @Test
    void pedidoDeMesaConMesaInexistenteODeOtroRestauranteDa400() {
        when(mesaRepository.bloquearPorIdYRestaurante(3L, 99L)).thenReturn(Optional.empty());

        var ex = assertThrows(BusinessException.class, () -> useCase.ejecutar(3L, TipoPedido.MESA, 99L));

        assertEquals("La mesa no existe", ex.getMessage());
        verify(mesaRepository, never()).saveAndFlush(any());
    }

    @Test
    void pedidoQueNoEsDeMesaSoloValidaQueLaMesaSeaDelRestaurante() {
        when(mesaRepository.existsByMesaIdAndRestauranteId(3L, 10L)).thenReturn(true);

        useCase.ejecutar(3L, TipoPedido.DELIVERY, 10L);

        verify(mesaRepository, never()).bloquearPorIdYRestaurante(any(), any());
        verify(mesaRepository, never()).saveAndFlush(any());
    }

    @Test
    void pedidoQueNoEsDeMesaConMesaAjenaDa400() {
        when(mesaRepository.existsByMesaIdAndRestauranteId(3L, 99L)).thenReturn(false);

        var ex = assertThrows(BusinessException.class, () -> useCase.ejecutar(3L, TipoPedido.DELIVERY, 99L));

        assertEquals("La mesa no existe", ex.getMessage());
    }

    @Test
    void sinMesaNoHaceNada() {
        useCase.ejecutar(null, TipoPedido.RECOJO, 10L);

        verifyNoInteractions(mesaRepository);
    }
}
