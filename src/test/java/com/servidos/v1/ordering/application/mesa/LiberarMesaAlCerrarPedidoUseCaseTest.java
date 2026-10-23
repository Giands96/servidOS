package com.servidos.v1.ordering.application.mesa;

import com.servidos.v1.ordering.domain.EstadoMesa;
import com.servidos.v1.ordering.infrastructure.jpa.MesaJpaEntity;
import com.servidos.v1.ordering.infrastructure.jpa.MesaJpaRepository;
import com.servidos.v1.ordering.infrastructure.jpa.PedidoJpaEntity;
import com.servidos.v1.ordering.infrastructure.jpa.PedidoJpaRepository;
import com.servidos.v1.ordering.infrastructure.mapper.MesaMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class LiberarMesaAlCerrarPedidoUseCaseTest {

    @Mock
    PedidoJpaRepository pedidoRepository;

    @Mock
    MesaJpaRepository mesaRepository;

    LiberarMesaAlCerrarPedidoUseCase useCase;

    @BeforeEach
    void setUp() {
        useCase = new LiberarMesaAlCerrarPedidoUseCase(pedidoRepository, mesaRepository, new MesaMapper());
    }

    private static PedidoJpaEntity pedido(Long mesaId) {
        var p = new PedidoJpaEntity();
        p.setPedidoId(1L);
        p.setRestauranteId(10L);
        p.setMesaId(mesaId);
        return p;
    }

    private static MesaJpaEntity mesa(EstadoMesa estado, Integer comensales) {
        var e = new MesaJpaEntity();
        e.setMesaId(3L);
        e.setRestauranteId(10L);
        e.setNumero(5);
        e.setCapacidad(4);
        e.setComensales(comensales);
        e.setEstado(estado);
        return e;
    }

    private void dadoPedido(PedidoJpaEntity p) {
        when(pedidoRepository.findByPedidoIdAndRestauranteId(1L, 10L)).thenReturn(Optional.of(p));
    }

    @Test
    void liberaLaMesaOcupadaCuandoNoQuedanPedidosAbiertos() {
        dadoPedido(pedido(3L));
        var m = mesa(EstadoMesa.OCUPADA, 3);
        when(mesaRepository.bloquearPorIdYRestaurante(3L, 10L)).thenReturn(Optional.of(m));
        when(pedidoRepository.contarAbiertosEnMesa(3L, 10L)).thenReturn(0L);

        useCase.ejecutar(1L, 10L);

        assertEquals(EstadoMesa.LIBRE, m.getEstado());
        assertNull(m.getComensales());
        verify(mesaRepository).saveAndFlush(m);
    }

    @Test
    void conUnPedidoAbiertoLaMesaSigueOcupada() {
        dadoPedido(pedido(3L));
        var m = mesa(EstadoMesa.OCUPADA, 3);
        when(mesaRepository.bloquearPorIdYRestaurante(3L, 10L)).thenReturn(Optional.of(m));
        when(pedidoRepository.contarAbiertosEnMesa(3L, 10L)).thenReturn(1L);

        useCase.ejecutar(1L, 10L);

        assertEquals(EstadoMesa.OCUPADA, m.getEstado());
        assertEquals(3, m.getComensales());
        verify(mesaRepository, never()).saveAndFlush(any());
    }

    @Test
    void esIdempotenteConLaMesaYaLibre() {
        dadoPedido(pedido(3L));
        when(mesaRepository.bloquearPorIdYRestaurante(3L, 10L)).thenReturn(Optional.of(mesa(EstadoMesa.LIBRE, null)));

        useCase.ejecutar(1L, 10L);

        verify(pedidoRepository, never()).contarAbiertosEnMesa(any(), any());
        verify(mesaRepository, never()).saveAndFlush(any());
    }

    @Test
    void pedidoSinMesaNoHaceNadaNiFalla() {
        dadoPedido(pedido(null));

        useCase.ejecutar(1L, 10L);

        verify(mesaRepository, never()).bloquearPorIdYRestaurante(any(), any());
    }

    @Test
    void pedidoDeOtroRestauranteNoHaceNada() {
        when(pedidoRepository.findByPedidoIdAndRestauranteId(1L, 99L)).thenReturn(Optional.empty());

        useCase.ejecutar(1L, 99L);

        verify(mesaRepository, never()).bloquearPorIdYRestaurante(any(), any());
    }
}
