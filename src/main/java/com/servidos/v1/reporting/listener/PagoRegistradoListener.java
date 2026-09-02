package com.servidos.v1.reporting.listener;

import com.servidos.v1.payment.domain.event.PagoRegistradoEvent;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

@Component
@Slf4j
public class PagoRegistradoListener {

    @EventListener
    public void on(PagoRegistradoEvent event) {
        log.info("Reporting: Pago registrado pagoId={} pedidoId={} restauranteId={}", event.getPagoId(), event.getPedidoId(), event.getRestauranteId());
        // TODO: agregado para reporte de ingresos por fecha (pago.fecha_pago)
    }
}
