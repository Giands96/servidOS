package com.servidos.v1.ordering.application.mesa;

import com.servidos.v1.ordering.domain.EstadoMesa;
import com.servidos.v1.ordering.infrastructure.jpa.MesaJpaEntity;
import com.servidos.v1.ordering.infrastructure.jpa.MesaJpaRepository;
import com.servidos.v1.ordering.infrastructure.jpa.PedidoJpaRepository;
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
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class LiberarMesaUseCaseTest {

    @Mock
    MesaJpaRepository mesaRepository;

    @Mock
    PedidoJpaRepository pedidoRepository;

    LiberarMesaUseCase useCase;

    @BeforeEach
    void setUp() {
        useCase = new LiberarMesaUseCase(mesaRepository, pedidoRepository, new MesaMapper());
    }

    private static MesaJpaEntity entidad(EstadoMesa estado, Integer comensales) {
        var e = new MesaJpaEntity();
        e.setMesaId(1L);
        e.setRestauranteId(10L);
        e.setNumero(5);
        e.setCapacidad(4);
        e.setComensales(comensales);
        e.setEstado(estado);
        return e;
    }

    private void dadaMesa(MesaJpaEntity e) {
        when(mesaRepository.bloquearPorIdYRestaurante(1L, 10L)).thenReturn(Optional.of(e));
    }

    @Test
    void liberaLaMesaSinPedidosAbiertos() {
        var e = entidad(EstadoMesa.OCUPADA, 3);
        dadaMesa(e);
        when(pedidoRepository.contarAbiertosEnMesa(1L, 10L)).thenReturn(0L);
        when(mesaRepository.saveAndFlush(any(MesaJpaEntity.class))).thenAnswer(inv -> inv.getArgument(0));

        var mesa = useCase.ejecutar(1L, 10L);

        assertEquals(EstadoMesa.LIBRE, mesa.getEstado());
        assertNull(mesa.getComensales());
        assertEquals(EstadoMesa.LIBRE, e.getEstado());
        assertNull(e.getComensales());
    }

    @Test
    void conPedidosAbiertosDa400YNoGuarda() {
        dadaMesa(entidad(EstadoMesa.OCUPADA, 3));
        when(pedidoRepository.contarAbiertosEnMesa(1L, 10L)).thenReturn(2L);

        var ex = assertThrows(BusinessException.class, () -> useCase.ejecutar(1L, 10L));

        assertEquals("La mesa tiene pedidos abiertos", ex.getMessage());
        verify(mesaRepository, never()).saveAndFlush(any());
    }

    @Test
    void mesaYaLibreDa400() {
        dadaMesa(entidad(EstadoMesa.LIBRE, null));

        var ex = assertThrows(BusinessException.class, () -> useCase.ejecutar(1L, 10L));

        assertEquals("La mesa ya está libre", ex.getMessage());
        verify(mesaRepository, never()).saveAndFlush(any());
    }

    @Test
    void mesaDeOtroRestauranteDa400() {
        when(mesaRepository.bloquearPorIdYRestaurante(1L, 99L)).thenReturn(Optional.empty());

        var ex = assertThrows(BusinessException.class, () -> useCase.ejecutar(1L, 99L));

        assertEquals("Mesa no encontrada", ex.getMessage());
    }

    @Test
    void sinRestauranteDa400() {
        assertThrows(BusinessException.class, () -> useCase.ejecutar(1L, null));
    }
}
