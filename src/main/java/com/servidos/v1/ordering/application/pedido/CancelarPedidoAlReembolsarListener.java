package com.servidos.v1.ordering.application.pedido;

import com.servidos.v1.ordering.domain.EstadoPedido;
import com.servidos.v1.ordering.infrastructure.jpa.PedidoJpaRepository;
import com.servidos.v1.payment.domain.event.PagoReembolsadoEvent;
import lombok.RequiredArgsConstructor;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

/**
 * Un reembolso cancela el pedido si todavía no se entregó: no tiene sentido seguir
 * preparando algo que ya se devolvió. Un pedido ENTREGADO queda como está (el reembolso
 * es posterior a la entrega).
 * <p>
 * Corre sincrónico, dentro de la transacción del reembolso: si la cancelación falla, el
 * reembolso tampoco se guarda. Delega en {@link CambiarEstadoPedidoUseCase} para que las
 * reglas de transición sean las mismas que en la API; el pago ya está REEMBOLSADO, así
 * que el chequeo de "pedido pagado no se cancela" no lo frena.
 */
@Component
@RequiredArgsConstructor
public class CancelarPedidoAlReembolsarListener {

    private final PedidoJpaRepository pedidoRepository;
    private final CambiarEstadoPedidoUseCase cambiarEstado;

    @EventListener
    public void on(PagoReembolsadoEvent event) {
        pedidoRepository.findByPedidoIdAndRestauranteId(event.getPedidoId(), event.getRestauranteId())
                .filter(p -> p.getEstado() != EstadoPedido.ENTREGADO && p.getEstado() != EstadoPedido.CANCELADO)
                .ifPresent(p -> cambiarEstado.ejecutar(p.getPedidoId(), p.getRestauranteId(), EstadoPedido.CANCELADO));
    }
}
