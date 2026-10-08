package com.servidos.v1.kitchen.api;

import com.servidos.v1.kitchen.api.dto.CocinaEventoMessage;
import com.servidos.v1.kitchen.domain.event.PedidoPreparadoEvent;
import com.servidos.v1.ordering.domain.EstadoPedido;
import com.servidos.v1.ordering.domain.event.PedidoEstadoCambiadoEvent;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.messaging.simp.SimpMessagingTemplate;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class CocinaWebSocketControllerTest {

    @Mock SimpMessagingTemplate messagingTemplate;

    @InjectMocks CocinaWebSocketController controller;

    /** Entra a la cola, pasa a listos, sale de listos o se cancela desde la cola. */
    @ParameterizedTest
    @CsvSource({
            "PENDIENTE, EN_PREPARACION",
            "EN_PREPARACION, LISTO",
            "LISTO, EN_ENTREGA",
            "LISTO, ENTREGADO",
            "EN_PREPARACION, CANCELADO"})
    void cambioQueTocaElTableroSeNotificaAlTopicoDelRestaurante(EstadoPedido anterior, EstadoPedido nuevo) {
        controller.on(new PedidoEstadoCambiadoEvent(20L, 1L, anterior, nuevo));

        verify(messagingTemplate).convertAndSend("/topic/restaurantes/1/cocina",
                new CocinaEventoMessage(20L, anterior, nuevo));
    }

    @ParameterizedTest
    @CsvSource({
            "PENDIENTE, CANCELADO",
            "EN_ENTREGA, ENTREGADO",
            "EN_ENTREGA, CANCELADO"})
    void cambioFueraDelTableroNoSeNotifica(EstadoPedido anterior, EstadoPedido nuevo) {
        controller.on(new PedidoEstadoCambiadoEvent(20L, 1L, anterior, nuevo));

        verify(messagingTemplate, never()).convertAndSend(anyString(), any(Object.class));
    }

    @Test
    void marcarListoDeCocinaSeNotifica() {
        controller.on(new PedidoPreparadoEvent(20L, 1L));

        verify(messagingTemplate).convertAndSend("/topic/restaurantes/1/cocina",
                new CocinaEventoMessage(20L, EstadoPedido.EN_PREPARACION, EstadoPedido.LISTO));
    }
}
