package com.servidos.v1.identity.api.dto;

import com.servidos.v1.tenant.domain.Suscripcion.EstadoSuscripcion;

import java.math.BigDecimal;
import java.time.LocalDate;

public record ResumenSuscripcion(
        Long planId,
        String nombrePlan,
        BigDecimal monto,
        String moneda,
        EstadoSuscripcion estado,
        LocalDate fechaFin,
        long diasRestantes,
        boolean vencida) {
}
