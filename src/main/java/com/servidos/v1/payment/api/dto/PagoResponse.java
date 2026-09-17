package com.servidos.v1.payment.api.dto;

import com.servidos.v1.payment.domain.EstadoPago;
import com.servidos.v1.payment.domain.MetodoPago;

import java.math.BigDecimal;

public record PagoResponse(
        Long pagoId,
        Long pedidoId,
        BigDecimal monto,
        BigDecimal vuelto,
        MetodoPago metodoPago,
        EstadoPago estado) {}
