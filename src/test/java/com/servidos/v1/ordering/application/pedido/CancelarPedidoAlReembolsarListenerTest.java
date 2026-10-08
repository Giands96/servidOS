package com.servidos.v1.ordering.application.pedido;

import com.servidos.v1.ordering.domain.EstadoPedido;
import com.servidos.v1.ordering.infrastructure.jpa.PedidoJpaEntity;
import com.servidos.v1.ordering.infrastructure.jpa.PedidoJpaRepository;
import com.servidos.v1.payment.domain.event.PagoReembolsadoEvent;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CancelarPedidoAlReembolsarListenerTest {

    @Mock PedidoJpaRepository pedidoRepository;
    @Mock CambiarEstadoPedidoUseCase cambiarEstado;

    @InjectMocks CancelarPedidoAlReembolsarListener listener;

    private void dadoPedido(EstadoPedido estado) {
        var p = new PedidoJpaEntity();
        p.setPedidoId(20L);
        p.setRestauranteId(1L);
        p.setEstado(estado);
        when(pedidoRepository.findByPedidoIdAndRestauranteId(20L, 1L)).thenReturn(Optional.of(p));
    }

    @ParameterizedTest
    @EnumSource(value = EstadoPedido.class, names = {"PENDIENTE", "EN_PREPARACION", "LISTO", "EN_ENTREGA"})
    void pedidoNoEntregadoSeCancela(EstadoPedido estado) {
        dadoPedido(estado);

        listener.on(new PagoReembolsadoEvent(3L, 1L, 20L));

        verify(cambiarEstado).ejecutar(20L, 1L, EstadoPedido.CANCELADO);
    }

    @ParameterizedTest
    @EnumSource(value = EstadoPedido.class, names = {"ENTREGADO", "CANCELADO"})
    void pedidoEntregadoOCanceladoNoSeToca(EstadoPedido estado) {
        dadoPedido(estado);

        listener.on(new PagoReembolsadoEvent(3L, 1L, 20L));

        verify(cambiarEstado, never()).ejecutar(any(), any(), any());
    }

    @Test
    void pedidoDeOtroRestauranteNoSeEncuentraNiSeToca() {
        listener.on(new PagoReembolsadoEvent(3L, 99L, 20L));

        verify(cambiarEstado, never()).ejecutar(any(), any(), any());
    }
}
