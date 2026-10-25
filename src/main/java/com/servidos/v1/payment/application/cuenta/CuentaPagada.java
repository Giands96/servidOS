package com.servidos.v1.payment.application.cuenta;

import com.servidos.v1.payment.domain.MetodoPago;

import java.math.BigDecimal;
import java.util.List;

/** Resultado del cobro conjunto: la cuenta, su vuelto (solo efectivo) y un pago por pedido. */
public record CuentaPagada(
        Long cuentaId,
        Long mesaId,
        Integer numeroMesa,
        BigDecimal total,
        MetodoPago metodoPago,
        BigDecimal vuelto,
        List<Pago> pagos) {

    public record Pago(Long pagoId, Long pedidoId, BigDecimal monto) {}
}
