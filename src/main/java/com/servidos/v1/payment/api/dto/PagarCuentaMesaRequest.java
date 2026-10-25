package com.servidos.v1.payment.api.dto;

import com.servidos.v1.payment.domain.MetodoPago;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.util.List;

/**
 * {@code pedidoIds}: los pedidos de la cuenta que el cajero vio; si la cuenta cambió desde entonces
 * el cobro se rechaza. {@code montoEntregado} solo aplica (y es obligatorio) en efectivo.
 */
public record PagarCuentaMesaRequest(
        @NotNull MetodoPago metodoPago,
        @Positive BigDecimal montoEntregado,
        @Size(max = 100) String referenciaExterna,
        @NotEmpty List<@NotNull Long> pedidoIds) {}
