package com.servidos.v1.reporting.application.ventas;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.LocalDate;

@Service
@RequiredArgsConstructor
@Slf4j
public class GenerarReporteVentas {

    /**
     * MVP: reporte por rango de fechas filtrando pedido.created_at / pago.fecha_pago
     * Comparar contra fecha local del restaurante, no UTC del servidor.
     * Sin tabla cierre_caja (ver PROJECT_CONTEXT:97).
     */
    public void generar(LocalDate desde, LocalDate hasta, Long restauranteId) {
        log.info("Generar reporte ventas restauranteId={} desde={} hasta={}", restauranteId, desde, hasta);
        // TODO: query pedidoRepository sum(total) where restaurante_id and created_at between
        // y pagoRepository sum(monto) where restaurante_id and fecha_pago between
    }
}
