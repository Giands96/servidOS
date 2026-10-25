package com.servidos.v1.payment.api.dto;

import com.servidos.v1.payment.domain.MetodoPago;

import java.math.BigDecimal;
import java.util.List;

/** Cuenta cobrada: el total, el vuelto (solo efectivo, calculado sobre el total) y un pago por pedido. */
public record PagarCuentaMesaResponse(
        Long cuentaId,
        Long mesaId,
        BigDecimal total,
        MetodoPago metodoPago,
        BigDecimal vuelto,
        List<PagoCuentaResponse> pagos) {

    public record PagoCuentaResponse(Long pagoId, Long pedidoId, BigDecimal monto) {}
}
