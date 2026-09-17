package com.servidos.v1.payment.api.dto;

import com.servidos.v1.payment.domain.EstadoPago;

import java.math.BigDecimal;

public record PagoResponse(
        Long pagoId,
        Long pedidoId,
        BigDecimal monto,
        EstadoPago estado) {}
