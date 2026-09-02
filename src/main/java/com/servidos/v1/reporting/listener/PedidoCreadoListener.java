package com.servidos.v1.reporting.listener;

import com.servidos.v1.ordering.domain.event.PedidoCreadoEvent;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

@Component
@Slf4j
public class PedidoCreadoListener {

    @EventListener
    public void on(PedidoCreadoEvent event) {
        log.info("Reporting: Pedido creado pedidoId={} restauranteId={}", event.getPedidoId(), event.getRestauranteId());
        // TODO: picks para reporte de ventas por fecha (pedido.created_at como filtro)
    }
}
