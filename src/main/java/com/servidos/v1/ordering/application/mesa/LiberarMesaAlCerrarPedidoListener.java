package com.servidos.v1.ordering.application.mesa;

import com.servidos.v1.ordering.domain.event.PedidoCanceladoEvent;
import com.servidos.v1.ordering.domain.event.PedidoEntregadoEvent;
import com.servidos.v1.payment.domain.event.PagoRegistradoEvent;
import lombok.RequiredArgsConstructor;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

/**
 * Una mesa se libera sola cuando su último pedido se cierra, en cualquier orden de los hechos:
 * pagar primero y entregar después, o al revés, o cancelar. Los tres eventos pueden ser el
 * "último"; cada uno re-evalúa la mesa y solo el que deja cero pedidos abiertos la libera.
 * <p>
 * Corre sincrónico, dentro de la transacción del evento (mismo patrón que
 * {@code CancelarPedidoAlReembolsarListener}): si la liberación falla, el pago, la entrega o la
 * cancelación tampoco se guardan, y el lock de la mesa se toma antes del commit.
 */
@Component
@RequiredArgsConstructor
public class LiberarMesaAlCerrarPedidoListener {

    private final LiberarMesaAlCerrarPedidoUseCase liberarMesa;

    @EventListener
    public void on(PagoRegistradoEvent event) {
        liberarMesa.ejecutar(event.getPedidoId(), event.getRestauranteId());
    }

    @EventListener
    public void on(PedidoEntregadoEvent event) {
        liberarMesa.ejecutar(event.getPedidoId(), event.getRestauranteId());
    }

    @EventListener
    public void on(PedidoCanceladoEvent event) {
        liberarMesa.ejecutar(event.getPedidoId(), event.getRestauranteId());
    }
}
