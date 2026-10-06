package com.servidos.v1.tenant.api.dto;

import com.servidos.v1.tenant.domain.Suscripcion.EstadoSuscripcion;
import java.time.LocalDate;

public record SuscripcionDashboardResponse(Long suscripcionId, Long planId, String nombrePlan,
        EstadoSuscripcion estado, LocalDate fechaInicio, LocalDate fechaFin) {}
