package com.servidos.v1.ordering.application.mesa;

import com.servidos.v1.ordering.domain.event.PedidoCanceladoEvent;
import com.servidos.v1.ordering.domain.event.PedidoEntregadoEvent;
import com.servidos.v1.payment.domain.event.PagoRegistradoEvent;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class LiberarMesaAlCerrarPedidoListenerTest {

    @Mock
    LiberarMesaAlCerrarPedidoUseCase useCase;

    LiberarMesaAlCerrarPedidoListener listener;

    @BeforeEach
    void setUp() {
        listener = new LiberarMesaAlCerrarPedidoListener(useCase);
    }

    @Test
    void elPagoRegistradoRevisaLaMesaDelPedido() {
        listener.on(new PagoRegistradoEvent(9L, 10L, 1L));

        verify(useCase).ejecutar(1L, 10L);
    }

    @Test
    void laEntregaRevisaLaMesaDelPedido() {
        listener.on(new PedidoEntregadoEvent(1L, 10L));

        verify(useCase).ejecutar(1L, 10L);
    }

    @Test
    void laCancelacionRevisaLaMesaDelPedido() {
        listener.on(new PedidoCanceladoEvent(1L, 10L));

        verify(useCase).ejecutar(1L, 10L);
    }
}
