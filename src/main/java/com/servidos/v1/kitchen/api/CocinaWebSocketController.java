package com.servidos.v1.kitchen.api;

import com.servidos.v1.kitchen.api.dto.CocinaEventoMessage;
import com.servidos.v1.kitchen.domain.event.PedidoPreparadoEvent;
import com.servidos.v1.ordering.domain.EstadoPedido;
import com.servidos.v1.ordering.domain.event.PedidoEstadoCambiadoEvent;
import lombok.RequiredArgsConstructor;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionalEventListener;

import java.util.EnumSet;
import java.util.Set;

/**
 * Empuja al tablero de cocina (STOMP) los cambios de estado que afectan la cola
 * (EN_PREPARACION) o los listos (LISTO): un pedido que entra, pasa de columna o sale.
 * <p>
 * Tópico por restaurante: {@code /topic/restaurantes/{restauranteId}/cocina}. Solo se
 * puede suscribir un usuario de ese restaurante (ver {@code StompAuthInterceptor}).
 * <p>
 * Se envía después del commit: si la transacción hace rollback, el front no se entera de
 * un cambio que no existió.
 */
@Component
@RequiredArgsConstructor
public class CocinaWebSocketController {

    private static final Set<EstadoPedido> TABLERO = EnumSet.of(EstadoPedido.EN_PREPARACION, EstadoPedido.LISTO);

    private final SimpMessagingTemplate messagingTemplate;

    public static String topico(Long restauranteId) {
        return "/topic/restaurantes/" + restauranteId + "/cocina";
    }

    @TransactionalEventListener(fallbackExecution = true)
    public void on(PedidoEstadoCambiadoEvent event) {
        notificar(event.getRestauranteId(), event.getPedidoId(), event.getEstadoAnterior(), event.getEstadoNuevo());
    }

    /** {@code marcarListo} de cocina no pasa por ordering, así que publica su propio evento. */
    @TransactionalEventListener(fallbackExecution = true)
    public void on(PedidoPreparadoEvent event) {
        notificar(event.getRestauranteId(), event.getPedidoId(), EstadoPedido.EN_PREPARACION, EstadoPedido.LISTO);
    }

    private void notificar(Long restauranteId, Long pedidoId, EstadoPedido anterior, EstadoPedido nuevo) {
        if (!TABLERO.contains(anterior) && !TABLERO.contains(nuevo)) {
            return;
        }
        messagingTemplate.convertAndSend(topico(restauranteId), new CocinaEventoMessage(pedidoId, anterior, nuevo));
    }
}
