package com.servidos.v1.payment.api.dto;

import com.servidos.v1.payment.domain.MetodoPago;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;

public record RegistrarPagoRequest(
        @NotNull Long pedidoId,
        @NotNull MetodoPago metodoPago,
        @Positive BigDecimal montoEntregado,
        @Size(max = 100) String referenciaExterna) {}
