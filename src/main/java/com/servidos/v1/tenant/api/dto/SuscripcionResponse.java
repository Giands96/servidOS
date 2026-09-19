package com.servidos.v1.tenant.api.dto;

import com.servidos.v1.tenant.domain.Suscripcion.EstadoSuscripcion;
import java.time.LocalDate;

public record SuscripcionResponse(Long suscripcionId, Long restauranteId, Long planId, EstadoSuscripcion estado, LocalDate fechaInicio, LocalDate fechaFin) {}
